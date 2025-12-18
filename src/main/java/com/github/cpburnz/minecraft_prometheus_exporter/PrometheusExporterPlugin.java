package com.github.cpburnz.minecraft_prometheus_exporter;

import java.io.IOException;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.HTTPServer;
import io.prometheus.client.hotspot.DefaultExports;


/**
 * The PrometheusExporterPlugin class defines the Paper plugin.
 */
public class PrometheusExporterPlugin extends JavaPlugin implements Listener {

	/**
	 * The HTTP server.
	 */
	private HTTPServer http_server;

	/**
	 * The Minecraft metrics collector.
	 */
	private MinecraftCollector mc_collector;

	/**
	 * The server configuration.
	 */
	private ServerConfig config;

	/**
	 * Tick counter for server tick timing.
	 */
	private TickTimer serverTickTimer;

	/**
	 * Called when the plugin is enabled.
	 */
	@Override
	public void onEnable() {
		// Register event listener
		getServer().getPluginManager().registerEvents(this, this);

		// Load configuration
		this.config = new ServerConfig(this);
		this.config.loadConfig();

		// Initialize HTTP server
		try {
			this.initHttpServer();
		} catch (IOException e) {
			getLogger().severe("Failed to start HTTP server: " + e.getMessage());
			e.printStackTrace();
			getServer().getPluginManager().disablePlugin(this);
			return;
		}

		// Initialize collectors
		this.initCollectors();

		// Start tick timing task
		this.serverTickTimer = new TickTimer();
		new BukkitRunnable() {
			@Override
			public void run() {
				if (mc_collector != null) {
					mc_collector.startServerTick();
					// Schedule the end of tick measurement for next tick
					Bukkit.getScheduler().runTask(PrometheusExporterPlugin.this, () -> {
						if (mc_collector != null) {
							mc_collector.stopServerTick();
						}
					});
				}
			}
		}.runTaskTimer(this, 0L, 1L); // Run every tick

		// Update cached metrics every second (20 ticks)
		// This ensures the HTTP endpoint always has fresh data without blocking
		new BukkitRunnable() {
			@Override
			public void run() {
				if (mc_collector != null) {
					mc_collector.updateCache();
				}
			}
		}.runTaskTimer(this, 0L, 20L); // Run every second

		getLogger().info("Prometheus Exporter plugin enabled");
	}

	/**
	 * Called when the plugin is disabled.
	 */
	@Override
	public void onDisable() {
		// Unregister collectors
		this.closeCollectors();

		// Stop HTTP server
		this.closeHttpServer();

		getLogger().info("Prometheus Exporter plugin disabled");
	}

	/**
	 * Unregister the metrics collectors.
	 */
	private void closeCollectors() {
		// Unregister all collectors.
		CollectorRegistry.defaultRegistry.clear();
	}

	/**
	 * Stop the HTTP server.
	 */
	private void closeHttpServer() {
		if (this.http_server != null) {
			this.http_server.close();
			this.http_server = null;
		} else {
			getLogger().warning("Cannot close http_server=null.");
		}
	}

	/**
	 * Initialize the metrics collectors.
	 */
	private void initCollectors() {
		// Collect JVM stats.
		if (this.config.collector_jvm) {
			DefaultExports.initialize();
		}

		// Collect Minecraft stats.
		if (this.config.collector_mc) {
			this.mc_collector = new MinecraftCollector(this.config, getServer());
			this.mc_collector.register();
		}
	}

	/**
	 * Initialize the HTTP server.
	 */
	private void initHttpServer() throws IOException {
		// Make sure the HTTP server thread is daemonized
		String address = this.config.web_listen_address;
		int port = this.config.web_listen_port;
		this.http_server = new HTTPServer(address, port, true);
		getLogger().info("Listening on " + address + ":" + port);
	}

	/**
	 * Helper class for timing ticks.
	 */
	private static class TickTimer {
		private long startTime;

		public void start() {
			this.startTime = System.nanoTime();
		}

		public double getDurationSeconds() {
			return (System.nanoTime() - startTime) / 1_000_000_000.0;
		}
	}
}
