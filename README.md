<img src="Prometheus_Exporter_400px.png" alt="Prometheus Exporter" width="200">

# Minecraft Prometheus Exporter

A [Prometheus] exporter for Minecraft servers. It exports metrics about the
Minecraft server and the JVM for the [Prometheus] monitoring toolkit. It is
**server-side only** — nothing needs to be installed on clients.

This repository is a monorepo: one shared, platform-agnostic core with thin
per-platform adapters for **Paper**, **Fabric**, **Forge**, and **NeoForge**.
Each platform is built and released independently. See [DEVELOPMENT.md] for the
architecture.

> This project continues [cpburnz/minecraft-prometheus-exporter][upstream], now
> maintained by [@drewburr](https://github.com/drewburr).

## Platform support

Targeting **Minecraft 26.2** (Java 25).

| Platform | Type | Status |
| --- | --- | --- |
| Paper | Bukkit plugin | ✅ Available |
| NeoForge | Mod | ✅ Available |
| Fabric | Mod | ⏳ Pending upstream 26.x mappings |
| Forge | Mod | ⏳ Pending ModDevGradle support for SRG-less 26.x Forge |

Fabric and Forge are fully implemented but disabled in the build until their
toolchains catch up to Minecraft's new 26.x version scheme. See [DEVELOPMENT.md]
for details.

## Installation

Download the jar for your platform from the [releases] page.

- **Paper:** copy the `prometheus-exporter-paper-*.jar` into the server
  `plugins/` directory.
- **NeoForge:** copy the `prometheus-exporter-neoforge-*.jar` into the server
  `mods/` directory.

The exporter adds nothing to the world, so upgrading is just replacing the jar.

## Configuration

A config file is generated on first start if it does not already exist:

- **Paper:** `plugins/PrometheusExporter/config.yml`
- **Mods:** `config/prometheus_exporter.properties`

Options:

| Key | Default | Description |
| --- | --- | --- |
| `collector.jvm` | `true` | Export JVM process metrics. |
| `collector.mc` | `true` | Export Minecraft server metrics. |
| `collector.mc_entities` | `true` | Export per-dimension entity counts. |
| `collector.mc_dimension_tick_errors` | `log` | Mismatched dimension tick handling: `ignore` / `log` / `strict` (mods only). |
| `web.listen_address` | `0.0.0.0` | Address to bind the metrics HTTP server. |
| `web.listen_port` | `19565` | Port to serve metrics on. |

Metrics are then served at `http://<host>:19565/metrics`.

## Metrics & dashboards

- Metrics are documented in [metrics.md], with sample output in
  [examples/output.txt](examples/output.txt).
- Compatible Grafana dashboards are listed in [dashboards.md]. Metric names are
  unchanged from upstream, so existing dashboards keep working.

## Building from source

Requires a full **JDK 25** (with `javac`). Gradle 9.6 is provided via the
wrapper.

```sh
./gradlew :core:test          # platform-agnostic unit tests
./gradlew :paper:build        # paper/build/libs/prometheus-exporter-paper-*.jar
./gradlew :mod:neoforge:build # mod/neoforge/build/libs/prometheus-exporter-neoforge-*.jar
```

Each platform carries its own version (in its module's `gradle.properties`) and
is released by pushing a namespaced tag — e.g. `paper/v1.3.0` or
`neoforge/v1.3.0` — which triggers the [release workflow](.github/workflows/release.yml)
to build and publish only that platform.

## License

[MIT](LICENSE).

[Prometheus]: https://prometheus.io/
[DEVELOPMENT.md]: DEVELOPMENT.md
[metrics.md]: metrics.md
[dashboards.md]: dashboards.md
[releases]: https://github.com/drewburr/minecraft-prometheus-exporter/releases
[upstream]: https://github.com/cpburnz/minecraft-prometheus-exporter
