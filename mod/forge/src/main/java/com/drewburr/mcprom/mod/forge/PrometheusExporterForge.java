package com.drewburr.mcprom.mod.forge;

import com.drewburr.mcprom.core.MinecraftCollector;
import com.drewburr.mcprom.mod.Dimensions;
import com.drewburr.mcprom.mod.ExporterCommon;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Forge entry point. Drives the loader-agnostic {@link ExporterCommon} from
 * Forge server lifecycle and tick events.
 *
 * <p>Forge 26.x uses the new (eventbus 7) record-event model: each event type
 * exposes a static {@code BUS} you add listeners to, and tick events are split
 * into {@code Pre}/{@code Post} instead of a phase field.</p>
 */
@Mod(PrometheusExporterForge.MOD_ID)
public class PrometheusExporterForge {

	public static final String MOD_ID = "prometheus_exporter";

	private final ExporterCommon exporter = new ExporterCommon(FMLPaths.CONFIGDIR.get());

	public PrometheusExporterForge() {
		ServerStartedEvent.BUS.addListener(event -> this.exporter.onServerStarted(event.getServer()));
		ServerStoppedEvent.BUS.addListener(event -> this.exporter.onServerStopped());

		TickEvent.ServerTickEvent.Pre.BUS.addListener(event -> {
			MinecraftCollector collector = this.exporter.collector();
			if (collector != null) {
				collector.startServerTick();
			}
		});
		TickEvent.ServerTickEvent.Post.BUS.addListener(event -> {
			MinecraftCollector collector = this.exporter.collector();
			if (collector != null) {
				collector.stopServerTick();
			}
			this.exporter.onServerTickEnd();
		});

		TickEvent.LevelTickEvent.Pre.BUS.addListener(event -> {
			if (event.level() instanceof ServerLevel level) {
				MinecraftCollector collector = this.exporter.collector();
				if (collector != null) {
					collector.startDimensionTick(Dimensions.id(level.dimension()), Dimensions.name(level.dimension()));
				}
			}
		});
		TickEvent.LevelTickEvent.Post.BUS.addListener(event -> {
			if (event.level() instanceof ServerLevel level) {
				MinecraftCollector collector = this.exporter.collector();
				if (collector != null) {
					collector.stopDimensionTick(Dimensions.id(level.dimension()), Dimensions.name(level.dimension()));
				}
			}
		});
	}
}
