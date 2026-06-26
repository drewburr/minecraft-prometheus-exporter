package com.drewburr.mcprom.paper;

import com.drewburr.mcprom.core.ExporterConfig;
import com.drewburr.mcprom.core.TickErrorPolicy;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Loads {@code config.yml} into a platform-agnostic {@link ExporterConfig}.
 */
public final class PaperServerConfig {

	private PaperServerConfig() {
	}

	/**
	 * Load (and create on first run) the plugin config.
	 *
	 * @param plugin The plugin instance.
	 * @return The populated config.
	 */
	public static ExporterConfig load(JavaPlugin plugin) {
		plugin.saveDefaultConfig();
		FileConfiguration file = plugin.getConfig();

		ExporterConfig config = new ExporterConfig();

		file.addDefault("collector.jvm", config.collector_jvm);
		file.addDefault("collector.mc", config.collector_mc);
		file.addDefault("collector.mc_entities", config.collector_mc_entities);
		file.addDefault("web.listen_address", config.web_listen_address);
		file.addDefault("web.listen_port", config.web_listen_port);
		file.options().copyDefaults(true);
		plugin.saveConfig();

		config.collector_jvm = file.getBoolean("collector.jvm", config.collector_jvm);
		config.collector_mc = file.getBoolean("collector.mc", config.collector_mc);
		config.collector_mc_entities = file.getBoolean("collector.mc_entities", config.collector_mc_entities);
		config.collector_mc_dimension_tick_errors = TickErrorPolicy.fromString(
			file.getString("collector.mc_dimension_tick_errors"));
		config.web_listen_address = file.getString("web.listen_address", config.web_listen_address);
		config.web_listen_port = file.getInt("web.listen_port", config.web_listen_port);

		plugin.getLogger().info("collector.jvm: " + config.collector_jvm);
		plugin.getLogger().info("collector.mc: " + config.collector_mc);
		plugin.getLogger().info("collector.mc_entities: " + config.collector_mc_entities);
		plugin.getLogger().info("web.listen_address: " + config.web_listen_address);
		plugin.getLogger().info("web.listen_port: " + config.web_listen_port);

		return config;
	}
}
