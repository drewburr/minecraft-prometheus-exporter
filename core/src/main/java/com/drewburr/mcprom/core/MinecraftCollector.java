package com.drewburr.mcprom.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import com.drewburr.mcprom.core.dto.DimensionStats;
import com.drewburr.mcprom.core.dto.EntityTypeCount;
import com.drewburr.mcprom.core.dto.PlayerInfo;

import io.prometheus.client.Collector;
import io.prometheus.client.GaugeMetricFamily;
import io.prometheus.client.Histogram;

/**
 * Collects stats from the Minecraft server for export. Platform-agnostic: all
 * server access is funneled through a {@link ServerStatsProvider}, so the same
 * collector serves the Paper plugin and the Fabric/Forge/NeoForge mods.
 */
public class MinecraftCollector extends Collector implements Collector.Describable {

	private static final Logger LOG = Logger.getLogger(MinecraftCollector.class.getName());

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
	 * The exporter configuration.
	 */
	private final ExporterConfig config;

	/**
	 * The platform stats provider.
	 */
	private final ServerStatsProvider stats;

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
	 * Active per-dimension tick timers, keyed by legacy dimension id. Tracked
	 * separately to support loaders that tick dimensions off the main thread.
	 */
	private final ConcurrentHashMap<Integer, Histogram.Timer> dim_tick_timers = new ConcurrentHashMap<>();

	/**
	 * Cached metrics that must be collected on the main thread. Volatile to
	 * ensure visibility across threads.
	 */
	private volatile MetricFamilySamples cached_player_list;
	private volatile MetricFamilySamples cached_dim_chunks_loaded;
	private volatile MetricFamilySamples cached_entities;

	/**
	 * The last dimension snapshot, used to approximate per-dimension tick timing
	 * on platforms that lack real tick events.
	 */
	private volatile List<DimensionStats> cached_dimensions = Collections.emptyList();

	/**
	 * Constructs the instance.
	 *
	 * @param config The exporter configuration.
	 * @param stats The platform stats provider.
	 */
	public MinecraftCollector(ExporterConfig config, ServerStatsProvider stats) {
		this.config = config;
		this.stats = stats;

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

		// Initialize cache with empty metrics.
		this.cached_player_list = newPlayerListMetric();
		this.cached_dim_chunks_loaded = newDimensionChunksLoadedMetric();
		if (config.collector_mc_entities) {
			this.cached_entities = newEntitiesTotalMetric();
		}
	}

	/**
	 * Update cached metrics. Must be called from the main server thread.
	 */
	public void updateCache() {
		try {
			List<DimensionStats> dimensions = this.stats.getDimensionStats(this.config.collector_mc_entities);
			this.cached_dimensions = dimensions;
			this.cached_player_list = this.collectPlayerList();
			this.cached_dim_chunks_loaded = this.collectDimensionChunksLoaded(dimensions);
			this.cached_entities = this.config.collector_mc_entities
				? this.collectEntitiesTotal(dimensions)
				: null;
		} catch (Exception e) {
			LOG.severe("Failed to update cached metrics: " + e.getMessage());
		}
	}

	/**
	 * Return all metrics for the collector. Called from the HTTP server thread,
	 * so it uses cached values for metrics that require main thread access.
	 *
	 * @return The collector metrics.
	 */
	@Override
	public List<MetricFamilySamples> collect() {
		try {
			MetricFamilySamples player_list = this.cached_player_list;
			MetricFamilySamples dim_chunks_loaded = this.cached_dim_chunks_loaded;
			MetricFamilySamples entities = this.cached_entities;

			List<MetricFamilySamples> server_ticks = this.server_tick_seconds.collect();
			List<MetricFamilySamples> dim_ticks = this.collectDimensionTicks();

			ArrayList<MetricFamilySamples> metrics = new ArrayList<>();
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
			LOG.severe("Failed to collect metrics: " + e.getMessage());
			return Collections.emptyList();
		}
	}

	/**
	 * Build the loaded-chunks metric from a dimension snapshot.
	 */
	private GaugeMetricFamily collectDimensionChunksLoaded(List<DimensionStats> dimensions) {
		GaugeMetricFamily metric = newDimensionChunksLoadedMetric();
		for (DimensionStats dim : dimensions) {
			metric.addMetric(List.of(Integer.toString(dim.id()), dim.name()), dim.loadedChunks());
		}
		return metric;
	}

	/**
	 * Collect dimension tick timing.
	 *
	 * <p>On platforms with real per-dimension tick events, the histogram is
	 * populated by the {@code start/stopDimensionTick} hooks. Otherwise the
	 * provider's approximate tick time is applied to every known dimension.</p>
	 */
	private List<MetricFamilySamples> collectDimensionTicks() {
		if (!this.stats.supportsDimensionTickEvents()) {
			double tick_seconds = this.stats.getApproximateTickSeconds();
			for (DimensionStats dim : this.cached_dimensions) {
				this.dim_tick_seconds.labels(Integer.toString(dim.id()), dim.name()).observe(tick_seconds);
			}
		}
		return this.dim_tick_seconds.collect();
	}

	/**
	 * Build the entities-total metric from a dimension snapshot.
	 */
	private GaugeMetricFamily collectEntitiesTotal(List<DimensionStats> dimensions) {
		GaugeMetricFamily metric = newEntitiesTotalMetric();
		for (DimensionStats dim : dimensions) {
			String dim_id_str = Integer.toString(dim.id());
			for (EntityTypeCount entity : dim.entities()) {
				metric.addMetric(List.of(dim.name(), dim_id_str, entity.type()), entity.count());
			}
		}
		return metric;
	}

	/**
	 * Build the player-list metric from the provider.
	 */
	private GaugeMetricFamily collectPlayerList() {
		GaugeMetricFamily metric = newPlayerListMetric();
		for (PlayerInfo player : this.stats.getOnlinePlayers()) {
			metric.addMetric(List.of(player.id(), player.name()), 1);
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

	private static GaugeMetricFamily newDimensionChunksLoadedMetric() {
		return new GaugeMetricFamily(
			"mc_dimension_chunks_loaded",
			"The number of loaded dimension chunks.",
			List.of("id", "name")
		);
	}

	private static GaugeMetricFamily newEntitiesTotalMetric() {
		return new GaugeMetricFamily(
			"mc_entities_total",
			"The number of entities in each dimension by type.",
			List.of("dim", "dim_id", "type")
		);
	}

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
			// Previous tick hasn't finished; skip.
			return;
		}
		this.server_tick_timer = this.server_tick_seconds.startTimer();
	}

	/**
	 * Record when a server tick finishes.
	 */
	public void stopServerTick() {
		if (this.server_tick_timer == null) {
			return;
		}
		this.server_tick_timer.observeDuration();
		this.server_tick_timer = null;
	}

	/**
	 * Record when a dimension tick begins. No-op on platforms that approximate
	 * tick timing.
	 *
	 * @param id The legacy dimension id.
	 * @param name The dimension name.
	 */
	public void startDimensionTick(int id, String name) {
		Histogram.Timer existing = this.dim_tick_timers.put(
			id, this.dim_tick_seconds.labels(Integer.toString(id), name).startTimer()
		);
		if (existing != null) {
			this.handleTickError("dimension " + id + " (" + name + ") started ticking before the previous tick finished");
		}
	}

	/**
	 * Record when a dimension tick finishes.
	 *
	 * @param id The legacy dimension id.
	 * @param name The dimension name.
	 */
	public void stopDimensionTick(int id, String name) {
		Histogram.Timer timer = this.dim_tick_timers.remove(id);
		if (timer != null) {
			timer.observeDuration();
		} else {
			this.handleTickError("dimension " + id + " (" + name + ") finished ticking without a recorded start");
		}
	}

	/**
	 * Apply the configured policy to a dimension tick mismatch.
	 */
	private void handleTickError(String message) {
		switch (this.config.collector_mc_dimension_tick_errors) {
			case IGNORE -> {
				// Do nothing.
			}
			case LOG -> LOG.warning(message);
			case STRICT -> throw new IllegalStateException(message);
		}
	}
}
