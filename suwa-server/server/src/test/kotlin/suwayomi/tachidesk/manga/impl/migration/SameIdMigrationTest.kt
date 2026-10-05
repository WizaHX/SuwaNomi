package suwayomi.tachidesk.manga.impl.migration

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.manga.impl.util.source.GetSource
import suwayomi.tachidesk.manga.impl.util.source.StubSource
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaMetaTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.test.ApplicationTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SameIdMigrationTest : ApplicationTest() {
    @BeforeEach
    fun registerSources() {
        GetSource.registerSource(1001L to StubSource(1001))
        GetSource.registerSource(1002L to StubSource(1002))
    }

    @AfterEach
    fun clean() {
        GetSource.unregisterSource(1001)
        GetSource.unregisterSource(1002)
        transaction {
            MangaTable.deleteAll()
        }
    }

    private fun manga(
        label: String,
        source: Long,
    ): Int =
        transaction {
            MangaTable
                .insertAndGetId {
                    it[title] = label
                    it[url] = "/$label"
                    it[sourceReference] = source
                    it[initialized] = true
                }.value
        }

    private fun chapter(
        mangaId: Int,
        label: String,
        number: Float,
        userId: Int = 1,
    ): Int =
        transaction {
            val id =
                ChapterTable
                    .insertAndGetId {
                        it[manga] = mangaId
                        it[url] = "/old/$label"
                        it[name] = label
                        it[chapter_number] = number
                        it[sourceOrder] = 0
                        it[isDownloaded] = true
                        it[pageCount] = 20
                    }.value
            ChapterUserTable.insert {
                it[chapter] = id
                it[user] = userId
                it[isRead] = true
                it[isBookmarked] = true
                it[lastReadAt] = 1700000000L
                it[lastPageRead] = 17
                it[isDownloaded] = true
                it[isDownloadRequested] = true
            }
            id
        }

    private fun prepared(vararg labels: Pair<String, Float>): SameIdMigration.Prepared =
        SameIdMigration.Prepared(
            SManga.create().apply {
                title = "New title"
                url = "/new"
                description = "Destination description"
            },
            labels.map { (label, number) ->
                SChapter.create().apply {
                    name = label
                    url = "/new/$label"
                    chapter_number = number
                }
            },
            "https://destination.example/new",
        )

    @Test
    fun `replaces source keeps ID preferences and per-user read cutoff`() =
        runTest {
            val old = manga("original", 1001L)
            val target = manga("destination", 1002L)
            val matched = chapter(old, "Chapter 1", 1f)
            chapter(old, "Removed special", -1f)
            val secondUser =
                transaction {
                    UserAccountTable
                        .insertAndGetId {
                            it[username] = "migration-reader"
                            it[password] = "unused"
                        }.value
                }
            transaction {
                MangaUserTable.insert {
                    it[manga] = old
                    it[user] = 1
                    it[inLibrary] = true
                    it[viewer] = 3
                }
                MangaMetaTable.insert {
                    it[ref] = old
                    it[user] = 1
                    it[key] = "reader-setting"
                    it[value] = "keep"
                }
                ChapterUserTable.insert {
                    it[chapter] = matched
                    it[user] = secondUser
                    it[lastReadAt] = 1700000010L
                    it[isRead] = false
                }
            }
            var cleaned = false
            val result =
                SameIdMigration.execute(old, target, { prepared("Chapter 1" to 1f, "Chapter 2" to 2f) }) {
                    transaction {
                        assertEquals(
                            1001L,
                            MangaTable.selectAll().where { MangaTable.id eq old }.single()[MangaTable.sourceReference],
                        )
                    }
                    assertFailsWith<IllegalStateException> { MigrationGate.access(old) {} }
                    cleaned = true
                }
            assertTrue(cleaned)
            assertEquals(old, result)
            transaction {
                val row = MangaTable.selectAll().where { MangaTable.id eq old }.single()
                assertEquals(1002L, row[MangaTable.sourceReference])
                assertEquals("/destination", row[MangaTable.url])
                assertEquals("New title", row[MangaTable.title])
                assertFalse(MangaTable.selectAll().where { MangaTable.id eq target }.any())
                assertEquals(3, MangaUserTable.selectAll().where { MangaUserTable.manga eq old }.single()[MangaUserTable.viewer])
                assertEquals("keep", MangaMetaTable.selectAll().single()[MangaMetaTable.value])
                val chapters = ChapterTable.selectAll().where { ChapterTable.manga eq old }.toList()
                assertEquals(2, chapters.size)
                assertTrue(
                    chapters.all {
                        it[ChapterTable.url].startsWith("/new/") && !it[ChapterTable.isDownloaded] &&
                            it[ChapterTable.pageCount] == -1
                    },
                )
                val newId = chapters.single { it[ChapterTable.name] == "Chapter 1" }[ChapterTable.id]
                val states = ChapterUserTable.selectAll().where { ChapterUserTable.chapter eq newId }.toList()
                assertEquals(1, states.size)
                assertTrue(states.all { it[ChapterUserTable.lastPageRead] == 0 && !it[ChapterUserTable.isDownloaded] })
                assertTrue(states.single { it[ChapterUserTable.user].value == 1 }[ChapterUserTable.isRead])
                assertTrue(states.none { it[ChapterUserTable.user].value == secondUser })
                assertFalse(states.single()[ChapterUserTable.isBookmarked])
                assertEquals(0L, states.single()[ChapterUserTable.lastReadAt])
            }
        }

    @Test
    fun `fetch failure and empty destination do not clean or replace`() =
        runTest {
            val old = manga("original", 1001)
            val target = manga("destination", 1002)
            val chapter = chapter(old, "Chapter 1", 1f)
            assertFailsWith<IllegalStateException> {
                SameIdMigration.execute(
                    old,
                    target,
                    { error("network") },
                    { error("must not clean") },
                )
            }
            assertFailsWith<IllegalArgumentException> { SameIdMigration.execute(old, target, { prepared() }, { error("must not clean") }) }
            transaction {
                assertTrue(ChapterTable.selectAll().where { ChapterTable.id eq chapter }.single()[ChapterTable.isDownloaded])
            }
        }

    @Test
    fun `allows used destinations and migrating back while retaining original preferences`() =
        runTest {
            val old = manga("original", 1001)
            chapter(old, "Read chapter", 1f)
            transaction {
                MangaUserTable.insert {
                    it[manga] = old
                    it[user] = 1
                    it[inLibrary] = true
                    it[viewer] = 3
                }
            }
            for (source in listOf(1002L, 1001L)) {
                val target = manga("destination-$source", source)
                chapter(target, "Destination read chapter", 99f)
                transaction {
                    MangaUserTable.insert {
                        it[manga] = target
                        it[user] = 1
                        it[inLibrary] = true
                        it[viewer] = 7
                    }
                    MangaMetaTable.insert {
                        it[ref] = target
                        it[user] = 1
                        it[key] = "destination-setting"
                        it[value] = "discard"
                    }
                }
                SameIdMigration.execute(old, target, { prepared("Chapter 1" to 1f, "Chapter 2" to 2f) }, {})
                transaction {
                    assertEquals(source, MangaTable.selectAll().single()[MangaTable.sourceReference])
                    assertEquals(3, MangaUserTable.selectAll().single()[MangaUserTable.viewer])
                    assertFalse(MangaMetaTable.selectAll().any())
                    assertEquals(1L, ChapterUserTable.selectAll().count())
                    assertTrue(ChapterUserTable.selectAll().single()[ChapterUserTable.isRead])
                }
            }
        }

    @Test
    fun `cleanup failure keeps history and source and permits explicit retry`() =
        runTest {
            val old = manga("original", 1001)
            val target = manga("destination", 1002)
            val chapter = chapter(old, "Chapter 1", 1f)
            assertFailsWith<IllegalStateException> {
                SameIdMigration.execute(old, target, { prepared("Chapter 1" to 1f) }, { error("disk failure") })
            }
            transaction {
                assertEquals(1001L, MangaTable.selectAll().where { MangaTable.id eq old }.single()[MangaTable.sourceReference])
                assertFalse(ChapterTable.selectAll().where { ChapterTable.id eq chapter }.single()[ChapterTable.isDownloaded])
                assertEquals(1700000000L, ChapterUserTable.selectAll().single()[ChapterUserTable.lastReadAt])
            }
            MigrationGate.access(old) {} // Failure leaves no persistent migration lock.
            SameIdMigration.execute(old, target, { prepared("Chapter 1" to 1f) }, {})
        }

    @Test
    fun `database failure rolls replacement back and allows ordinary retry`() =
        runTest {
            val old = manga("original", 1001)
            val target = manga("destination", 1002)
            chapter(old, "Chapter 1", 1f)
            val invalid = prepared("Chapter 1" to 1f).let { it.copy(realUrl = "x".repeat(3000)) }
            assertFailsWith<Exception> { SameIdMigration.execute(old, target, { invalid }, {}) }
            transaction {
                assertEquals(1001L, MangaTable.selectAll().where { MangaTable.id eq old }.single()[MangaTable.sourceReference])
                assertEquals("/old/Chapter 1", ChapterTable.selectAll().single()[ChapterTable.url])
                assertEquals(1700000000L, ChapterUserTable.selectAll().single()[ChapterUserTable.lastReadAt])
                assertFalse(ChapterTable.selectAll().single()[ChapterTable.isDownloaded])
            }
            SameIdMigration.execute(old, target, { prepared("Chapter 1" to 1f) }, {})
        }

    @Test
    fun `busy source operation rejects migration before destructive work`() =
        runTest {
            val old = manga("original", 1001)
            val target = manga("destination", 1002)
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val reader =
                async {
                    MigrationGate.access(old) {
                        entered.complete(Unit)
                        release.await()
                    }
                }
            entered.await()
            try {
                assertFailsWith<IllegalStateException> { SameIdMigration.execute(old, target, { error("must not fetch") }, {}) }
            } finally {
                release.complete(Unit)
                reader.await()
            }
        }

    @Test
    fun `nested source guards keep outer operation active and release after failure`() =
        runTest {
            val old = manga("original", 1001)
            MigrationGate.access(old) {
                MigrationGate.access(old) {}
                assertFailsWith<IllegalStateException> { MigrationGate.exclusive(setOf(old)) {} }
                assertFailsWith<IllegalStateException> { MigrationGate.access(old) { error("fetch failed") } }
                assertFailsWith<IllegalStateException> { MigrationGate.exclusive(setOf(old)) {} }
            }
            MigrationGate.exclusive(setOf(old)) {}
        }

    @Test
    fun `destination ranges use highest read and discard old gaps and history`() =
        runTest {
            for (range in listOf(1..45, 15..40, 35..44)) {
                val old = manga("original-${range.first}", 1001)
                val target = manga("destination-${range.first}", 1002)
                chapter(old, "Highest read", 30f)
                val gap = chapter(old, "Unread gap", 5f)
                val later = chapter(old, "Unread later", 50f)
                transaction {
                    ChapterUserTable.update({ ChapterUserTable.chapter inList listOf(gap, later) }) { it[isRead] = false }
                }
                SameIdMigration.execute(old, target, { prepared(*range.map { "New chapter $it" to it.toFloat() }.toTypedArray()) }, {})
                transaction {
                    val chapters = ChapterTable.selectAll().where { ChapterTable.manga eq old }.toList()
                    assertEquals(range.map { it.toFloat() }, chapters.map { it[ChapterTable.chapter_number] }.sorted())
                    val states =
                        ChapterUserTable
                            .selectAll()
                            .where { ChapterUserTable.chapter inList chapters.map { it[ChapterTable.id] } }
                            .toList()
                    val readIds = states.filter { it[ChapterUserTable.isRead] }.map { it[ChapterUserTable.chapter] }
                    assertEquals(
                        range.filter { it <= 30 }.map { it.toFloat() },
                        chapters.filter { it[ChapterTable.id] in readIds }.map { it[ChapterTable.chapter_number] }.sorted(),
                    )
                    assertTrue(states.all { it[ChapterUserTable.lastReadAt] == 0L && !it[ChapterUserTable.isBookmarked] })
                }
            }
        }
}
