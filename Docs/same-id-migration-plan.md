# Manga migration with a stable server ID

Status: planning only. No application implementation yet.

## Objective

Add an optional server setting, **Use same ID on migration**. When enabled, changing a manga’s source retains its existing database ID and server URL. The destination source becomes authoritative for manga details and the active chapter list.

## Agreed requirements

- Default the setting to off to preserve existing behavior.
- Keep the original manga row and ID. No primary-key swapping or rollback copy.
- Replace source binding, upstream URL, title, description, cover, and other source-owned information.
- Retain library membership, categories, tracking associations, and reasonable user settings.
- Retain minimal reading history: manga, chapter name/number, and reading date, for each affected user.
- Preserve read status on confidently matched destination chapters. Retain bookmarks where a reliable match exists.
- Discard old active chapter links, downloads, and source-dependent page/cover caches.
- Historical information needs no working URL, page data, or download state. Unnecessary fields may be blank, null, or defaulted as allowed by the schema.
- Chapters missing from the new source must not lose their historical reading information.
- No requirement to preserve exact page position across different editions.

## Current source findings

Research baseline, to recheck before coding:

- Server commit: `cff9169a378013f9eba6646ca1de1ae956ea509b`.
- WebUI commit: `5596096c7e72a27b051a66b375b642f273830b36`.
- Migration is currently coordinated by WebUI `src/features/migration/MangaMigration.ts`. It copies selected state to the destination and removes the original from the library through multiple API calls.
- Server `MangaTable` separates the integer ID from `sourceReference` and `url`. Keeping the ID does not require primary-key manipulation.
- `ChapterUserTable` contains per-user read state and `lastReadAt`, with a cascading reference to chapter records. Deleting old chapters without preserving their state can erase history.
- Current master shares manga/chapter content across users while storing personal state separately. In-place replacement affects everyone referencing the manga.

These findings come from source inspection, not runtime tests. Inspect actual history storage and queries before selecting the storage design. Preserve all existing recorded dates/events; do not assume that a last-read timestamp constitutes a complete reading-event log.

## Proposed implementation

### Server-only scope (owner decision)

This feature targets the owner’s personal use. Do not require a separate WebUI/site project, companion WebUI source changes, or a custom WebUI build. Implement the replacement in the server in place; replacing existing server behavior is authorized where needed. General-purpose client compatibility and a new multi-user permission framework are not goals. Keep existing authentication and preserve recorded personal data.

The optional **Use same ID on migration** setting remains default-off. Configure it through server configuration if exposing a new control would require WebUI changes.

First verify the exact requests emitted by the unchanged client's migration action, including how original and destination IDs are conveyed and what ID the client uses afterward. The inspected server `MangaMutation.updateManga(s)` inputs contain IDs and an `inLibrary` patch, not an explicit original/destination migration pair. The previously researched client coordinates migration through several ordinary API calls; there is not yet a verified single server migration function to replace.

Prefer replacing or adapting the existing server request path so the unchanged migration action invokes a centralized same-ID migration service. A new server mutation alone is insufficient if the existing client never calls it. Do not infer a destructive migration from timing, matching titles, or unrelated library add/remove calls. Establish a deterministic trigger and source/destination pairing before implementation, and verify client navigation/cache behavior after retaining the original ID.

If the unchanged client does not transmit enough information to distinguish migration reliably, document that concrete limitation and choose a server-side invocation/configuration mechanism within this project. Do not silently reintroduce a WebUI project requirement or claim transparent integration has been proven. Any alternative invocation that changes the user's migration workflow must be stated explicitly before implementation.

The internal migration service should accept original and destination IDs, prepare and validate destination content, transplant source data into the original record, and return the retained original ID. Preserve setting-disabled behavior where possible; when enabled, replacing the current migration behavior takes priority over supporting every upstream client option. Ordinary library updates must not accidentally trigger migration.

### Minimal history storage

First inspect history queries, foreign keys, chapter URL constraints, and chapter refresh behavior. Select the smallest compatible implementation:

1. Historical chapter records with cleared source fields and an explicit distinction from active chapters, if this fits the existing model cleanly.
2. Minimal historical snapshots independent of active chapter records, if cleaner for the existing queries and constraints.

Neither storage approach is decided yet. The requirement is historical manga/chapter/date information, not retention of operational chapter data. Blank URLs alone must not let historical records enter active chapter lists, source matching, or downloads. Check uniqueness constraints before using blank/null URLs.

### Operation sequence

1. Validate IDs, permissions, destination source availability, and destination conflicts. Reject migration to the same record.
2. Fetch and validate destination manga details and its complete chapter list before destructive work.
3. Lock conflicting refresh/download/migration operations, revalidate state, and stop or drain in-flight work so it cannot recreate old files or write stale data.
4. Capture personal chapter state and reading history before old chapter deletion.
5. Delete old downloads and source-dependent caches while original metadata still identifies their locations. If cleanup fails, stop before source replacement and report the failure.
6. In one database transaction, preserve minimal history, replace source-owned manga data and active chapters, retain manga-level personal data, and apply reliably matched chapter state.
7. Resolve the prepared destination record without creating accidental duplicates or deleting existing user data.
8. Invalidate remaining in-memory state, publish appropriate update notifications, and return the original manga ID.
9. Release locks in all outcomes.

Filesystem deletion cannot roll back with the database transaction. If replacement fails after cleanup, the original record may remain without downloaded files; this is acceptable. Download flags must reflect removed files even if replacement fails. Reading history and other personal state must remain intact.

Assess whether a small persisted operation record is needed for crash recovery. This would record phases and cleanup targets, not duplicate the old manga. Interrupted cleanup must be retryable rather than silently leaving abandoned files or stale flags indefinitely.

### Conflicts and matching

- The destination may already exist and hold library membership, history, or other users’ data. Do not blindly delete it.
- Prefer rejecting a non-temporary destination conflict before cleanup unless a deliberate merge policy is implemented. Finalize this policy during design.
- Check source lookup/deduplication so future browsing resolves to the retained manga record.
- Match chapters using reliable information, accounting for duplicate numbers, specials, and missing chapters. Chapter position alone is insufficient.
- Preserve unmatched history without marking unrelated destination chapters read.
- Reset source-dependent page positions/counts instead of assuming editions have identical pages.

## Work phases

### 1. Verify migration integration points

Verify settings conventions, API mutation patterns, history storage, download paths, cache invalidation, locking, and test infrastructure. Verify the unchanged client’s request contract through read-only inspection; no WebUI project changes are in scope.

### 2. Finalize storage and API design

Choose minimal history representation, chapter matching rules, destination conflict handling, the deterministic server-side trigger, and setting/service payloads. Use existing authentication for this personal deployment. Document any necessary schema migration. Preserve all affected users’ history.

### 3. Implement server behavior

Implement configuration, migration service, authorization, cleanup coordination, transactional replacement, history retention, and failure handling. Keep the default path unchanged.

### 4. Integrate the server request path

Connect the verified server-side trigger to the migration service without changing WebUI source. Document how to enable the setting in server configuration. Verify completion, subsequent requests, and navigation with the unchanged client. If transparent integration is impossible, resolve the explicit server-only invocation workflow before coding it. Copy-mode compatibility is secondary to the owner’s in-place migration requirement; document any intentional behavior change.

### 5. Validate on a disposable database/server

Validate migration in a disposable container/database before using the real library. Use targeted integration tests and a manual migration to verify:

- Original manga ID/URL remain unchanged and subsequent fetches use the new source.
- Destination details and active chapters become authoritative.
- Tracking, categories, membership, and reasonable settings survive.
- Chapter labels and existing reading dates survive for matched and unmatched chapters, for every affected user.
- Reliable matches retain read state; ambiguous matches do not create false read state.
- History never participates in active chapter fetching or downloading.
- Old links, downloads, and caches are removed and download flags remain correct.
- Destination-fetch and conflict failures happen before cleanup.
- Cleanup failure, database failure after cleanup, concurrent updates, and process interruption leave explicit, recoverable states.
- Setting-disabled migration preserves existing behavior; any intentional change to enabled-mode copy behavior is documented.
- The chosen server-only invocation works without a separate WebUI project or custom WebUI build; an unchanged-client migration path is tested if supported.
- Browsing the destination source does not accidentally create a second canonical record.

## Completion criteria

A user enables the server option and migrates a manga once. New source content appears under the original manga ID. Personal data and minimal reading history remain available, while obsolete source links and files are discarded. Document implementation/schema changes, validation results, and remaining limitations before delivery.
