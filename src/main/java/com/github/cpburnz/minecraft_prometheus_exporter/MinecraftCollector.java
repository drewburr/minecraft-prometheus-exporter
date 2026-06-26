package com.github.cpburnz.minecraft_prometheus_exporter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;

import io.prometheus.client.Collector;
import io.prometheus.client.GaugeMetricFamily;
import io.prometheus.client.Histogram;

/**
 * The MinecraftCollector class collects stats from the Minecraft server for
 * export.
 */
public class MinecraftCollector extends Collector implements Collector.Describable {

	/**
	 * The histogram buckets to use for ticks.
	 */
	private static final double[] TICK_BUCKETS = new double[] {
		0.01,
		0.025,
		0.05,
		0.10,
		0.25,
		0.5,
		1.0,
	};

	/**
	 * The server configuration.
	 */
	private final ServerConfig config;

	/**
	 * The Bukkit server.
	 */
	private final Server server;

	/**
	 * Histogram metrics for dimension tick timing.
	 */
	private final Histogram dim_tick_seconds;

	/**
	 * Histogram metrics for server tick timing.
	 */
	private final Histogram server_tick_seconds;

	/**
	 * The active timer when timing a server tick.
	 */
	private Histogram.Timer server_tick_timer;

	/**
	 * Cached metrics that must be collected on the main thread.
	 * Volatile to ensure visibility across threads.
	 */
	private volatile MetricFamilySamples cached_player_list;
	private volatile MetricFamilySamples cached_dim_chunks_loaded;
	private volatile MetricFamilySamples cached_entities;

	/**
	 * Constructs the instance.
	 *
	 * @param config The plugin configuration.
	 * @param server The Bukkit server.
	 */
	public MinecraftCollector(ServerConfig config, Server server) {
		this.config = config;
		this.server = server;

		// Setup server metrics.
		this.server_tick_seconds = Histogram.build()
			.buckets(TICK_BUCKETS)
			.name("mc_server_tick_seconds")
			.help("Stats on server tick times.")
			.create();

		this.dim_tick_seconds = Histogram.build()
			.buckets(TICK_BUCKETS)
			.name("mc_dimension_tick_seconds")
			.labelNames("id", "name")
			.help("Stats on dimension tick times.")
			.create();

		// Initialize cache with empty metrics
		this.cached_player_list = newPlayerListMetric();
		this.cached_dim_chunks_loaded = newDimensionChunksLoadedMetric();
		if (config.collector_mc_entities) {
			this.cached_entities = newEntitiesTotalMetric();
		}
	}

	/**
	 * Update cached metrics. This must be called from the main server thread.
	 * Called periodically by the plugin to update the cache.
	 */
	public void updateCache() {
		try {
			// Collect metrics that require main thread access
			this.cached_player_list = this.collectPlayerList();
			this.cached_dim_chunks_loaded = this.collectDimensionChunksLoaded();

			if (this.config.collector_mc_entities) {
				this.cached_entities = this.collectEntitiesTotal();
			} else {
				this.cached_entities = null;
			}
		} catch (Exception e) {
			Bukkit.getLogger().severe("Failed to update cached metrics: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Return all metrics for the collector.
	 *
	 * <p>This method is called from the HTTP server thread, so it uses cached
	 * values for metrics that require main thread access.</p>
	 *
	 * @return The collector metrics.
	 */
	@Override
	public List<MetricFamilySamples> collect() {
		try {
			// Use cached metrics (collected on main thread)
			MetricFamilySamples player_list = this.cached_player_list;
			MetricFamilySamples dim_chunks_loaded = this.cached_dim_chunks_loaded;
			MetricFamilySamples entities = this.cached_entities;

			// These can be collected directly as they don't access world data
			List<MetricFamilySamples> server_ticks = this.server_tick_seconds.collect();
			List<MetricFamilySamples> dim_ticks = this.collectDimensionTicks();

			int entities_init = (entities != null) ? 1 : 0;
			int player_list_init = (player_list != null) ? 1 : 0;
			int dim_chunks_init = (dim_chunks_loaded != null) ? 1 : 0;

			// Aggregate metrics.
			ArrayList<MetricFamilySamples> metrics = new ArrayList<>(
				player_list_init
				+ entities_init
				+ server_ticks.size()
				+ dim_chunks_init
				+ dim_ticks.size()
			);

			if (player_list != null) {
				metrics.add(player_list);
			}
			if (entities != null) {
				metrics.add(entities);
			}
			metrics.addAll(server_ticks);
			if (dim_chunks_loaded != null) {
				metrics.add(dim_chunks_loaded);
			}
			metrics.addAll(dim_ticks);

			return metrics;
		} catch (Exception e) {
			Bukkit.getLogger().severe("Failed to collect metrics: " + e.getMessage());
			e.printStackTrace();
			return Collections.emptyList();
		}
	}

	/**
	 * Get the number of loaded dimension chunks.
	 *
	 * @return The dimension chunks loaded metric.
	 */
	private GaugeMetricFamily collectDimensionChunksLoaded() {
		GaugeMetricFamily metric = newDimensionChunksLoadedMetric();
		for (World world : this.server.getWorlds()) {
			int dim_id = getDimensionId(world);
			String id_str = Integer.toString(dim_id);
			String name = getDimensionName(world);
			int loaded = world.getLoadedChunks().length;
			metric.addMetric(List.of(id_str, name), loaded);
		}
		return metric;
	}

	/**
	 * Collect dimension tick timing data using TPS data.
	 *
	 * <p>Paper/Bukkit doesn't expose per-world tick times directly like Forge does.
	 * Instead, we calculate approximate tick times from TPS data. If TPS is below 20,
	 * the tick time exceeded 50ms. This provides a reasonable approximation of world
	 * tick performance.</p>
	 *
	 * @return The dimension tick timing metrics.
	 */
	private List<MetricFamilySamples> collectDimensionTicks() {
		// Get server TPS (ticks per second) for recent periods
		double[] tps = this.server.getTPS();

		// Calculate average tick time from TPS
		// TPS of 20 = 50ms per tick = 0.05 seconds
		// TPS of 10 = 100ms per tick = 0.1 seconds
		// Formula: tick_time = 1.0 / (TPS / 20)
		double recentTPS = tps[0]; // 1-minute average
		double tickTimeSeconds = Math.min(1.0, 1.0 / (recentTPS / 20.0));

		// Since we don't have per-world timing, we apply the same tick time
		// to all worlds as an approximation
		for (World world : this.server.getWorlds()) {
			int dim_id = getDimensionId(world);
			String id_str = Integer.toString(dim_id);
			String name = getDimensionName(world);

			// Record the approximated tick time
			this.dim_tick_seconds.labels(id_str, name).observe(tickTimeSeconds);
		}

		return this.dim_tick_seconds.collect();
	}

	/**
	 * Get the entities per dimension.
	 *
	 * @return The entities total metric.
	 */
	private GaugeMetricFamily collectEntitiesTotal() {
		// Aggregate stats.
		HashMap<EntityKey, Integer> entity_totals = new HashMap<>();
		for (World world : this.server.getWorlds()) {
			// Get dimension info.
			int dim_id = getDimensionId(world);
			String dim = getDimensionName(world);

			// Get entity info.
			for (Entity entity : world.getEntities()) {
				if (!(entity instanceof Player)) {
					// Get entity type.
					String entity_type;
					if (entity instanceof Item) {
						// Merge items. Do not count items individually by type.
						entity_type = "Item";
					} else {
						entity_type = entity.getType().name();
					}

					EntityKey entity_key = new EntityKey(dim, dim_id, entity_type);
					entity_totals.merge(entity_key, 1, Integer::sum);
				}
			}
		}

		// Record metrics.
		GaugeMetricFamily metric = newEntitiesTotalMetric();
		for (var entry : entity_totals.entrySet()) {
			EntityKey entity_key = entry.getKey();
			double total = entry.getValue();
			String dim_id_str = Integer.toString(entity_key.dim_id);
			metric.addMetric(
				List.of(entity_key.dim, dim_id_str, entity_key.type), total
			);
		}
		return metric;
	}

	/**
	 * Get the active players.
	 *
	 * @return The player list metric.
	 */
	private GaugeMetricFamily collectPlayerList() {
		GaugeMetricFamily metric = newPlayerListMetric();
		for (Player player : this.server.getOnlinePlayers()) {
			UUID uuid = player.getUniqueId();
			String name = player.getName();
			metric.addMetric(List.of(uuid.toString(), name), 1);
		}
		return metric;
	}

	/**
	 * Return all metric descriptions for the collector.
	 *
	 * @return The collector metric descriptions.
	 */
	@Override
	public List<MetricFamilySamples> describe() {
		// Aggregate metric descriptions.
		ArrayList<MetricFamilySamples> descs = new ArrayList<>();
		descs.add(newPlayerListMetric());
		if (this.config.collector_mc_entities) {
			descs.add(newEntitiesTotalMetric());
		}
		descs.addAll(this.server_tick_seconds.describe());
		descs.add(newDimensionChunksLoadedMetric());
		descs.addAll(this.dim_tick_seconds.describe());
		return descs;
	}

	/**
	 * Get the dimension id.
	 *
	 * <p>This method maps Bukkit world environments to dimension IDs to maintain
	 * compatibility with the Forge/Fabric version. Vanilla dimensions use fixed
	 * id values (-1, 0, 1), and custom dimensions get an id calculated from the
	 * world name.</p>
	 *
	 * @param world The world.
	 * @return The dimension id.
	 */
	private static int getDimensionId(World world) {
		return switch (world.getEnvironment()) {
			case NORMAL -> 0;
			case THE_END -> 1;
			case NETHER -> -1;
			case CUSTOM -> world.getName().hashCode();
		};
	}

	/**
	 * Get the dimension name.
	 *
	 * <p>This method maps Bukkit worlds to dimension names to maintain
	 * compatibility with the Forge/Fabric version.</p>
	 *
	 * @param world The world.
	 * @return The dimension name.
	 */
	private static String getDimensionName(World world) {
		return switch (world.getEnvironment()) {
			case NORMAL -> world.getName().equals("world") ? "overworld" : world.getName();
			case THE_END -> "the_end";
			case NETHER -> "the_nether";
			case CUSTOM -> world.getName();
		};
	}

	/**
	 * Create a new metric for the dimension chunks loaded.
	 *
	 * @return The dimension chunks loaded metric.
	 */
	private static GaugeMetricFamily newDimensionChunksLoadedMetric() {
		return new GaugeMetricFamily(
			"mc_dimension_chunks_loaded",
			"The number of loaded dimension chunks.",
			List.of("id", "name")
		);
	}

	/**
	 * Create a new metric for the total entities.
	 *
	 * @return The entities total metric.
	 */
	private static GaugeMetricFamily newEntitiesTotalMetric() {
		return new GaugeMetricFamily(
			"mc_entities_total",
			"The number of entities in each dimension by type.",
			List.of("dim", "dim_id", "type")
		);
	}

	/**
	 * Create a new metric for the player list.
	 *
	 * @return The player list metric.
	 */
	private static GaugeMetricFamily newPlayerListMetric() {
		return new GaugeMetricFamily(
			"mc_player_list",
			"The players connected to the server.",
			List.of("id", "name")
		);
	}

	/**
	 * Record when a server tick begins.
	 */
	public void startServerTick() {
		if (this.server_tick_timer != null) {
			// Skip if previous tick hasn't finished (shouldn't happen normally)
			return;
		}

		this.server_tick_timer = this.server_tick_seconds.startTimer();
	}

	/**
	 * Record when a server tick finishes.
	 */
	public void stopServerTick() {
		if (this.server_tick_timer == null) {
			// No timer to stop
			return;
		}

		this.server_tick_timer.observeDuration();
		this.server_tick_timer = null;
	}

	/**
	 * The EntityKey class is used to count entities per dimension.
	 *
	 * @param dim The dimension name.
	 * @param dim_id The dimension id.
	 * @param type The entity type.
	 */
	private record EntityKey(String dim, int dim_id, String type) {
		// Empty.
	}
}
