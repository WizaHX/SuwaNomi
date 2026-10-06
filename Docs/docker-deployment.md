# Docker deployment

The image includes both the custom server and WebUI. It uses a pinned [upstream Suwayomi runtime](https://github.com/Suwayomi/Suwayomi-Server-docker), retaining its non-root user, CEF/browser libraries, Xvfb, startup environment variables and data location. The custom WebUI is copied into the data directory on each startup; upstream WebUI auto-updates are disabled so they cannot replace it.

## Publish on GitHub

1. Merge the Docker setup into the repository's default branch.
2. In **Actions → Build and publish Docker image → Run workflow**, select `main`.
3. After success, the image is available as `ghcr.io/wizahx/suwanomi:latest`.

The workflow also runs for pushed `v*` tags, publishing that tag and `latest`. Every run publishes an immutable-by-convention `sha-<full-commit>` tag too. Manual builds from other branches publish only their SHA tag. Ordinary commits do not trigger builds.

The workflow uses GitHub's built-in `GITHUB_TOKEN` with `packages: write`; no registry password needs to be added to the repository. It works with private repositories. If an existing GHCR package rejects publication, grant this repository Actions access in that package's settings.

Both `linux/amd64` and `linux/arm64` are published under the same image name. Docker picks the native architecture. Mint AMD64 is the local test host; the Pi 4 needs a 64-bit ARM OS (`uname -m` should report `aarch64`). Actual Pi validation is still pending.

## Run or update

Save the root `compose.yaml` on the host. While the GHCR package is private, log in first:

```sh
sudo docker login ghcr.io -u WizaHX
```

At the password prompt, use a GitHub classic personal access token with `read:packages`, not your account password. Then:

```sh
sudo docker compose pull
sudo docker compose up -d
sudo docker compose logs -f
```

The default port is 4567 and data is stored in the Compose-managed `suwanomi-data` volume. The container path is `/home/suwayomi/.local/share/Tachidesk`, matching upstream. The supplied Compose file uses fresh storage; it does not mount any existing library. Do not use `down -v` if you want to retain its data.

Set `SUWANOMI_IMAGE` to a version/SHA tag for a fixed version, or `SUWANOMI_PORT` and `SUWANOMI_BIND_IP` to change the published port/address. Upstream environment settings such as `AUTH_MODE` can be added under `environment` in Compose.

When ready for anonymous pulls, change the **GHCR package** visibility to public. Making the repository public alone does not make an existing private package public. Public images need no registry login. Updates are deployed with the pull/up commands above, not the upstream server/WebUI update buttons.

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
- CI builds and smoke-tests an AMD64 image (HTTP startup, WebUI version query, migration API schema, bundled UI files) before publishing both architectures. GitHub execution is pending. The first local container started but exposed a missing WebUI revision file; packaging is corrected, with a rebuilt-container check pending. Local Docker requires the owner's interactive sudo password.
- Owner confirmed WebView opens and loads pages, and client migration MangaBall → Qi Scans succeeded. Cookie sharing, reverse migration after the client ID fix, ARM64/Pi and persistence on container recreation still need runtime tests.
