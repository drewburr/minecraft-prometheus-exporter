package com.drewburr.mcprom.core;

import java.util.List;

import com.drewburr.mcprom.core.dto.DimensionStats;
import com.drewburr.mcprom.core.dto.PlayerInfo;

/**
 * The seam between the platform-agnostic {@link MinecraftCollector} and a
 * specific platform (Paper via the Bukkit API, or a mod via {@code
 * net.minecraft} internals). Each platform implements this to feed server data
 * into the shared collector.
 *
 * <p>Snapshot methods are pulled from {@code MinecraftCollector.updateCache()},
 * which the platform invokes on the main server thread. Tick timing is instead
 * driven by the platform via the collector's {@code start/stop*Tick} hooks.</p>
 */
public interface ServerStatsProvider {

	/**
	 * The currently online players. Feeds {@code mc_player_list}.
	 *
	 * @return The online players.
	 */
	List<PlayerInfo> getOnlinePlayers();

	/**
	 * A per-dimension snapshot (loaded chunk counts and, optionally, per-type
	 * entity counts). Feeds {@code mc_dimension_chunks_loaded} and {@code
	 * mc_entities_total}.
	 *
	 * @param withEntities Whether to populate per-type entity counts.
	 * @return The per-dimension stats.
	 */
	List<DimensionStats> getDimensionStats(boolean withEntities);

	/**
	 * Whether this platform delivers real per-dimension tick events (driving the
	 * collector's {@code start/stopDimensionTick} hooks). When false, the
	 * collector does not emit per-dimension tick metrics at all, since the
	 * platform (e.g. Paper) cannot measure per-world tick timing.
	 *
	 * @return True if per-dimension tick events are available.
	 */
	boolean supportsDimensionTickEvents();
}
