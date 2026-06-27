package com.drewburr.mcprom.core;

/**
 * The platform-agnostic exporter configuration schema. Each platform reads its
 * native config format (Bukkit YAML, Forge/NeoForge TOML, Fabric JSON) and
 * populates this plain object.
 */
public class ExporterConfig {

	/**
	 * The default address to listen on. Defaults to listening everywhere because
	 * it is the most useful default.
	 */
	public static final String DEFAULT_ADDRESS = "0.0.0.0";

	/**
	 * The default TCP port. Arbitrary; derived from the Minecraft port (25565)
	 * and the Prometheus exporter port range (9100+).
	 */
	public static final int DEFAULT_PORT = 19565;

	/** Whether to collect JVM process metrics. */
	public boolean collector_jvm = true;

	/** Whether to collect Minecraft server metrics. */
	public boolean collector_mc = true;

	/** Whether to collect per-dimension entity metrics. */
	public boolean collector_mc_entities = true;

	/** How to handle mismatched dimension tick events. */
	public TickErrorPolicy collector_mc_dimension_tick_errors = TickErrorPolicy.LOG;

	/** The IP address to listen on. */
	public String web_listen_address = DEFAULT_ADDRESS;

	/** The TCP port to listen on. */
	public int web_listen_port = DEFAULT_PORT;
}
