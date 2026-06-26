package com.drewburr.mcprom.mod.fabric;

import com.drewburr.mcprom.core.MinecraftCollector;
import com.drewburr.mcprom.mod.Dimensions;
import com.drewburr.mcprom.mod.ExporterCommon;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric (dedicated server) entry point. Drives the loader-agnostic {@link
 * ExporterCommon} from Fabric lifecycle and tick events.
 */
public class PrometheusExporterFabric implements DedicatedServerModInitializer {

	private final ExporterCommon exporter =
		new ExporterCommon(FabricLoader.getInstance().getConfigDir());

	@Override
	public void onInitializeServer() {
		ServerLifecycleEvents.SERVER_STARTED.register(this.exporter::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> this.exporter.onServerStopped());

		ServerTickEvents.START_SERVER_TICK.register(server -> {
			MinecraftCollector collector = this.exporter.collector();
			if (collector != null) {
				collector.startServerTick();
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			MinecraftCollector collector = this.exporter.collector();
			if (collector != null) {
				collector.stopServerTick();
			}
			this.exporter.onServerTickEnd();
		});

		ServerTickEvents.START_WORLD_TICK.register(world -> {
			MinecraftCollector collector = this.exporter.collector();
			if (collector != null) {
				collector.startDimensionTick(Dimensions.id(world.dimension()), Dimensions.name(world.dimension()));
			}
		});
		ServerTickEvents.END_WORLD_TICK.register(world -> {
			MinecraftCollector collector = this.exporter.collector();
			if (collector != null) {
				collector.stopDimensionTick(Dimensions.id(world.dimension()), Dimensions.name(world.dimension()));
			}
		});
	}
}
