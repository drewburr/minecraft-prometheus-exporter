package com.drewburr.mcprom.mod.forge;

import com.drewburr.mcprom.core.MinecraftCollector;
import com.drewburr.mcprom.mod.Dimensions;
import com.drewburr.mcprom.mod.ExporterCommon;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Forge entry point. Drives the loader-agnostic {@link ExporterCommon} from
 * Forge server lifecycle and tick events.
 */
@Mod(PrometheusExporterForge.MOD_ID)
public class PrometheusExporterForge {

	public static final String MOD_ID = "prometheus_exporter";

	private final ExporterCommon exporter = new ExporterCommon(FMLPaths.CONFIGDIR.get());

	public PrometheusExporterForge() {
		MinecraftForge.EVENT_BUS.register(this);
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
	public void onServerTick(TickEvent.ServerTickEvent event) {
		if (event.side != LogicalSide.SERVER) {
			return;
		}
		MinecraftCollector collector = this.exporter.collector();
		if (event.phase == TickEvent.Phase.START) {
			if (collector != null) {
				collector.startServerTick();
			}
		} else if (event.phase == TickEvent.Phase.END) {
			if (collector != null) {
				collector.stopServerTick();
			}
			this.exporter.onServerTickEnd();
		}
	}

	@SubscribeEvent
	public void onLevelTick(TickEvent.LevelTickEvent event) {
		if (event.side != LogicalSide.SERVER || !(event.level instanceof ServerLevel level)) {
			return;
		}
		MinecraftCollector collector = this.exporter.collector();
		if (collector == null) {
			return;
		}
		int id = Dimensions.id(level.dimension());
		String name = Dimensions.name(level.dimension());
		if (event.phase == TickEvent.Phase.START) {
			collector.startDimensionTick(id, name);
		} else if (event.phase == TickEvent.Phase.END) {
			collector.stopDimensionTick(id, name);
		}
	}
}
