# Docker deployment on Raspberry Pi

The intended runtime is Docker on the owner’s Raspberry Pi. Desktop installers are out of scope.

## Decisions still needed

- Confirm the Raspberry Pi model and OS architecture before selecting the image build platform.
- Inspect upstream container packaging and persistent storage conventions before implementing image builds. Container packaging is maintained separately from the imported server source.
- Choose the image build and distribution method. Publishing to a registry has not been requested.
- Define persistent volumes for server data and downloads using the verified upstream conventions.

## Validation

Validate changes in a disposable container/database before using the real library. Feature-specific migration checks belong in the [same-ID migration plan](same-id-migration-plan.md).
