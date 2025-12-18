# Minecraft Prometheus Exporter - Paper Plugin

This plugin provides a Prometheus exporter for Minecraft Paper servers. It exports metrics
related to the Minecraft server and the JVM for consumption by the open-source systems
monitoring toolkit, [Prometheus](https://prometheus.io/).

**This is a Paper plugin version** - it works with Paper/Spigot/Bukkit servers, NOT Forge/Fabric.

## Features

- JVM metrics (memory, GC, threads, etc.)
- Player list with UUIDs
- World metrics (loaded chunks per world)
- Entity counts by type and world
- Server tick timing metrics

## Requirements

- Minecraft 1.21.1 or compatible
- Paper, Spigot, or Bukkit server
- Java 21

## Installation

1. Download the latest JAR file from the releases
2. Copy the JAR file to your server's `plugins/` directory
3. Restart your server
4. The plugin will generate a default configuration at `plugins/PrometheusExporter/config.yml`

## Configuration

The configuration file is located at `plugins/PrometheusExporter/config.yml`:

```yaml
# Collector settings
collector:
  # Enable collecting metrics about the JVM process
  jvm: true

  # Enable collecting metrics about the Minecraft server
  mc: true

  # Enable collecting metrics about the entities in each world
  mc_entities: true

# Web server settings
web:
  # The IP address to listen on
  # To only allow connections from the local machine, use "127.0.0.1"
  # To allow connections from remote machines, use "0.0.0.0"
  listen_address: "0.0.0.0"

  # The TCP port to listen on
  # Ports 1-1023 require root (not recommended)
  listen_port: 19565
```

## Metrics

The plugin exposes metrics on `http://<server-ip>:19565/metrics` (by default).

### Available Metrics

- `mc_player_list` - Players connected to the server (labels: id, name)
- `mc_dimension_chunks_loaded` - Number of loaded chunks per dimension (labels: id, name)
- `mc_entities_total` - Number of entities by type and dimension (labels: dim, dim_id, type)
- `mc_server_tick_seconds` - Server tick timing histogram
- `mc_dimension_tick_seconds` - Dimension tick timing histogram (labels: id, name) - *approximated from server TPS*
- JVM metrics (when enabled):
  - `jvm_memory_*` - JVM memory usage
  - `jvm_gc_*` - Garbage collection stats
  - `jvm_threads_*` - Thread counts
  - And more...

## Building from Source

```bash
./gradlew clean build
```

The built JAR will be in `build/libs/Prometheus-Exporter-1.21.1-paper-1.2.1.jar`

## Prometheus Configuration

Add this to your `prometheus.yml`:

```yaml
scrape_configs:
  - job_name: 'minecraft'
    static_configs:
      - targets: ['your-server-ip:19565']
```

## Differences from Forge/Fabric Version

This Paper plugin version has some differences from the original Forge/Fabric mod:

- **Metrics are fully compatible**: Uses the same metric names and labels as Forge/Fabric version
  - `mc_dimension_chunks_loaded` with labels `id` and `name`
  - `mc_entities_total` with labels `dim`, `dim_id`, and `type`
  - `mc_player_list` with labels `id` and `name`
  - `mc_dimension_tick_seconds` with labels `id` and `name` - *Note: approximated from server TPS rather than direct per-world timing*
- **Configuration**: Uses Bukkit-style YAML configuration instead of Forge TOML
- **Dimension tick timing**: Approximated from server TPS rather than direct per-world event timing (Paper doesn't expose per-world tick events)
- **Entity types**: Uses Bukkit entity type names (which may differ slightly from Forge)

## License

MIT License

## Credits

Original Forge/Fabric mod by [cpburnz](https://github.com/cpburnz/minecraft-prometheus-exporter).
Paper plugin conversion by drewburr.

## Links

- [Prometheus](https://prometheus.io/)
- [Paper](https://papermc.io/)
- [Grafana](https://grafana.com/)
