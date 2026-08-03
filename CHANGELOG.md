# expTrade — Changelog

Trade XP between players, with a chest-GUI request queue.
Works on client and server.

Format based on [Keep a Changelog](https://keepachangelog.com/); versioning per [SemVer](https://semver.org/).

## [1.3.1] — 2026-08-03

Packaging only. No gameplay changes.

### Changed
- Renamed the combined output jar from `-universal` to `-multi` for consistent naming across the mod suite.

## [1.3.0] — 2026-07-01

First multi-loader release for **Minecraft 26.x** (the 26.2.x line).

### Added
- **Fabric + NeoForge** support from a single **universal** jar (per-loader `-fabric` / `-neoforge` jars are also produced).
- Minecraft **26.2** compatibility.

### Changed
- **No Architectury API required** — expTrade is now fully standalone. Events are wired natively (Fabric API on Fabric, the NeoForge event bus on NeoForge).
- Version pinned to the **26.2.x** line; the jar will not load on a different minor version.

### Dependencies
- **Fabric jar:** Minecraft 26.2.x, Fabric Loader >= 0.19.3, Fabric API 0.153.0+26.2
- **NeoForge jar:** Minecraft 26.2.x, NeoForge 26.2.0.7-beta  *(no Fabric API, no Architectury)*
