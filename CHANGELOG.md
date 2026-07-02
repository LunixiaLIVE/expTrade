# expTrade — Changelog

Trade XP between players, with a chest-GUI request queue.
Works on client and server.

Format based on [Keep a Changelog](https://keepachangelog.com/); versioning per [SemVer](https://semver.org/).

## [1.3.0] — 2026-07-02

Multi-loader release for **Minecraft 1.21.9 – 1.21.10**.

### Added
- **True single-file multi-loader jar** (`-multi.jar`): a jar-in-jar bundle that runs on **both Fabric and NeoForge** — each loader loads its own nested build. (Per-loader `-fabric` / `-neoforge` jars are also produced in `build/staging/`.)
- **Minecraft 1.21.9 and 1.21.10** compatibility.

### Notes
- **Floor is 1.21.9:** expTrade uses `GameProfile.id()`/`.name()` (the record-style accessors) and `ResolvableProfile.createResolved(...)`, both introduced in 1.21.9. Older patches use `getId()`/`getName()` + `new ResolvableProfile(...)` and would need a separate build.
- **Ceiling is 1.21.10:** 1.21.11 renamed `ResourceLocation`→`Identifier` and changed the command-permission API — a hard break — so it lives on `multi_1.21.11`.

### Changed
- **No Architectury API required** — expTrade is fully standalone. Events are wired natively (Fabric API on Fabric, the NeoForge event bus on NeoForge).
- For ≤1.21.10 the command permission uses classic `hasPermission(2)` and the Fabric attachment id uses `ResourceLocation`.

### Dependencies
- **Fabric side:** Minecraft 1.21.9–1.21.10, Fabric Loader >= 0.19.2, Fabric API *(Fabric only)*
- **NeoForge side:** Minecraft 1.21.9–1.21.10, NeoForge 21.9.x–21.10.x  *(no Fabric API, no Architectury)*
