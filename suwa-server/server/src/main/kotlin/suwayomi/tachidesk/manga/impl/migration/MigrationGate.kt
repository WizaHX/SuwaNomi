package suwayomi.tachidesk.manga.impl.migration

/** Reject busy migrations before cleanup; prevent new source/file work during migration. */
object MigrationGate {
    private val monitor = Any()
    private val active = mutableMapOf<Int, Int>()
    private val migrating = mutableSetOf<Int>()

    suspend fun <T> access(
        mangaId: Int,
        block: suspend () -> T,
    ): T {
        synchronized(monitor) {
            check(mangaId !in migrating) { "Manga $mangaId is being migrated; retry after migration completes" }
            active[mangaId] = active.getOrDefault(mangaId, 0) + 1
        }
        try {
            return block()
        } finally {
            synchronized(monitor) {
                val remaining = active.getValue(mangaId) - 1
                if (remaining == 0) active.remove(mangaId) else active[mangaId] = remaining
            }
        }
    }

    suspend fun <T> exclusive(
        ids: Set<Int>,
        block: suspend () -> T,
    ): T {
        synchronized(monitor) {
            check(ids.none { it in migrating || active.getOrDefault(it, 0) > 0 }) {
                "Manga is busy; stop its downloads/refreshes and retry migration"
            }
            migrating.addAll(ids)
        }
        try {
            return block()
        } finally {
            synchronized(monitor) { migrating.removeAll(ids) }
        }
    }
}
