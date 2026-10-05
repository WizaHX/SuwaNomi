# Existing migration request trace

Inspected 2026-10-04. This is source-level evidence for the [same-ID migration plan](same-id-migration-plan.md), not a runtime network capture.

## Baselines

- Local server: `cff9169a378013f9eba6646ca1de1ae956ea509b` (imported baseline).
- WebUI: `5596096c7e72a27b051a66b375b642f273830b36` (the plan's research baseline; the owner's running WebUI version has not been checked).
- WebUI files were downloaded to temporary storage for read-only inspection. No WebUI project was added.

## Where the two IDs exist

[`MangaMigration.ts`](https://github.com/Suwayomi/Suwayomi-WebUI/blob/5596096c7e72a27b051a66b375b642f273830b36/src/features/migration/MangaMigration.ts) has both manga objects in `migrate`. It prepares copy and cleanup actions in the browser, runs the copy actions concurrently, then runs cleanup after copying succeeds. The `mode` determines whether cleanup runs.

The original/destination relationship exists in this client function, but it does not send that pair and mode in a single migration request.

## Requests sent

[`RequestManager.ts`](https://github.com/Suwayomi/Suwayomi-WebUI/blob/5596096c7e72a27b051a66b375b642f273830b36/src/lib/requests/RequestManager.ts) maps the actions to ordinary API calls:

| Action | Information sent | Limitation for same-ID migration |
| --- | --- | --- |
| Read original or destination | One manga ID plus requested data flags | Separate calls; no pair or migration session ID |
| Fetch destination | One manga ID and fetch flags | Does not identify the original |
| Add destination to library | Destination ID and `inLibrary: true` | Same operation as ordinary library addition |
| Copy categories / metadata | Destination IDs and copied values | Original manga ID is absent |
| Copy chapter state | Destination chapter IDs and state patches | Original chapter IDs are absent |
| Copy tracker | Destination manga ID and original track-record ID | Indirectly identifies original, but optional and also used in copy mode |
| Delete old downloads | Original chapter IDs | Destination absent; optional |
| Unbind old trackers | Original track-record IDs | Destination absent; optional |
| Remove original from library | Original ID and `inLibrary: false` | Same operation as ordinary library removal |

The exact [`UPDATE_MANGA` GraphQL document](https://github.com/Suwayomi/Suwayomi-WebUI/blob/5596096c7e72a27b051a66b375b642f273830b36/src/lib/graphql/manga/MangaMutation.ts) combines category and library updates for a single manga; it does not combine original and destination. RequestManager supplies no migration correlation token.

Server counterparts:

- `suwa-server/server/src/main/kotlin/suwayomi/tachidesk/graphql/mutations/MangaMutation.kt`: `UpdateMangaInput` contains one ID and a library patch; `FetchMangaAndChaptersInput` also contains one ID.
- `suwa-server/server/src/main/kotlin/suwayomi/tachidesk/graphql/mutations/TrackMutation.kt`: `BindTrackRecordInput` contains destination manga ID and a track-record ID.
- `suwa-server/server/src/main/kotlin/suwayomi/tachidesk/manga/impl/track/Track.kt`: `bindTrackRecord` can resolve the original manga through the track record, but copies the binding. It is not a mandatory migration operation.

## Why the existing calls are insufficient

With optional state copying disabled, the migration write sequence reduces to adding one manga to the library and removing another. Ordinary independent library edits can produce the same requests. Inferring a destructive replacement from their timing or order would change unrelated actions.

Even when tracking is enabled, there may be no bindings to copy, or the destination may already have them. Tracker copying also happens in copy mode. It cannot be the general migration trigger.

The client's chapter logic marks destination chapters up to the highest read chapter number as read. The owner subsequently chose the same cutoff rule for the server command. Intercepting only the final library-removal request still cannot identify the destination reliably.

## Integration decision

The server can own validation, read-cutoff transfer, file cleanup, and transactional replacement. It needs an explicit original/destination pair to invoke that work reliably.

The owner selected option 1 on 2026-10-04:

1. Keep the server-only scope and invoke a dedicated API operation explicitly with both IDs. This changes the owner's migration workflow; the existing button remains the upstream flow.
2. Allow a minimal client call change so the existing button invokes the new server operation and uses its retained ID. This changes the previously agreed scope and requires the owner's decision before introducing client work.

Do not implement heuristic interception, silently add client work, or describe a new API operation as integrated with the unchanged button. The new server command is invoked explicitly; the existing UI button is unchanged. Runtime navigation and the installed client version remain unverified.

## Upstream flow versus the same-ID command

| Behavior | Pinned upstream WebUI flow | Same-ID server command |
| --- | --- | --- |
| Identity | Adds destination to library; removes original from library | Keeps original ID; replaces source fields and active chapters |
| Trackers/categories/preferences | Optional copying to another manga, then cleanup | Already attached to retained ID; no copying/unbinding needed |
| Read status | Marks destination chapters up to the highest read number | Same highest-read cutoff, applied per user; gaps become read too |
| Chapter labels | Uses numbers for read progress | Uses only the highest read number; all names/URLs come from the destination |
| Downloads | Optional deletion of old downloaded chapters after copy | Mandatory removal of old downloads/caches before source replacement |
| Destination state | Can add/copy into a destination with existing state | Allows existing destination state; original ID’s personal state wins and the separate destination row is deleted |
| Failure handling | Separate requests; no migration journal | Database replacement is atomic; ordinary retry after failure, no journal/recovery API |
| Invocation | Existing client button coordinates calls | Explicit original/destination API pair, as selected by the owner |

The initial implementation added a persistent journal, a numbered database migration, pending-operation query, cancel command and post-failure locks. These were removed: original source data remains available until replacement commits, so normal retry is sufficient. File deletion itself remains irreversible, as in upstream.

The in-memory file/source guards remain because replacing an existing manga binding while its old download or refresh writes are active could mix source data. Path checks remain because upstream storage directories are title-based and can be shared. No custom backup or database schema is needed.
