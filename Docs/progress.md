# Current progress

Updated: 2026-10-07. Current checkout: `main`. Docker/GHCR setup and migration fixes are merged. Workflow run `37597121426` successfully published commit `821715293890132cf56c6fe5895d4317b0decfe7`; anonymous image access and AMD64/ARM64 manifests verified.

## Completed

- Owner restored a SuwaNomi backup into upstream stable v2.4.2366 (WebUI r3518) in `/home/user/CodeProjects/Tests/Suwayomi`; log confirms SUCCESS with no missing sources/trackers listed. Upstream also emits D-Bus/Vulkan/on-device-model startup messages.

- Published-image test on Mint AMD64 passed: owner restored a backup (server reported SUCCESS), opened restored manga, loaded pages in WebView, and migrated repeatedly in both directions. Test data is bind-mounted under `/home/user/CodeProjects/Tests/SuwaNomi/data`. Earlier local test containers/images/data were cleaned up; old `/tmp` audit/build artifacts may no longer exist.

- Fixed stale migration destinations in `MangaMigration.ts`: successful API migration now clears the existing source browse/search cache as well as Apollo. `useGetSourceMangas` otherwise falls back to raw cached records after Apollo eviction. Two-line application change; no server/backup changes. Regression harness reproduced the old failure and passed six alternating migrations with the actual cache code and a simulated server; failure/cache-preservation and existing copy/API checks passed. TypeScript, focused lint/format and production build passed. UI production build passed; owner verified repeated single-manga migrations in both directions in Docker. Harness: `/tmp/suwanomi-search-cache-check.cjs`; build log: `/tmp/suwanomi-webui-cache-build.log`.

- Owner confirmed downloaded chapter files are deleted on migration.
- Owner confirmed WebView opens and loads pages; cookie sharing is not yet verified. Removed the location-specific timezone default from Compose.
- Prepared Dockerfile, Compose, build/start scripts and a manual/version-tag GHCR workflow for AMD64/ARM64. Uses the pinned upstream runtime plus our server and client; publication succeeded in the workflow run above. Workflow lint, Compose/shell checks and isolated UI-install/persistence checks passed. See [deployment notes](docker-deployment.md).

- Imported Suwayomi-WebUI baseline `f620015410dc78bc7579e895dd2bd8f8344693b6` in `suwa-webUI/`, with full history and `upstream-webui` remote.
- Server API keeps the original manga ID and personal settings, replaces source data and chapters with the destination, then deletes the destination row. Read status uses each user's highest read chapter number; old chapter history is discarded. No enablement setting or admin requirement; authentication remains. Backup code and schema are unchanged.
- WebUI single/bulk Migrate now invokes the server API directly, invalidates old cached manga/chapters and keeps navigation on the original ID. Copy keeps upstream behavior; its checkboxes are labeled separately. Failed requests leave the single-entry dialog open for retry.
- Final code review passed 29 migration/API and backup tests plus style checks. Log: `/tmp/suwanomi-final-review-tests.log`.
- Owner's Docker smoke test: MangaBall → Qiscans retained 49 and removed 75; chapters 1–6 read, 7 unread. API confirmed source/IDs and 152 chapters; user-state/database inspection remains unfinished.
- Client checks passed: full TypeScript check, lint on the five changed files, production Vite build, and isolated service/dialog checks for API dispatch, failures, cache eviction and copy options. Build output: `suwa-webUI/build/`. Logs: `/tmp/suwanomi-webui-{types,lint,build}.log`; check harness: `/tmp/suwanomi-ui-migration-check.cjs`. Node 24.20.0 in `/tmp`; installed dependencies from the unchanged lockfile. Upstream Vite emitted a legacy-minifier compatibility warning but completed.

- Fixed raw translation IDs in the migration dialog/bulk notice by keeping the three custom messages as plain English. TypeScript, focused lint and production build passed; all three texts verified in staged production JavaScript. Included in the published image.

- Fixed missing original ID when selecting a migration destination from a source’s full results page: MangaCard now reads navigation-state mangaId when the URL has none. Callback regression checks passed for URL/state/precedence, along with TypeScript, focused lint and production build. Included in the published image. Repeated round trips exposed a separate stale destination-ID problem, now fixed and verified above.

## Remaining

- Owner confirmed the packaged WebUI loads after adding its missing `revision` file. Its update checker then tried an invalid URL for Custom mode. Client background/About checks now skip Custom mode and About hides its update controls. TypeScript, focused lint and production build passed; updated assets are staged in `.docker-build/webui`. Rebuild/recreate and refresh the browser to verify no update-check errors; existing test data can be retained.
- Batch migration has not been tested end to end; owner explicitly deferred it because they do not use it. WebView cookie sharing and persistence across container recreation remain unverified.
- Copied test H2 database inspected read-only: 27 foreign-key relationships, zero orphan rows, zero duplicate manga source/URL pairs. Library manga 49 is QiScans with 153 chapters: 1–34 read, 35–153 unread; no duplicate chapter URLs, downloads or page records. Manga Ball entry 230 has 312 chapters and no library/user chapter state, consistent with a separately fetched source entry. No categories/trackers are present on 49, so preservation of those cannot be proven from this snapshot. Disk files and before/after growth were not audited. Local results: `/tmp/suwanomi-db-audit/results.txt`; database stays outside Git.
- Remaining deployment checks: persistence across container recreation and ARM64/Pi operation. Restore into upstream stable succeeded. No backup schema/code changes authorized.
- No further behavior removals are approved. Migration guards/validation and normal destination chapter processing remain. Keep backup restore and library synchronization idle during testing; old folders may remain if a removed extension's directory cannot be resolved.

Builds use Java 21 (`/home/user/.jdks/jbr-21.0.11`), `GRADLE_USER_HOME=/tmp/suwanomi-gradle`, and `-Pkotlin.daemon.jvmargs=-Xmx3g --max-workers=2`, from `suwa-server/`. No commit or push is authorized by a handoff entry.
