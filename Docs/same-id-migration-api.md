# Same-ID migration API

Implemented and validated with automated stub-source, database, filesystem and backup round-trip tests. Live-source and Raspberry Pi validation remain outstanding; see [progress](progress.md).

This feature uses an explicit server API command. The existing WebUI migration button continues to use upstream copy/remove behavior and does **not** invoke this command. No custom WebUI is required.

The command is available without an enablement setting. Clients choose when to invoke it. It uses existing authentication; any authenticated user can invoke it without administrator permissions.

## Migrate

Find the destination through its source normally, including the local source. Existing destination library state/history/downloads are allowed. The original ID’s settings and progress are kept; destination-only personal state is discarded, not merged. Use this API rather than the existing migration button. Stop active downloads/refreshes for the two manga before invoking it; busy entries are rejected before cleanup. Run migration while backup restore and library synchronization are idle, and keep them idle until migration finishes. Those upstream paths are unchanged and are not covered by the migration guards.

Execute in the server's authenticated GraphiQL interface at `/api/graphql`, or send the operation to that endpoint with your existing API authentication. Replace `12` and `34` with the original and destination manga IDs:

```graphql
mutation {
  migrateMangaSameId(input: { originalId: 12, destinationId: 34 }) {
    mangaId
  }
}
```

On success, `mangaId` is `12`. Reload the original manga in the client. Destination record `34` and its separate personal state are removed when replacement commits. This command does not swap IDs or create backup manga copies.

The destination supplies source metadata and active chapters. Existing library membership, reader preferences, categories, tracking, and manga metadata stay attached to the original ID. Chapter URLs, downloaded files, page caches, covers, page positions, and source-dependent hashes are discarded. Migration to/from local-source manga is supported; the local content files remain in their source directory. Only the destination must be available. An absent/broken original extension or website does not block migration. Cleanup uses upstream’s source-or-stub paths, so old folders whose source name is no longer resolvable may remain on disk.

For each user, every destination chapter at or below their highest original read chapter number is marked read; chapters above remain unread. With no original read chapters, all remain unread. Gaps are intentionally filled: an old unread chapter below that number does not stay unread. The entire chapter list and its names/URLs come from the destination. Old chapter history, bookmarks and page positions are discarded; there is no individual chapter matching or special-chapter fallback.

## Moving between upstream and SuwaNomi

Backups must work in both directions: upstream → SuwaNomi → upstream → SuwaNomi. Backup schemas and backup/restore code stay unchanged. Export a normal backup and restore it into a fresh server compatible with the imported baseline. Use backups for portability rather than relying on raw database compatibility across server versions.

Normal manga, tracker, category, preference, active-chapter and reading-history data follow upstream backup options. Upstream backup/restore does not promise to retain numeric manga IDs. There is no custom history archive to transfer.

## If migration fails

The original source and reading state remain until replacement commits. Deleted downloads/caches cannot be rolled back; download flags are cleared before file deletion. After fixing the cause, retry the same command with the same IDs. Missing files are tolerated during cleanup.

A failure does not leave a persistent migration lock. The original manga can be used normally; there is no recovery query, cancellation command, journal table or custom database migration. After a lost connection, check whether the original ID already has the destination source before retrying: a completed migration removes the temporary destination entry.
