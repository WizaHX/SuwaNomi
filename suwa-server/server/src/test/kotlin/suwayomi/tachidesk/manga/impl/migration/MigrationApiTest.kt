package suwayomi.tachidesk.manga.impl.migration

import eu.kanade.tachiyomi.source.local.LocalSource
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.protobuf.ProtoBuf
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import suwayomi.tachidesk.manga.impl.backup.BackupFlags
import suwayomi.tachidesk.manga.impl.backup.proto.handlers.BackupMangaHandler
import suwayomi.tachidesk.manga.impl.backup.proto.models.Backup
import suwayomi.tachidesk.manga.impl.util.getMangaDownloadDir
import suwayomi.tachidesk.manga.impl.util.source.GetSource
import suwayomi.tachidesk.manga.impl.util.source.StubSource
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.user.UserType
import suwayomi.tachidesk.test.GraphQLTest
import java.io.File
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MigrationApiTest : GraphQLTest() {
    @TempDir
    lateinit var storage: File
    private var savedDownloadsPath: String? = null
    private var savedLocalPath: String? = null

    @AfterEach
    fun clean() {
        savedDownloadsPath?.let { serverConfig.downloadsPath.value = it }
        savedLocalPath?.let { serverConfig.localSourcePath.value = it }
        GetSource.unregisterSource(2001)
        GetSource.unregisterSource(2002)
        transaction {
            MangaTable.deleteAll()
        }
    }

    @BeforeEach
    fun setupSources() {
        savedDownloadsPath = serverConfig.downloadsPath.value
        serverConfig.downloadsPath.value = storage.absolutePath
        GetSource.registerSource(
            2002L to
                object : StubSource(2002) {
                    override suspend fun getMangaUpdate(
                        manga: SManga,
                        chapters: List<SChapter>,
                        fetchDetails: Boolean,
                        fetchChapters: Boolean,
                    ): SMangaUpdate =
                        SMangaUpdate(
                            SManga.create().apply {
                                title = "API destination title"
                                url = manga.url
                                description = "New description"
                            },
                            listOf(
                                SChapter.create().apply {
                                    name = "Chapter 1"
                                    url = "/chapter/1"
                                    chapter_number = 1f
                                },
                            ),
                        )
                },
        )
    }

    @Test
    fun `API migrates missing original source cleans files and preserves upstream backup representation`() {
        val (old, target) =
            transaction {
                val old =
                    MangaTable
                        .insertAndGetId {
                            it[title] = "API original"
                            it[url] = "/old"
                            it[sourceReference] = 2001
                            it[initialized] =
                                true
                        }.value
                val target =
                    MangaTable
                        .insertAndGetId {
                            it[title] = "API target"
                            it[url] = "/target"
                            it[sourceReference] = 2002
                            it[initialized] =
                                true
                        }.value
                MangaUserTable.insert {
                    it[manga] = old
                    it[user] = 1
                    it[inLibrary] = true
                }
                val chapter =
                    ChapterTable
                        .insertAndGetId {
                            it[manga] = old
                            it[name] = "Removed special"
                            it[url] = "/old/special"
                            it[sourceOrder] =
                                0
                        }.value
                ChapterUserTable.insert {
                    it[ChapterUserTable.chapter] = chapter
                    it[user] = 1
                    it[lastReadAt] = 1700000000L
                    it[isRead] = true
                }
                val matched =
                    ChapterTable
                        .insertAndGetId {
                            it[manga] = old
                            it[name] = "Original chapter title"
                            it[url] = "/old/1"
                            it[chapter_number] = 1f
                            it[sourceOrder] =
                                1
                        }.value
                ChapterUserTable.insert {
                    it[ChapterUserTable.chapter] = matched
                    it[user] = 1
                    it[lastReadAt] = 1700000001L
                    it[isRead] = true
                }
                old to target
            }
        val oldDir = File(getMangaDownloadDir("API original", "2001"))
        oldDir.mkdirs()
        File(oldDir, "old.cbz").writeText("old download")
        val operation =
            "mutation { migrateMangaSameId(input: {originalId: $old, destinationId: $target}) " +
                "{ mangaId } }"
        graphql(operation, user = UserType.Visitor).assertHasError()
        assertTrue(oldDir.exists())
        val result = graphql(operation, user = UserType.User(1, emptyList()))
        result.assertNoErrors()
        assertEquals(old, (result.dataPath("migrateMangaSameId", "mangaId") as Number).toInt())
        assertFalse(oldDir.exists())
        val backup = BackupMangaHandler.backup(1, BackupFlags.DEFAULT).single()
        assertEquals("/target", backup.url)
        assertEquals(2002L, backup.source)
        assertEquals(listOf("/chapter/1"), backup.chapters.map { it.url })
        assertTrue(backup.history.isEmpty(), "Old per-chapter reading history is discarded")
        transaction { MangaTable.deleteAll() }
        val errors = mutableListOf<Pair<Date, String>>()
        val encoded = ProtoBuf.encodeToByteArray(Backup.serializer(), Backup(backupManga = listOf(backup)))
        val decoded = ProtoBuf.decodeFromByteArray(Backup.serializer(), encoded)
        BackupMangaHandler.restore(1, decoded.backupManga.single(), emptyMap(), emptyMap(), errors, BackupFlags.DEFAULT)
        assertTrue(errors.isEmpty(), errors.toString())
        val exportedAgain = BackupMangaHandler.backup(1, BackupFlags.DEFAULT).single()
        assertEquals(backup.history, exportedAgain.history)
        assertEquals(backup.chapters.map { it.url }, exportedAgain.chapters.map { it.url })
        transaction { MangaTable.deleteAll() }
        val secondBytes = ProtoBuf.encodeToByteArray(Backup.serializer(), Backup(backupManga = listOf(exportedAgain)))
        BackupMangaHandler.restore(
            1,
            ProtoBuf.decodeFromByteArray(Backup.serializer(), secondBytes).backupManga.single(),
            emptyMap(),
            emptyMap(),
            errors,
            BackupFlags.DEFAULT,
        )
        assertTrue(errors.isEmpty(), errors.toString())
        transaction {
            assertEquals(0L, ChapterUserTable.selectAll().single()[ChapterUserTable.lastReadAt])
            assertTrue(ChapterUserTable.selectAll().single()[ChapterUserTable.isRead])
        }
    }

    @Test
    fun `migrates from missing source to local and back online without deleting local content`() =
        runTest {
            savedLocalPath = serverConfig.localSourcePath.value
            serverConfig.localSourcePath.value = File(storage, "local").apply { mkdirs() }.absolutePath
            val pages =
                (1..2).map { number ->
                    File(storage, "local/Local comic/Chapter $number/page.png").apply {
                        parentFile.mkdirs()
                        writeBytes(
                            java.util.Base64.getDecoder().decode(
                                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aWQAAAABJRU5ErkJggg==",
                            ),
                        )
                    }
                }
            val (old, local) =
                transaction {
                    val old =
                        MangaTable
                            .insertAndGetId {
                                it[title] = "Gone source"
                                it[url] = "/gone"
                                it[sourceReference] = 2001
                            }.value
                    val local =
                        MangaTable
                            .insertAndGetId {
                                it[title] = "Local comic"
                                it[url] = "Local comic"
                                it[sourceReference] = LocalSource.ID
                            }.value
                    MangaUserTable.insert {
                        it[manga] = old
                        it[user] = 1
                        it[inLibrary] = true
                    }
                    val chapter =
                        ChapterTable.insertAndGetId {
                            it[manga] = old
                            it[url] = "/gone/1"
                            it[name] = "Chapter 1"
                            it[chapter_number] = 1f
                            it[sourceOrder] = 0
                        }
                    ChapterUserTable.insert {
                        it[ChapterUserTable.chapter] = chapter
                        it[user] = 1
                        it[isRead] = true
                    }
                    old to local
                }
            assertEquals(old, SameIdMigration.migrate(old, local))
            transaction {
                assertEquals(LocalSource.ID, MangaTable.selectAll().single()[MangaTable.sourceReference])
                assertEquals(2L, ChapterTable.selectAll().count())
                assertEquals(1L, ChapterUserTable.selectAll().count())
            }
            val source = GetSource.getSourceOrNull(LocalSource.ID)!!
            assertEquals(1, source.getPageList(SChapter.create().apply { url = "Local comic/Chapter 1" }).size)
            val online =
                transaction {
                    MangaTable
                        .insertAndGetId {
                            it[title] = "Online candidate"
                            it[url] = "/online"
                            it[sourceReference] = 2002
                        }.value
                }
            assertEquals(old, SameIdMigration.migrate(old, online))
            assertTrue(pages.all { it.exists() })
            transaction {
                assertEquals(2002L, MangaTable.selectAll().where { MangaTable.id eq old }.single()[MangaTable.sourceReference])
                assertEquals("/chapter/1", ChapterTable.selectAll().single()[ChapterTable.url])
                assertTrue(ChapterUserTable.selectAll().single()[ChapterUserTable.isRead])
            }
        }
}
