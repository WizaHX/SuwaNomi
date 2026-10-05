package suwayomi.tachidesk.manga.impl.migration

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.chapter.ChapterRecognition
import eu.kanade.tachiyomi.util.chapter.ChapterSanitizer.sanitize
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import suwayomi.tachidesk.manga.impl.Manga
import suwayomi.tachidesk.manga.impl.download.DownloadManager
import suwayomi.tachidesk.manga.impl.util.getMangaCacheDir
import suwayomi.tachidesk.manga.impl.util.getMangaDownloadDir
import suwayomi.tachidesk.manga.impl.util.source.GetSource
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.server.ApplicationDirs
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.nio.file.Files
import java.sql.Connection
import java.time.Instant

object SameIdMigration {
    private val dirs: ApplicationDirs by injectLazy()

    data class Prepared(
        val manga: SManga,
        val chapters: List<SChapter>,
        val realUrl: String?,
    )

    suspend fun migrate(
        originalId: Int,
        destinationId: Int,
    ): Int = execute(originalId, destinationId, ::prepare, ::cleanup)

    internal suspend fun execute(
        originalId: Int,
        destinationId: Int,
        prepareDestination: suspend (ResultRow) -> Prepared,
        cleanFiles: suspend (List<String>) -> Unit,
    ): Int {
        require(originalId > 0 && destinationId > 0 && originalId != destinationId) { "Use two different positive manga IDs" }
        return MigrationGate.exclusive(setOf(originalId, destinationId)) {
            val (original, destination) = transaction { manga(originalId) to manga(destinationId) }
            // Network errors and malformed/empty chapter lists must precede any cleanup.
            val prepared = prepareDestination(destination)
            require(prepared.manga.title.isNotBlank()) { "Destination title is empty" }
            require(prepared.chapters.isNotEmpty()) { "Destination has no chapters" }
            require(
                prepared.chapters.all {
                    it.url.isNotBlank() && it.url.length <= 2048 && it.name.isNotBlank() &&
                        it.chapter_number.isFinite()
                },
            ) {
                "Destination returned an invalid chapter"
            }
            require(
                prepared.chapters
                    .mapTo(mutableSetOf()) { it.url }
                    .size == prepared.chapters.size,
            ) { "Destination returned duplicate chapter URLs" }
            val paths = cleanupPaths(original, destination)
            transaction {
                checkBinding(original)
                checkBinding(destination)
            }
            clearDownloads(setOf(originalId, destinationId))
            cleanFiles(paths)
            transaction(transactionIsolation = Connection.TRANSACTION_SERIALIZABLE) {
                maxAttempts = 1
                MangaTable
                    .selectAll()
                    .where { MangaTable.id inList listOf(originalId, destinationId) }
                    .forUpdate()
                    .toList()
                checkBinding(original)
                checkBinding(destination)
                val highestNumber = ChapterTable.chapter_number.max()
                val highestReadByUser =
                    (ChapterTable innerJoin ChapterUserTable)
                        .select(ChapterUserTable.user, highestNumber)
                        .where { (ChapterTable.manga eq originalId) and (ChapterUserTable.isRead eq true) }
                        .groupBy(ChapterUserTable.user)
                        .associate { it[ChapterUserTable.user] to it[highestNumber]!! }
                ChapterTable.deleteWhere { manga eq originalId }
                val now = Instant.now().epochSecond
                prepared.chapters.forEachIndexed { index, chapter ->
                    val chapterId =
                        ChapterTable.insertAndGetId {
                            it[manga] = originalId
                            it[url] = chapter.url
                            it[name] = chapter.name
                            it[chapter_number] = chapter.chapter_number
                            it[scanlator] = chapter.scanlator
                            it[date_upload] = chapter.date_upload
                            it[fetchedAt] = now
                            it[sourceOrder] = prepared.chapters.lastIndex - index
                            it[memo] = chapter.memo
                        }
                    highestReadByUser.forEach { (userId, highestRead) ->
                        if (chapter.chapter_number <= highestRead) {
                            ChapterUserTable.insert {
                                it[ChapterUserTable.chapter] = chapterId
                                it[user] = userId
                                it[isRead] = true
                            }
                        }
                    }
                }
                MangaTable.update({ MangaTable.id eq originalId }) {
                    it[sourceReference] = destination[MangaTable.sourceReference]
                    it[url] = destination[MangaTable.url]
                    it[title] = prepared.manga.title
                    it[artist] = prepared.manga.artist
                    it[author] = prepared.manga.author
                    it[description] = prepared.manga.description
                    it[genre] = prepared.manga.genre
                    it[status] = prepared.manga.status
                    it[thumbnail_url] = prepared.manga.thumbnail_url
                    it[thumbnailUrlLastFetched] = now
                    it[initialized] = true
                    it[realUrl] = prepared.realUrl
                    it[lastFetchedAt] = now
                    it[chaptersLastFetchedAt] = now
                    it[updateStrategy] = prepared.manga.update_strategy.name
                    it[memo] = prepared.manga.memo
                }
                MangaTable.deleteWhere { id eq destinationId }
                originalId
            }
        }
    }

    private fun clearDownloads(mangaIds: Set<Int>) {
        val chapterIds =
            transaction {
                ChapterTable.selectAll().where { ChapterTable.manga inList mangaIds }.map { it[ChapterTable.id].value }
            }
        DownloadManager.dequeue(chapterIds)
        transaction {
            ChapterTable.update({ ChapterTable.id inList chapterIds }) { it[isDownloaded] = false }
            ChapterUserTable.update({ ChapterUserTable.chapter inList chapterIds }) {
                it[isDownloaded] = false
                it[isDownloadRequested] = false
                it[koreaderHash] = null
            }
        }
    }

    private suspend fun prepare(destination: ResultRow): Prepared {
        val source =
            GetSource.getSourceOrNull(destination[MangaTable.sourceReference]) ?: error("Destination source is not installed or available")
        val update = Manga.fetchMangaAndChapters(destination, source, fetchDetails = true, fetchChapters = true)
        update.manga.url = destination[MangaTable.url]
        update.chapters.forEach { chapter ->
            (source as? HttpSource)?.prepareNewChapter(chapter, update.manga)
            chapter.chapter_number =
                ChapterRecognition.parseChapterNumber(update.manga.title, chapter.name, chapter.chapter_number.toDouble()).toFloat()
            chapter.name = chapter.name.sanitize(update.manga.title)
            chapter.scanlator = chapter.scanlator?.trim()?.ifBlank { null }
        }
        return Prepared(update.manga, update.chapters, (source as? HttpSource)?.getMangaUrl(update.manga))
    }

    private fun manga(id: Int): ResultRow =
        MangaTable.selectAll().where { MangaTable.id eq id }.singleOrNull() ?: error("Manga $id does not exist")

    private fun checkBinding(expected: ResultRow) {
        val current = manga(expected[MangaTable.id].value)
        check(
            current[MangaTable.sourceReference] == expected[MangaTable.sourceReference] &&
                current[MangaTable.url] == expected[MangaTable.url] &&
                current[MangaTable.title] == expected[MangaTable.title],
        ) {
            "Manga changed while migration was preparing; retry"
        }
    }

    private suspend fun cleanupPaths(
        original: ResultRow,
        destination: ResultRow,
    ): List<String> {
        val paths = mutableListOf<String>()
        for (row in listOf(original, destination)) {
            val source = GetSource.getSourceOrStub(row[MangaTable.sourceReference])
            val downloadDir = File(getMangaDownloadDir(row[MangaTable.title], source.toString())).absoluteFile.normalize()
            val cacheDir = File(getMangaCacheDir(row[MangaTable.title], source.toString())).absoluteFile.normalize()
            // Paths are title-based upstream. Never delete a directory shared by another manga.
            val others = transaction { MangaTable.selectAll().where { MangaTable.id neq row[MangaTable.id] }.toList() }
            for (other in others) {
                val otherSource = GetSource.getSourceOrStub(other[MangaTable.sourceReference])
                require(
                    File(getMangaDownloadDir(other[MangaTable.title], otherSource.toString())).canonicalFile != downloadDir.canonicalFile,
                ) {
                    "Download directory is shared with manga ${other[MangaTable.id].value}; migration cannot safely clean it"
                }
            }
            paths += downloadDir.path
            paths += cacheDir.path
            for (root in listOf(dirs.thumbnailDownloadsRoot, dirs.tempThumbnailCacheRoot)) {
                paths +=
                    File(root)
                        .listFiles()
                        .orEmpty()
                        .filter { it.name.startsWith("${row[MangaTable.id].value}.") }
                        .map { it.absolutePath }
            }
        }
        return paths.distinct()
    }

    private fun cleanup(paths: List<String>) {
        val roots =
            listOf(dirs.mangaDownloadsRoot, dirs.tempMangaCacheRoot, dirs.thumbnailDownloadsRoot, dirs.tempThumbnailCacheRoot).map {
                File(it).canonicalFile.toPath()
            }
        paths.forEach { value ->
            val file = File(value)
            val path = file.canonicalFile.toPath()
            require(file.absoluteFile.normalize().toPath() == path) { "Cleanup paths must not traverse symbolic links" }
            require(
                roots.any {
                    path != it && path.startsWith(it)
                },
            ) { "Cleanup path is outside the configured storage roots" }
            if (file.exists()) {
                // Do not follow links into unrelated storage.
                Files.walk(file.toPath()).use { entries ->
                    entries.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) }
                }
            }
        }
    }
}
