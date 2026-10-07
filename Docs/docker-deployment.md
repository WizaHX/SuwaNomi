# Docker deployment

The image includes both the custom server and WebUI. It uses a pinned [upstream Suwayomi runtime](https://github.com/Suwayomi/Suwayomi-Server-docker), retaining its non-root user, CEF/browser libraries, Xvfb, startup environment variables and data location. The custom WebUI is copied into the data directory on each startup; upstream WebUI auto-updates are disabled so they cannot replace it.

## Publish on GitHub

1. Make the repository public and merge the Docker setup into its default branch.
2. In **Actions → Build and publish Docker image → Run workflow**, select `main`.
3. After the first successful publication, open the `suwanomi` package settings on GitHub and set its visibility to **Public**. GHCR package visibility is separate from repository visibility.
4. The public image is available as `ghcr.io/wizahx/suwanomi:latest` without a login.

The workflow also runs for pushed `v*` tags, publishing that tag and `latest`. Every run publishes an immutable-by-convention `sha-<full-commit>` tag too. Manual builds from other branches publish only their SHA tag. Ordinary commits do not trigger builds.

The workflow uses GitHub's built-in `GITHUB_TOKEN` with `packages: write` to publish; no personal token or registry password needs to be configured. This authentication is required for publishing public images too.

Both `linux/amd64` and `linux/arm64` are published under the same image name. Docker picks the native architecture. Mint AMD64 is the local test host; the Pi 4 needs a 64-bit ARM OS (`uname -m` should report `aarch64`). Actual Pi validation is still pending.

## Run or update

Save the root `compose.yaml` on the host, then run:

```sh
sudo docker compose pull
sudo docker compose up -d
sudo docker compose logs -f
```

The default port is 4567 and data is stored in the Compose-managed `suwanomi-data` volume. The container path is `/home/suwayomi/.local/share/Tachidesk`, matching upstream. The supplied Compose file uses fresh storage; it does not mount any existing library. Do not use `down -v` if you want to retain its data.

Set `SUWANOMI_IMAGE` to a version/SHA tag for a fixed version, or `SUWANOMI_PORT` and `SUWANOMI_BIND_IP` to change the published port/address. Upstream environment settings such as `AUTH_MODE` can be added under `environment` in Compose.

Updates are deployed with the pull/up commands above, not the upstream server/WebUI update buttons.

## Local build and test

Prerequisites: Java 21, Node 24.20 or newer, pnpm 11.1.2, Docker with Buildx/Compose. From the repository root:

```sh
sh docker/build-artifacts.sh
sudo docker build -t suwanomi:local .
sudo env SUWANOMI_IMAGE=suwanomi:local SUWANOMI_PORT=4568 \
  docker compose -p suwanomi-preview up -d
sudo docker compose -p suwanomi-preview logs -f
```

This uses separate test storage. Open `http://localhost:4568`; test WebView and the migration button before using a real library. The build script writes ignored artifacts to `.docker-build/`; the Docker context contains only those artifacts and the startup wrapper.

## Validation status

- Verified upstream runtime manifest includes AMD64/ARM64 and retains its non-root user and tini entrypoint.
- Workflow lint, shell syntax, Compose validation and an isolated startup-wrapper test passed. The wrapper replaces stale UI assets while preserving a database sentinel across restarts.
- CI builds and smoke-tests an AMD64 image (HTTP startup, WebUI version query, migration API schema, bundled UI files) before publishing both architectures. Workflow [37597121426](https://github.com/WizaHX/SuwaNomi/actions/runs/37597121426) passed and published both architectures; anonymous GHCR access was verified. The first local container started but exposed a missing WebUI revision file; packaging is corrected and the owner confirmed the rebuilt UI loads. Local Docker requires the owner's interactive sudo password.
- With the published image on Mint AMD64, owner restored a backup successfully and confirmed WebView opens and loads pages, and repeated single-manga migrations between MangaBall and Qi Scans succeeded, including deletion of old downloaded chapters. Cookie sharing, ARM64/Pi and persistence on container recreation still need runtime tests. Batch migration testing is deferred by the owner.
