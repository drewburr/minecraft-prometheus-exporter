# Migration Guide: Forge/Fabric to Paper

This guide explains how to migrate from using the Forge/Fabric version of the Minecraft Prometheus Exporter to the Paper plugin version.

## Why Paper?

Paper is a high-performance Minecraft server platform that:
- Provides better performance than Vanilla/Forge
- Has a simpler plugin API (Bukkit/Spigot)
- Is widely used for multiplayer servers
- Has excellent compatibility with vanilla clients (no client-side mods needed)

## Installation Differences

### Forge/Fabric
- Install mod in `mods/` directory
- Client may need the mod installed (depending on setup)
- Uses Minecraft Forge/Fabric mod loader

### Paper Plugin
- Install plugin in `plugins/` directory
- **No client-side installation needed** - vanilla clients can connect
- Requires Paper/Spigot/Bukkit server

## Configuration Changes

### File Location
- **Forge/Fabric**: `world/serverconfig/prometheus_exporter-server.toml` or `config/prometheus_exporter-server.toml`
- **Paper**: `plugins/PrometheusExporter/config.yml`

### Configuration Format
The configuration has been converted from TOML to YAML format:

#### Forge/Fabric (TOML)
```toml
[collector]
jvm = true
mc = true
mc_dimension_tick_errors = "LOG"
mc_entities = true

[web]
listen_address = "0.0.0.0"
listen_port = 19565
```

#### Paper (YAML)
```yaml
collector:
  jvm: true
  mc: true
  mc_entities: true

web:
  listen_address: "0.0.0.0"
  listen_port: 19565
```

### Removed Configuration Options
- `collector.mc_dimension_tick_errors` - Not applicable to Paper (different event system)

## Metric Changes

Most metrics remain the same, but there are some naming and label changes:

### Changed Metrics

| Forge/Fabric Metric | Paper Metric | Changes |
|---------------------|--------------|---------|
| `mc_dimension_chunks_loaded` | `mc_dimension_chunks_loaded` | **Fully compatible** - same metric name and labels |
| `mc_entities_total` | `mc_entities_total` | **Fully compatible** - same metric name and labels |
| `mc_player_list` | `mc_player_list` | **Fully compatible** - same metric name and labels |
| `mc_dimension_tick_seconds` | `mc_dimension_tick_seconds` | **Available** - approximated from server TPS (not direct per-world timing) |

### Metric Compatibility

**The Paper plugin maintains full metric compatibility with Forge/Fabric:**
- Uses the same metric names
- Uses the same label names: `id`, `name`, `dim`, `dim_id`, `type`
- Dimension IDs are mapped: Overworld=0, The End=1, Nether=-1
- Dimension names are mapped: overworld, the_end, the_nether

**Example - Metrics are identical**:
- Forge: `mc_dimension_chunks_loaded{id="0",name="overworld"}`
- Paper: `mc_dimension_chunks_loaded{id="0",name="overworld"}` ✓

## Prometheus Configuration

**No changes needed!** Your existing Prometheus queries will work as-is:

```promql
# These queries work with both Forge/Fabric and Paper versions
mc_dimension_chunks_loaded{name="overworld"}
mc_entities_total{dim="overworld"}
mc_player_list
```

## Grafana Dashboard Updates

**No changes needed!** Your existing Grafana dashboards will work without modification because the Paper plugin uses the same metric names and labels as the Forge/Fabric version.

## API Compatibility

The Paper version uses the Bukkit/Spigot API instead of Minecraft Forge APIs:

| Forge/Fabric | Paper/Bukkit |
|--------------|--------------|
| `MinecraftServer` | `org.bukkit.Server` |
| `ServerLevel` | `org.bukkit.World` |
| `ServerPlayer` | `org.bukkit.entity.Player` |
| `ResourceKey<Level>` | World name (String) |
| Event Bus | Bukkit Events |

## Building

### Forge/Fabric
```bash
./gradlew build
# Uses ForgeGradle or Fabric Loom
```

### Paper
```bash
./gradlew build
# Uses standard Gradle with Paper API
```

## Migration Steps

1. **Stop your Minecraft server**

2. **Backup your data** (always!)

3. **Switch to Paper**
   - Download Paper from https://papermc.io/downloads
   - Replace your server JAR with Paper
   - Start server once to generate Paper configs

4. **Remove old mod**
   - Delete the old mod from `mods/` directory
   - Remove old config from `world/serverconfig/` or `config/`

5. **Install Paper plugin**
   - Download the Paper plugin version
   - Place in `plugins/` directory
   - Start server

6. **Configure the plugin**
   - Edit `plugins/PrometheusExporter/config.yml`
   - Set your desired listen address and port

7. **Update Prometheus configuration**
   - Update any queries that reference dimension metrics
   - Update Grafana dashboards as needed

8. **Test**
   - Check that metrics are being exported: `curl http://localhost:19565/metrics`
   - Verify players appear in metrics
   - Check that world chunks are being tracked

## Common Issues

### Plugin won't load
- Ensure you're using Paper/Spigot/Bukkit (not Vanilla or Forge)
- Check Java version is 21 or higher
- Check logs in `logs/latest.log`

### Metrics endpoint not accessible
- Check firewall settings
- Verify `listen_address` in config (use `0.0.0.0` for external access)
- Ensure port is not already in use

### Missing metrics
- Verify collector settings in `config.yml`
- Check that `collector.mc` is set to `true`

## Benefits of Paper Version

1. **No client mods required** - Vanilla clients can connect
2. **Better performance** - Paper is optimized for multiplayer
3. **Simpler deployment** - Just drop the plugin in
4. **Active ecosystem** - Large plugin community and support
5. **Easier updates** - Paper updates faster than Forge

## Getting Help

- Check the README-PAPER.md for documentation
- Review example configuration in `examples/config.yml`
- Check server logs for error messages
- Open an issue on GitHub with logs and config

## Rollback

If you need to roll back to Forge/Fabric:

1. Stop the server
2. Remove the Paper plugin from `plugins/`
3. Switch back to Forge/Fabric server JAR
4. Restore the old mod to `mods/`
5. Restore your old TOML configuration
6. Start the server
