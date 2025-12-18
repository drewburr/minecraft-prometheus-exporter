# Quick Start Guide - Paper Plugin

## Prerequisites

- Paper server 1.21.1 (or compatible Spigot/Bukkit)
- Java 21
- Basic knowledge of server administration

## Installation Steps

1. **Download the plugin**
   ```bash
   # Get the latest JAR from build/libs/
   # File: Prometheus-Exporter-1.21.1-paper-1.2.1.jar
   ```

2. **Install on your server**
   ```bash
   # Copy to your server's plugins directory
   cp Prometheus-Exporter-1.21.1-paper-1.2.1.jar /path/to/server/plugins/
   ```

3. **Start your server**
   ```bash
   # The plugin will automatically create its configuration
   java -jar paper.jar
   ```

4. **Verify it's working**
   ```bash
   # Check the metrics endpoint
   curl http://localhost:19565/metrics
   ```

## Quick Configuration

Edit `plugins/PrometheusExporter/config.yml`:

```yaml
collector:
  jvm: true           # JVM metrics (memory, GC, threads)
  mc: true            # Minecraft server metrics
  mc_entities: true   # Entity counts per world

web:
  listen_address: "0.0.0.0"  # Listen on all interfaces
  listen_port: 19565          # Default port
```

After editing, reload the plugin:
```
/reload confirm
```

## Configure Prometheus

Add to your `prometheus.yml`:

```yaml
scrape_configs:
  - job_name: 'minecraft'
    static_configs:
      - targets: ['your-server-ip:19565']
```

Reload Prometheus:
```bash
curl -X POST http://localhost:9090/-/reload
```

## View Metrics

- **Metrics endpoint**: http://your-server:19565/metrics
- **Prometheus UI**: http://localhost:9090
- **Grafana**: Create dashboards using the exported metrics

## Example Queries

```promql
# Number of online players
mc_player_list

# Chunks loaded in the overworld
mc_world_chunks_loaded{world="world"}

# Server tick duration (95th percentile)
histogram_quantile(0.95, mc_server_tick_seconds_bucket)

# Total entities in all worlds
sum(mc_entities_total)
```

## Troubleshooting

### Plugin won't load
```bash
# Check server logs
tail -f logs/latest.log
```

Common causes:
- Not using Paper/Spigot/Bukkit (must not be Vanilla/Forge)
- Wrong Java version (need Java 21)
- Port already in use

### Can't access metrics
```bash
# Check if port is listening
netstat -tulpn | grep 19565

# Test locally
curl http://localhost:19565/metrics
```

If it works locally but not remotely:
- Check firewall: `sudo ufw allow 19565`
- Verify `listen_address` is `0.0.0.0` not `127.0.0.1`

### No player metrics
- Players must be online when you check
- Wait a few seconds after player joins
- Check that `collector.mc` is `true` in config

## Next Steps

1. Set up Prometheus to scrape your server
2. Create Grafana dashboards
3. Set up alerting for server issues
4. Monitor your server's performance!

## Getting Help

- Check the logs: `logs/latest.log`
- Review configuration: `plugins/PrometheusExporter/config.yml`
- Read full docs: `README-PAPER.md`
- Report issues on GitHub
