package com.github.cpburnz.minecraft_prometheus_exporter;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The ServerConfig class defines the server-side plugin config. This is used to
 * load and generate the config.yml config file.
 */
public class ServerConfig {

	/**
	 * The plugin instance.
	 */
	private final JavaPlugin plugin;

	/**
	 * Whether collecting metrics about the JVM process is enabled.
	 */
	public boolean collector_jvm;

	/**
	 * Whether collecting metrics about the Minecraft server is enabled.
	 */
	public boolean collector_mc;

	/**
	 * Whether collecting metrics about the entities in each dimension (world) is
	 * enabled.
	 */
	public boolean collector_mc_entities;

	/**
	 * The IP address to listen on.
	 */
	public String web_listen_address;

	/**
	 * The TCP port to listen on.
	 */
	public int web_listen_port;

	/**
	 * The default address to listen on. This defaults to listening everywhere
	 * because it is the most useful default.
	 */
	private static final String DEFAULT_ADDRESS = "0.0.0.0";

	/**
	 * The default TCP port to use. This is completely arbitrary. It was derived
	 * from the Minecraft port (25565) and the Prometheus exporter ports
	 * (9100+).
	 */
	private static final int DEFAULT_PORT = 19565;

	/**
	 * Construct the instance.
	 * 
	 * @param plugin The plugin instance.
	 */
	public ServerConfig(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	/**
	 * Load the configuration values.
	 */
	public void loadConfig() {
		// Save default config if it doesn't exist
		plugin.saveDefaultConfig();
		
		FileConfiguration config = plugin.getConfig();

		// Set defaults
		config.addDefault("collector.jvm", true);
		config.addDefault("collector.mc", true);
		config.addDefault("collector.mc_entities", true);
		config.addDefault("web.listen_address", DEFAULT_ADDRESS);
		config.addDefault("web.listen_port", DEFAULT_PORT);

		// Save defaults to file
		config.options().copyDefaults(true);
		plugin.saveConfig();

		// Load config values
		this.collector_jvm = config.getBoolean("collector.jvm", true);
		this.collector_mc = config.getBoolean("collector.mc", true);
		this.collector_mc_entities = config.getBoolean("collector.mc_entities", true);
		this.web_listen_address = config.getString("web.listen_address", DEFAULT_ADDRESS);
		this.web_listen_port = config.getInt("web.listen_port", DEFAULT_PORT);

		// Log configuration
		plugin.getLogger().info("collector.jvm: " + this.collector_jvm);
		plugin.getLogger().info("collector.mc: " + this.collector_mc);
		plugin.getLogger().info("collector.mc_entities: " + this.collector_mc_entities);
		plugin.getLogger().info("web.listen_address: " + this.web_listen_address);
		plugin.getLogger().info("web.listen_port: " + this.web_listen_port);
	}
}
