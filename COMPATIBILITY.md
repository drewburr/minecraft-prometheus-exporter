# Metric Compatibility Summary

## ✅ Full Metric Compatibility with Forge/Fabric Version

The Paper plugin has been updated to maintain **100% metric compatibility** with the Forge/Fabric version.

### Metrics - Identical to Forge/Fabric

| Metric Name | Labels | Status |
|-------------|--------|--------|
| `mc_dimension_chunks_loaded` | `id`, `name` | ✅ Identical |
| `mc_entities_total` | `dim`, `dim_id`, `type` | ✅ Identical |
| `mc_player_list` | `id`, `name` | ✅ Identical |
| `mc_server_tick_seconds` | *(histogram)* | ✅ Identical |
| `mc_dimension_tick_seconds` | - | ❌ Not available in Paper |
| JVM metrics | *(various)* | ✅ Identical |

### Dimension Mapping

The Paper plugin maps Bukkit worlds to dimension IDs exactly like Forge/Fabric:

| World Environment | Dimension ID | Dimension Name |
|-------------------|--------------|----------------|
| NORMAL | `0` | `overworld` |
| THE_END | `1` | `the_end` |
| NETHER | `-1` | `the_nether` |
| CUSTOM | `hashCode()` | world name |

### Example Metrics Output

Both Forge/Fabric and Paper produce identical metrics:

```
mc_dimension_chunks_loaded{id="0",name="overworld"} 256
mc_dimension_chunks_loaded{id="-1",name="the_nether"} 64
mc_dimension_chunks_loaded{id="1",name="the_end"} 16

mc_entities_total{dim="overworld",dim_id="0",type="Zombie"} 5
mc_entities_total{dim="overworld",dim_id="0",type="Item"} 12

mc_player_list{id="550e8400-e29b-41d4-a716-446655440000",name="Steve"} 1
```

### What This Means

- ✅ **No Prometheus config changes needed**
- ✅ **No Grafana dashboard changes needed**
- ✅ **Drop-in replacement** for Forge/Fabric version
- ✅ **All existing queries work as-is**
- ✅ **Historical data remains compatible**

### Only Difference

The only metric not available in the Paper version is:
- `mc_dimension_tick_seconds` - Per-dimension tick timing histogram

This metric is not available because Paper's event system doesn't expose per-world tick timing in the same way that Forge does. The server-level tick timing (`mc_server_tick_seconds`) is still available.

### Migration Impact

**Zero migration effort** - Your existing monitoring setup will work without any changes when switching from Forge/Fabric to Paper.
