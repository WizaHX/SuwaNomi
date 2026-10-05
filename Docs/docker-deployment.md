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

Validate changes in a disposable container/database before using the real library. Feature-specific migration checks belong in the [same-ID migration plan](same-id-migration-plan.md).

## Local Docker test host

- Linux Mint on AMD x64 (`linux/amd64`); Docker client 29.8.2 verified on 2026-10-05.
- Test attempt blocked before container creation: host user `user` is not in the `docker` group, and `/var/run/docker.sock` is owned by `root:docker` with mode 660. Daemon access fails outside the Codex sandbox too.
- Grant Docker access on the host, then restart the login session and Codex so the new group membership takes effect. Docker group membership grants root-equivalent access.
- Next: build the current server and run a disposable AMD64 container with isolated storage, then invoke the migration API and verify retained ID, destination chapters and read cutoff. No real library should be mounted.
- An AMD64 test validates container behavior on this PC; ARM64/Pi validation remains separate.
