package com.drewburr.mcprom.mod;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.logging.Logger;

import com.drewburr.mcprom.core.ExporterConfig;
import com.drewburr.mcprom.core.TickErrorPolicy;

/**
 * Loads the exporter config from a plain {@code .properties} file, so the mod
 * stays loader-agnostic (no Forge/NeoForge config spec, no Fabric config lib).
 * Creates the file with defaults on first run.
 */
public final class ModConfig {

	private static final Logger LOG = Logger.getLogger(ModConfig.class.getName());

	private static final String FILE_NAME = "prometheus_exporter.properties";

	private ModConfig() {
	}

	/**
	 * Load (creating defaults if absent) the config from a config directory.
	 *
	 * @param configDir The loader's config directory.
	 * @return The populated config.
	 */
	public static ExporterConfig load(Path configDir) {
		ExporterConfig config = new ExporterConfig();
		Path file = configDir.resolve(FILE_NAME);

		Properties props = new Properties();
		if (Files.exists(file)) {
			try (InputStream in = Files.newInputStream(file)) {
				props.load(in);
			} catch (IOException e) {
				LOG.warning("Failed to read " + file + ", using defaults: " + e.getMessage());
			}
		}

		config.collector_jvm = bool(props, "collector.jvm", config.collector_jvm);
		config.collector_mc = bool(props, "collector.mc", config.collector_mc);
		config.collector_mc_entities = bool(props, "collector.mc_entities", config.collector_mc_entities);
		config.collector_mc_dimension_tick_errors = TickErrorPolicy.fromString(
			props.getProperty("collector.mc_dimension_tick_errors"));
		config.web_listen_address = props.getProperty("web.listen_address", config.web_listen_address);
		config.web_listen_port = intValue(props, "web.listen_port", config.web_listen_port);

		if (!Files.exists(file)) {
			writeDefaults(file, config);
		}
		return config;
	}

	private static void writeDefaults(Path file, ExporterConfig config) {
		Properties props = new Properties();
		props.setProperty("collector.jvm", Boolean.toString(config.collector_jvm));
		props.setProperty("collector.mc", Boolean.toString(config.collector_mc));
		props.setProperty("collector.mc_entities", Boolean.toString(config.collector_mc_entities));
		props.setProperty("collector.mc_dimension_tick_errors",
			config.collector_mc_dimension_tick_errors.name().toLowerCase());
		props.setProperty("web.listen_address", config.web_listen_address);
		props.setProperty("web.listen_port", Integer.toString(config.web_listen_port));
		try {
			Files.createDirectories(file.getParent());
			try (OutputStream out = Files.newOutputStream(file)) {
				props.store(out, "Prometheus Exporter configuration");
			}
		} catch (IOException e) {
			LOG.warning("Failed to write default config " + file + ": " + e.getMessage());
		}
	}

	private static boolean bool(Properties props, String key, boolean fallback) {
		String value = props.getProperty(key);
		return value != null ? Boolean.parseBoolean(value.trim()) : fallback;
	}

	private static int intValue(Properties props, String key, int fallback) {
		String value = props.getProperty(key);
		if (value != null) {
			try {
				return Integer.parseInt(value.trim());
			} catch (NumberFormatException e) {
				LOG.warning("Invalid integer for " + key + ": " + value);
			}
		}
		return fallback;
	}
}
