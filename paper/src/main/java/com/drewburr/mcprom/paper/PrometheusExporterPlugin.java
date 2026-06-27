package com.drewburr.mcprom.paper;

import java.io.IOException;

import com.drewburr.mcprom.core.ExporterConfig;
import com.drewburr.mcprom.core.HttpExporterServer;
import com.drewburr.mcprom.core.MinecraftCollector;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.hotspot.DefaultExports;

/**
 * The PaperMC plugin entry point. Wires the platform-agnostic core collector to
 * a {@link BukkitStatsProvider} and serves metrics over HTTP.
 */
public class PrometheusExporterPlugin extends JavaPlugin {

	private HttpExporterServer httpServer;
	private MinecraftCollector collector;
	private ExporterConfig config;

	@Override
	public void onEnable() {
		this.config = PaperServerConfig.load(this);

		try {
			this.httpServer = new HttpExporterServer(this.config.web_listen_address, this.config.web_listen_port);
			getLogger().info("Listening on " + this.config.web_listen_address + ":" + this.config.web_listen_port);
		} catch (IOException e) {
			getLogger().severe("Failed to start HTTP server: " + e.getMessage());
			getServer().getPluginManager().disablePlugin(this);
			return;
		}

		initCollectors();
		scheduleTasks();

		getLogger().info("Prometheus Exporter plugin enabled");
	}

	@Override
	public void onDisable() {
		CollectorRegistry.defaultRegistry.clear();
		if (this.httpServer != null) {
			this.httpServer.close();
			this.httpServer = null;
		}
		getLogger().info("Prometheus Exporter plugin disabled");
	}

	private void initCollectors() {
		if (this.config.collector_jvm) {
			DefaultExports.initialize();
		}
		if (this.config.collector_mc) {
			this.collector = new MinecraftCollector(this.config, new BukkitStatsProvider(getServer()));
			this.collector.register();
		}
	}

	private void scheduleTasks() {
		if (this.collector == null) {
			return;
		}

		// Measure server tick time as the interval between consecutive ticks.
		// Paper exposes no tick start/end event, so we time how long each tick
		// actually takes by the gap between this once-per-tick task's runs
		// (~50ms at 20 TPS, growing when the server lags).
		new BukkitRunnable() {
			private long lastNanos = 0L;

			@Override
			public void run() {
				long now = System.nanoTime();
				if (this.lastNanos != 0L) {
					collector.observeServerTick((now - this.lastNanos) / 1_000_000_000.0);
				}
				this.lastNanos = now;
			}
		}.runTaskTimer(this, 0L, 1L);

		// Refresh cached world/player/entity metrics once per second on the main
		// thread, so the HTTP endpoint can serve them without blocking.
		new BukkitRunnable() {
			@Override
			public void run() {
				collector.updateCache();
			}
		}.runTaskTimer(this, 0L, 20L);
	}
}
