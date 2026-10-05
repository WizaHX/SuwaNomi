# Manga migration with a stable server ID

Status: explicit API invocation with no enablement setting. See [progress](progress.md) for validation.

## Objective

Provide an explicit API command accepting original and destination manga IDs. Keep the original manga ID while replacing its source information and active chapters with the destination's content.

The owner selected explicit API invocation. The existing WebUI migration button remains unchanged; no separate WebUI project or custom build is required. See [request trace](migration-request-trace.md) and [API usage](same-id-migration-api.md).

## Agreed requirements

- Clients explicitly invoke the API; no server enablement setting is required. Ordinary library updates and upstream migration/copy calls retain their existing behavior.
- Keep the original manga row and ID. No ID swapping or backup manga copies.
- Use destination source metadata and active chapters. Retain library membership, categories, trackers, manga metadata, and reader settings attached to the original ID.
- For each user, take the highest original chapter number marked read. Mark every destination chapter at or below it read; chapters above it stay unread. With no read chapters, all destination chapters stay unread. Unread gaps below the cutoff are deliberately ignored.
- **Replace the entire chapter list with the destination’s list.** Discard old per-chapter history, bookmarks and positions. Do not add matching logic, special-chapter fallback, historical records or synthetic URLs.
- Discard old chapter links, downloads, covers, page caches, positions, and source-dependent hashes.
- Validate the destination before cleanup; clean old files while original source metadata still identifies their locations, then replace source data transactionally.
- Keep the upstream diff minimal and isolate custom behavior so upstream merges remain manageable.
- **Leave backup schemas and backup/restore code unchanged.** Backups must support upstream → SuwaNomi → upstream → SuwaNomi for standard library data. Retained reading state uses ordinary upstream chapter fields.
- This is a personal deployment. Use existing authentication/permissions; do not invent a new multi-user permission framework.

## Verified source findings

- Server baseline: `cff9169a378013f9eba6646ca1de1ae956ea509b`.
- WebUI research baseline: `5596096c7e72a27b051a66b375b642f273830b36`; the installed client version has not been verified.
- The browser coordinates copy and cleanup requests. No mandatory request supplies a migration pair; optional tracker copying is insufficient as a trigger. See the request trace for exact calls.
- `MangaTable` separates ID from source binding and URL, so replacing its source does not require primary-key changes.
- `ChapterUserTable` stores per-user read state and one `lastReadAt` value, cascading on chapter deletion. It is not a complete reading-event log.
- Manga and chapter content are shared across users. Calculate the read cutoff separately for each user.
- Download paths depend on source name and manga title. Cleanup must reject directories shared with another manga and must not follow paths into unrelated storage.

## Implementation design

### Entry point and read cutoff

Expose `migrateMangaSameId(originalId, destinationId)` through GraphQL, using existing authentication, with no administrator permission requirement. Return the retained manga ID. Reload the retained manga in the client afterward.

For an original highest read chapter of 30: destination 1–45 becomes 1–30 read and 31–45 unread; destination 15–40 becomes 15–30 read and 31–40 unread; destination 35–44 remains entirely unread. Only destination chapters exist afterward. Names, URLs and metadata always come from the destination; no old/new chapter mapping is needed.

Allow destinations with existing personal state or downloads, and allow migration to/from the local source. The original ID’s library state remains authoritative; destination-only personal state is discarded when its separate row is removed. Future source browsing resolves the destination binding to the retained original record.

### Operation sequence

1. Validate authentication, IDs and destination source availability. The original extension or website may be gone.
2. Exclude conflicting source/file work. If a manga is busy, reject before cleanup and allow an explicit retry when idle.
3. Fetch and validate complete destination metadata and chapters before destructive work.
4. Resolve cleanup paths using stored titles and upstream’s source-or-stub lookup. Reject unsafe/shared paths. Missing original extensions must not block migration; folders that cannot be resolved after extension removal may remain on disk.
5. Remove queued downloads and clear download flags before deleting files, so interruption cannot advertise deleted/partial files as complete.
6. Delete downloads/caches at the resolved paths. Local-source content files are source material and remain in the separate local directory. On cleanup failure, stop before source replacement.
7. In one database transaction, revalidate both records, capture each user’s highest read number, replace active chapters/source-owned manga fields, apply the read cutoff, and remove the unused destination.
8. Return the original manga ID and release in-memory guards in all outcomes. Queued download removals use existing notifications.

Filesystem deletion cannot roll back with a database transaction. Failure after cleanup may leave the original manga without downloads, but must preserve its source and personal reading state until replacement commits.

The operation is intended for a maintenance window without concurrent backup restore or library synchronization; those unchanged upstream paths do not acquire the migration guards.

There is no persistent migration state or database schema addition. On failure, the guard is released and the original source remains usable with its reading state intact; downloads may be partially or fully removed. Retry the normal command with the same IDs. Cleanup tolerates missing files. A process restart also releases in-memory guards. No pending-operation query or cancellation command is needed.

### Backup portability

Only standard manga/chapter state remains after migration. No custom history tables or backup fields are needed. Validate ordinary backup export and restore in both directions using the unchanged upstream representation. Use a normal backup restored into a fresh compatible server in either direction. Raw database interchange across versions and preservation of numeric IDs during backup restore are outside this feature’s guarantees.

## Validation checklist

- Original ID remains unchanged; destination metadata and chapters become authoritative.
- Manga-level tracking, categories, library membership, and settings survive.
- Destination chapters at/below each user’s cutoff are read, including old unread gaps; chapters above stay unread.
- Old chapters, bookmarks and reading dates are discarded; no matching or name fallback remains.
- Old URLs, download files, covers, and page caches are removed before source replacement.
- Destination fetch/validation errors occur before cleanup; existing destination state, local sources and missing old extensions do not block migration.
- Cleanup failure, database failure after cleanup, busy operations, and process interruption leave recoverable states with correct download flags.
- Ordinary retry works after cleanup/database failure; failed operations do not leave manga locked.
- The API works without configuration changes; the upstream WebUI workflow remains unchanged.
- Authenticated API invocation works without client changes; unauthorized users cannot invoke migration.
- Backup formats and backup/restore implementation remain unchanged; ordinary library data survives round trips.
- Source lookup after migration resolves to the retained ID.

Use disposable databases and storage for validation. Deployment work follows a working, tested implementation.

## Completion criteria

The client invokes the API with original and destination IDs. Destination content appears under the original ID, the highest-read cutoff and manga-level personal data survive, old chapter data is discarded, and ordinary upstream-compatible backups remain usable in both directions. Record actual checks and outstanding limitations in the progress file before delivery.
