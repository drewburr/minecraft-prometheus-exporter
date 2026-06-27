package com.drewburr.mcprom.mod.neoforge;

import com.drewburr.mcprom.core.MinecraftCollector;
import com.drewburr.mcprom.mod.Dimensions;
import com.drewburr.mcprom.mod.ExporterCommon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * NeoForge entry point. Drives the loader-agnostic {@link ExporterCommon} from
 * NeoForge server lifecycle and tick events.
 */
@Mod(PrometheusExporterNeoForge.MOD_ID)
public class PrometheusExporterNeoForge {

	public static final String MOD_ID = "prometheus_exporter";

	private final ExporterCommon exporter = new ExporterCommon(FMLPaths.CONFIGDIR.get());

	public PrometheusExporterNeoForge(IEventBus modBus) {
		NeoForge.EVENT_BUS.register(this);
	}

	@SubscribeEvent
	public void onServerStarted(ServerStartedEvent event) {
		this.exporter.onServerStarted(event.getServer());
	}

	@SubscribeEvent
	public void onServerStopped(ServerStoppedEvent event) {
		this.exporter.onServerStopped();
	}

	@SubscribeEvent
	public void onServerTickPre(ServerTickEvent.Pre event) {
		MinecraftCollector collector = this.exporter.collector();
		if (collector != null) {
			collector.startServerTick();
		}
	}

	@SubscribeEvent
	public void onServerTickPost(ServerTickEvent.Post event) {
		MinecraftCollector collector = this.exporter.collector();
		if (collector != null) {
			collector.stopServerTick();
		}
		this.exporter.onServerTickEnd();
	}

	@SubscribeEvent
	public void onLevelTickPre(LevelTickEvent.Pre event) {
		if (event.getLevel() instanceof ServerLevel level) {
			MinecraftCollector collector = this.exporter.collector();
			if (collector != null) {
				collector.startDimensionTick(Dimensions.id(level.dimension()), Dimensions.name(level.dimension()));
			}
		}
	}

	@SubscribeEvent
	public void onLevelTickPost(LevelTickEvent.Post event) {
		if (event.getLevel() instanceof ServerLevel level) {
			MinecraftCollector collector = this.exporter.collector();
			if (collector != null) {
				collector.stopDimensionTick(Dimensions.id(level.dimension()), Dimensions.name(level.dimension()));
			}
		}
	}
}
