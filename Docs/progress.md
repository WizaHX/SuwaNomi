# Current progress

Updated: 2026-10-07. Slim Docker packaging is merged into local `main`; publication is pending. See [Docker deployment](docker-deployment.md) for build, publish and run instructions.

## Verified

- Same-ID migration works repeatedly in both directions through the WebUI, preserves the read cutoff and deletes old downloaded chapters. Source-result cache and navigation-ID fixes are included.
- Database snapshot: no orphaned records across 27 foreign-key relationships and no duplicate manga source/URL pairs; manga 49's chapters and read status were consistent.
- Server checks: 29 migration/API and backup tests plus style checks passed. Client type, lint, production build and focused migration checks passed.
- Published AMD64 image: backup restore, WebView page loading and migration passed. A SuwaNomi backup also restored successfully into upstream stable v2.4.2366.
- Slim image tested locally on AMD64 with existing data: startup, WebView, repeated migration and container recreation passed. Disk usage dropped from 2.48 GB to 2.13 GB; compressed content from 848 MB to 684 MB.

## Next

- Push merged `main` and run the Docker workflow to publish the slim image as `latest`. The previous publication is [run 37597121426](https://github.com/WizaHX/SuwaNomi/actions/runs/37597121426).
- Test the published slim image on the Raspberry Pi's ARM64 OS.
- WebView cookie sharing remains unverified. Batch migration testing is deferred at the owner's request.
