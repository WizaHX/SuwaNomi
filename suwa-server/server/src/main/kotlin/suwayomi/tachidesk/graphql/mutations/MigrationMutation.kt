package suwayomi.tachidesk.graphql.mutations

import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.manga.impl.migration.SameIdMigration
import suwayomi.tachidesk.server.JavalinSetup.future
import java.util.concurrent.CompletableFuture

class MigrationMutation {
    data class MigrateMangaSameIdInput(
        val originalId: Int,
        val destinationId: Int,
    )

    data class MigrateMangaSameIdPayload(
        val mangaId: Int,
    )

    @RequireAuth
    fun migrateMangaSameId(input: MigrateMangaSameIdInput): CompletableFuture<MigrateMangaSameIdPayload> =
        future {
            MigrateMangaSameIdPayload(SameIdMigration.migrate(input.originalId, input.destinationId))
        }
}
