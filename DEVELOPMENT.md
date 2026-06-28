# Development & Architecture

This document is the source of truth for how this repository is structured and
why. If the code ever deviates from what is described here, treat that as a
signal to either fix the code or deliberately update this document — don't let
the two drift silently.

## Background

This project was forked from
[`cpburnz/minecraft-prometheus-exporter`](https://github.com/cpburnz/minecraft-prometheus-exporter).
Upstream shipped support by maintaining **one git branch per
(Minecraft version × loader)** — `mc1.21.1-fabric`, `mc1.21.1-forge`,
`mc1.21.1-neoforge`, and so on. That model does not scale and is painful to
maintain.

A later local rewrite then narrowed our fork to **PaperMC only**, dropping
Fabric/Forge/NeoForge entirely.

We are now taking over long-term maintenance. The goals of this restructure:

- Support **PaperMC, Fabric, Forge, and NeoForge** from a single codebase.
- A shared "middle" library so every platform exports **identical metrics**.
- **Independent per-platform versioning** — e.g. ship a new Paper build without
  re-releasing the mods.

## Core principles

1. **One platform-agnostic core.** All metric definitions, the Prometheus HTTP
   server, the config schema, and the legacy dimension-id scheme live in a pure
   Java `core` module with **zero** Minecraft or Bukkit dependencies. This
   module is shared by *both* the Paper plugin and the mods.

2. **All Minecraft access goes through one seam.** `core` never imports
   `net.minecraft.*` or `org.bukkit.*`. Instead it defines a
   `ServerStatsProvider` interface, and each platform provides an
   implementation. This is what allows the same metrics code to serve a Bukkit
   plugin and a vanilla-internals mod.

3. **Mods share source via Architectury Loom (build-time only).** We use
   Architectury **Loom tooling** to compile one `mod/common` source set against
   Fabric, Forge, and NeoForge. We do **not** depend on the runtime Architectury
   API, so end users install nothing extra. All three loaders are normalized to
   **Mojang (official) mappings** — this is the key decision that lets Fabric
   (normally Yarn-mapped) share `net.minecraft` code with Forge/NeoForge.

4. **Each platform is independently versioned and released.** Version numbers
   live per-module; releases are driven by per-platform git tags.

## Module layout

```
core/                       pure Java — NO Minecraft/Bukkit
  MinecraftCollector        platform-agnostic; reads via ServerStatsProvider
  ServerStatsProvider       the seam (interface)
  ExporterConfig            plain POJO config schema
  HttpExporterServer        wraps io.prometheus HTTPServer + registry lifecycle
  TickErrorPolicy           enum IGNORE/LOG/STRICT
  DimensionIds              canonical legacy-int dimension-id helper
  dto/                      PlayerInfo, DimensionStats, EntityTypeCount (records)

paper/                      Bukkit plugin (java + com.gradleup.shadow)
  PrometheusExporterPlugin  JavaPlugin bootstrap + tick scheduler
  BukkitStatsProvider       implements ServerStatsProvider via Bukkit API
  PaperServerConfig         config.yml -> ExporterConfig
  resources/                plugin.yml, config.yml

mod/common/                 architectury-loom + java; Mojang-mapped
  ExporterCommon            shared mod bootstrap
  VanillaStatsProvider      implements ServerStatsProvider on net.minecraft
  TickHooks                 server/dimension tick plumbing
mod/fabric/                 loom; fabric.mod.json + Fabric entrypoint
mod/forge/                  loom; mods.toml + @Mod class
mod/neoforge/               loom; neoforge.mods.toml + @Mod class
```

`settings.gradle` includes `core`, `paper`, `mod:common`, `mod:fabric`,
`mod:forge`, `mod:neoforge`. Every module depends on `core` via
`implementation project(':core')`; the loader modules also depend on
`project(':mod:common')`.

## The `ServerStatsProvider` seam

`core.MinecraftCollector` reads everything it needs through this interface.
Snapshot/pull data goes on the interface; tick timing is event-driven, so it
stays as collector entry points the platform drives via hooks.

```java
List<PlayerInfo>     getOnlinePlayers();                        // mc_player_list
List<DimensionStats> getDimensionStats(boolean withEntities);   // mc_dimension_chunks_loaded, mc_entities_total
boolean              supportsDimensionTickEvents();             // Forge/NeoForge/Fabric=true, Paper=false
double               getApproximateTickSeconds();               // Paper fallback derived from getTPS()
```

DTOs are `record`s in `core/dto`. `DimensionStats` is
`(int id, String name, int loadedChunks, List<EntityTypeCount> entities)`.

Tick timing entry points on the collector:
`start/stopServerTick()` and `start/stopDimensionTick(int id, String name)`.
The platform resolves its native dimension key to `(id, name)` before calling.
`TickErrorPolicy` lives in `core`.

Platform implementations:

- **`BukkitStatsProvider`** (Paper): `server.getOnlinePlayers()`,
  `world.getLoadedChunks().length`, `world.getEntities()` with
  `instanceof Item`/`Player`. Reports `supportsDimensionTickEvents() = false`
  and approximates per-dimension tick time from `server.getTPS()`.
- **`VanillaStatsProvider`** (mod/common): `getPlayerList().getPlayers()`,
  `world.getChunkSource().getLoadedChunksCount()`, `world.getAllEntities()` with
  `instanceof ItemEntity`/`Player`. Reports `supportsDimensionTickEvents() = true`.

The legacy integer dimension-id mapping (overworld `0`, end `1`, nether `-1`,
otherwise `name.hashCode()`) is **byte-for-byte preserved** in
`core.DimensionIds` so existing dashboards keep working.

## Dependency bundling & relocation

The Prometheus client is bundled into every final jar and relocated:

```
io.prometheus  ->  com.drewburr.mcprom.vendors.io.prometheus
```

Relocation happens in **all** artifacts so we never collide with another
mod/plugin that also bundles Prometheus. The relocation string and shared
dependency versions are defined once in the root `build.gradle`.

- **Paper jar**: `com.gradleup.shadow` bundles the Prometheus client +
  `project(':core')` output, relocates, and `minimize()`s.
- **Loader jars**: Shadow runs alongside Loom. `shadowJar` bundles Prometheus +
  `core` + `mod:common` output and relocates; then
  `remapJar.inputFile = shadowJar.archiveFile` (with `remapJar.dependsOn
  shadowJar`) so Loom remaps the shadowed jar into the final artifact.

> ⚠️ `minimize()` can strip reflectively-loaded
> `simpleclient_httpserver`/`simpleclient_hotspot` classes. If a runtime
> `ClassNotFoundException` shows up, add explicit `keep` filters.

## Versioning & releases

- Each module owns its version in its **own `gradle.properties`**:
  `paper_version`, `fabric_version`, `forge_version`, `neoforge_version`, plus an
  internal `core_version`. The root `gradle.properties` holds only shared config
  (Java toolchain, `prometheus_version`, group/package).
- Bumping `core` does **not** force a public release — only the platform module
  that ships changed code gets re-released.
- **The version file is the source of truth.** Releases are version-driven, not
  tag-driven: on a push to `master`, `.github/workflows/release.yml` reads each
  buildable platform's version from its `gradle.properties`, and if no release
  exists yet for that version it builds the module and publishes a release
  (which also creates the namespaced git tag, e.g. `paper/v1.3.0`). Already-
  released versions are skipped, so unrelated pushes are no-ops.
- To ship a platform: bump its `*_version` and merge. To ship several, bump
  several. No manual tagging.
- The release matrix currently covers the buildable platforms (`paper`,
  `neoforge`, `fabric`); add `forge` to it once its module is re-enabled.

## Building & verifying

| Goal                | Command                          |
| ------------------- | -------------------------------- |
| Core (fast, no MC)  | `./gradlew :core:test`           |
| Paper plugin jar    | `./gradlew :paper:build`         |
| Fabric jar          | `./gradlew :mod:fabric:build`    |
| Forge jar           | `./gradlew :mod:forge:build`     |
| NeoForge jar        | `./gradlew :mod:neoforge:build`  |
| Everything          | `./gradlew build`                |

Verification checklist per jar:

1. Unzip and confirm `com/drewburr/mcprom/vendors/io/prometheus/...` is present
   and `io/prometheus/...` is **absent**.
2. Confirm `core` classes are embedded and no Architectury runtime classes
   leaked in.
3. Runtime smoke test: launch a server with the jar, `curl
   http://127.0.0.1:19565/metrics`, and confirm all five `mc_*` metric families
   render. (This catches any over-aggressive `minimize()`.)

## Namespace

Group and base package were rebranded from
`com.github.cpburnz.minecraft_prometheus_exporter` to `com.drewburr.mcprom` as
part of taking over maintenance. Metric names (`mc_*`) are intentionally
unchanged for dashboard compatibility.

## Mod loader status (MC 26.x tooling)

Paper, NeoForge, and Fabric build and ship today. Only Forge is disabled in
`settings.gradle`, pending upstream tooling for the new 26.x version scheme:

- **Gson/Java 25 (solved):** Loom crashed on Java 25 because the old
  `foojay-resolver-convention 0.7.0` plugin dragged Gson 2.9.1 onto the plugin
  classpath, and Gson 2.9.1 cannot write `final` fields on JDK 18+ (the legacy
  reflection accessors were removed in JDK 23). Upgrading foojay to `1.0.0`
  resolves Gson to ≥2.13 and Loom runs fine on Java 25.
- **Fabric (working):** Minecraft 26.x ships **unobfuscated**, so Fabric stopped
  maintaining intermediary/yarn from 26.1 onward and there is **nothing to map**.
  The build must therefore: use the **`net.fabricmc.fabric-loom` `1.17-SNAPSHOT`**
  plugin (the release `1.17.12` still requires a mappings dependency), declare
  **no `mappings` line**, use plain `implementation` (not `modImplementation`,
  since there is no remapping), and treat the **shaded jar as final** (there is
  no `remapJar` task). `loom.officialMojangMappings()` is wrong here — it tries
  to download proguard mappings that no longer exist.
- **NeoForge (working):** builds via NeoForge's **ModDevGradle**
  (`net.neoforged.moddev`), which has its own mapping pipeline and doesn't
  depend on Fabric intermediary or piston-meta mappings.
- **Forge (blocked upstream):** Forge 26.x dropped SRG and runs Mojang-mapped
  like NeoForge, but ModDevGradle's legacy-forge variant
  (`net.neoforged.moddev.legacyforge` 2.0.141) still expects an SRG
  `intermediaryToNamedMapping` result and fails on 26.x. The module is
  scaffolded and re-enables once MDG supports SRG-less Forge.
- **JDK requirement:** NeoForm recompiles Minecraft, so a full **JDK 25 with
  `javac`** must be available to the toolchain (a JRE is not enough).

### 26.2 API notes
Minecraft 26.x ships **deobfuscated** (Mojang publishes no obfuscation mappings;
the jar already has real names). Notable renames vs the 1.21.x era, handled in
`mod/common`: `ResourceLocation` → `Identifier`, `ResourceKey.location()` →
`identifier()`, and `GameProfile` is now a record (`id()`/`name()`).

## Conventions

- Target **one Minecraft version per platform** for now (currently 1.21.1).
  Adding more versions later should extend the build, not reintroduce
  per-version branches.
- Keep all genuinely shared logic in `core`. If you find yourself copy-pasting
  metric or config code into a platform module, it belongs in `core` behind the
  `ServerStatsProvider` seam instead.
