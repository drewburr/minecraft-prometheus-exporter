package com.drewburr.mcprom.mod;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Logger;

import com.drewburr.mcprom.core.ExporterConfig;
import com.drewburr.mcprom.core.HttpExporterServer;
import com.drewburr.mcprom.core.MinecraftCollector;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.hotspot.DefaultExports;
import net.minecraft.server.MinecraftServer;

/**
 * Loader-agnostic mod bootstrap. Each loader entry point loads the config once,
 * then drives this on the server start/stop and tick events.
 */
public class ExporterCommon {

	private static final Logger LOG = Logger.getLogger(ExporterCommon.class.getName());

	/** Refresh cached metrics once per second (every 20 ticks). */
	private static final int CACHE_REFRESH_TICKS = 20;

	private final ExporterConfig config;
	private HttpExporterServer httpServer;
	private MinecraftCollector collector;
	private int tickCounter;

	/**
	 * @param configDir The loader's config directory.
	 */
	public ExporterCommon(Path configDir) {
		this.config = ModConfig.load(configDir);
	}

	/**
	 * The active collector, or null before the server has started / when MC
	 * collection is disabled. Loaders use this to deliver tick events.
	 */
	public MinecraftCollector collector() {
		return this.collector;
	}

	/**
	 * Start the exporter once the server is available.
	 */
	public void onServerStarted(MinecraftServer server) {
		try {
			this.httpServer = new HttpExporterServer(this.config.web_listen_address, this.config.web_listen_port);
			LOG.info("Listening on " + this.config.web_listen_address + ":" + this.config.web_listen_port);
		} catch (IOException e) {
			LOG.severe("Failed to start HTTP server: " + e.getMessage());
			return;
		}

		if (this.config.collector_jvm) {
			DefaultExports.initialize();
		}
		if (this.config.collector_mc) {
			this.collector = new MinecraftCollector(this.config, new VanillaStatsProvider(server));
			this.collector.register();
		}
	}

	/**
	 * Refresh cached metrics on the server thread. Loaders call this once per
	 * server tick (end phase).
	 */
	public void onServerTickEnd() {
		if (this.collector != null && ++this.tickCounter >= CACHE_REFRESH_TICKS) {
			this.tickCounter = 0;
			this.collector.updateCache();
		}
	}

	/**
	 * Stop the exporter when the server stops.
	 */
	public void onServerStopped() {
		CollectorRegistry.defaultRegistry.clear();
		this.collector = null;
		if (this.httpServer != null) {
			this.httpServer.close();
			this.httpServer = null;
		}
	}
}
