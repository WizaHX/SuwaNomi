# Docker deployment on Raspberry Pi

The intended runtime is Docker on the owner’s Raspberry Pi. Desktop installers are out of scope.

## Target hardware

- Owner-confirmed model: Raspberry Pi 4 Model B.
- Owner describes the OS as “x64”; interpreted as a 64-bit OS on the Pi’s ARM processor, not x86-64.
- Expected native Docker build platform: `linux/arm64` (ARM64 / `aarch64`). Verify on the Pi before building or deploying; `uname -m` should report `aarch64` for the expected kernel. The OS has not been inspected directly.

## Decisions still needed
- Inspect upstream container packaging and persistent storage conventions before implementing image builds. Container packaging is maintained separately from the imported server source.
- Choose the image build and distribution method. Publishing to a registry has not been requested.
- Define persistent volumes for server data and downloads using the verified upstream conventions.

## Validation

Validate changes in a disposable container/database before using the real library.

## Local Docker test host

- Linux Mint on AMD x64 (`linux/amd64`); Docker client 29.8.2 verified on 2026-10-05.
- The owner runs Docker through `sudo` and prefers not to join the Docker group. Codex cannot run sudo Docker commands unattended when a password is required.
- The owner built the server JAR and started `suwanomi-test` on localhost port 4568 using `eclipse-temurin:21-jdk`, with isolated named volume `suwanomi-test-data` mounted at `/data`.
- WebView failed because `libjawt.so` could not load `libXext.so.6`. The minimal Java image is incomplete for CEF/browser support. A separate source request reported Cloudflare bypass disabled.
- Next: adapt upstream Docker packaging for the custom JAR and browser dependencies. Preserve the test volume when replacing the container; do not mount the real library for testing.
- An AMD64 test validates container behavior on this PC; ARM64/Pi validation remains separate.
