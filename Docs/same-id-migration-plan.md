# Manga migration with a stable server ID

Status: planning only. No fork or application implementation yet.

## Objective

Add an optional server setting, **Use same ID on migration**. When enabled, changing a manga’s source retains its existing database ID and server URL. The destination source becomes authoritative for manga details and the active chapter list.

Project directory: `/home/user/CodeProjects/SuwaNomi`. Planning documents use the existing `Docs` directory. Fork and check out Suwayomi-Server here when implementation starts; choose a repository layout that retains these documents.

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

Add a dedicated server migration operation accepting original and destination manga IDs and returning the retained original ID. Prepare the destination normally, then transplant its source data into the original record.

The initiating WebUI must call the new operation when the setting is enabled. A server setting alone cannot change its current copy/remove sequence. Identify the smallest companion WebUI change and choose its repository location when coding begins. Keep normal copy mode and setting-disabled migration unchanged.

Apply authorization appropriate to changing a shared manga for all affected users. Ordinary library updates must not implicitly trigger migration.

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

### 1. Establish the fork and verify the baseline

When ready to start, fork/clone the server into the agreed project layout and read repository instructions. Verify settings conventions, API mutation patterns, history storage, download paths, cache invalidation, locking, and test infrastructure. Locate companion WebUI integration points.

### 2. Finalize storage and API design

Choose minimal history representation, chapter matching rules, destination conflict handling, shared-user authorization, and the setting/API payloads. Document any necessary schema migration. Preserve all affected users’ history.

### 3. Implement server behavior

Implement configuration, migration service, authorization, cleanup coordination, transactional replacement, history retention, and failure handling. Keep the default path unchanged.

### 4. Integrate the initiating UI

Expose the server setting and call the operation when enabled. Keep completion results and navigation on the original ID. Preserve copy mode.

### 5. Validate on a disposable database/server

Use targeted integration tests and a manual migration to verify:

- Original manga ID/URL remain unchanged and subsequent fetches use the new source.
- Destination details and active chapters become authoritative.
- Tracking, categories, membership, and reasonable settings survive.
- Chapter labels and existing reading dates survive for matched and unmatched chapters, for every affected user.
- Reliable matches retain read state; ambiguous matches do not create false read state.
- History never participates in active chapter fetching or downloading.
- Old links, downloads, and caches are removed and download flags remain correct.
- Destination-fetch and conflict failures happen before cleanup.
- Cleanup failure, database failure after cleanup, concurrent updates, and process interruption leave explicit, recoverable states.
- Setting-disabled migration and copy mode preserve existing behavior.
- Browsing the destination source does not accidentally create a second canonical record.

## Completion criteria

A user enables the server option and migrates a manga once. New source content appears under the original manga ID. Personal data and minimal reading history remain available, while obsolete source links and files are discarded. Document implementation/schema changes, validation results, and remaining limitations before delivery.

## Repository and deployment decisions

- Project remote: https://github.com/WizaHX/SuwaNomi.
- One top-level repository includes `Docs/`, other project files, and the server under `suwa-server/`.
- Import server as a Git subtree with original history preserved; retain the upstream remote for optional updates.
- Primary deployment target is Docker on a Raspberry Pi. Desktop installers are out of scope.
- Confirm Raspberry Pi model and OS architecture before selecting the image build platform.
- Inspect upstream container packaging and persistent storage conventions before implementing image builds.
- Validate migration in a disposable container/database before using the real library.
- Decide image build/distribution separately; publishing to a registry is not yet requested.
