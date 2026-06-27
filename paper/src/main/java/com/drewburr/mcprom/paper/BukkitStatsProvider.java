package com.drewburr.mcprom.paper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.drewburr.mcprom.core.DimensionIds;
import com.drewburr.mcprom.core.ServerStatsProvider;
import com.drewburr.mcprom.core.dto.DimensionStats;
import com.drewburr.mcprom.core.dto.EntityTypeCount;
import com.drewburr.mcprom.core.dto.PlayerInfo;

import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;

/**
 * Feeds server data into the core collector using the Bukkit/Paper API.
 *
 * <p>Paper does not expose per-world tick timing the way the mod loaders do, so
 * {@link #supportsDimensionTickEvents()} returns false and dimension tick time
 * is approximated from TPS.</p>
 */
public class BukkitStatsProvider implements ServerStatsProvider {

	private final Server server;

	public BukkitStatsProvider(Server server) {
		this.server = server;
	}

	@Override
	public List<PlayerInfo> getOnlinePlayers() {
		List<PlayerInfo> players = new ArrayList<>();
		for (Player player : this.server.getOnlinePlayers()) {
			players.add(new PlayerInfo(player.getUniqueId().toString(), player.getName()));
		}
		return players;
	}

	@Override
	public List<DimensionStats> getDimensionStats(boolean withEntities) {
		List<DimensionStats> dimensions = new ArrayList<>();
		for (World world : this.server.getWorlds()) {
			int id = getDimensionId(world);
			String name = getDimensionName(world);
			int loadedChunks = world.getLoadedChunks().length;

			List<EntityTypeCount> entities = withEntities
				? countEntities(world)
				: List.of();

			dimensions.add(new DimensionStats(id, name, loadedChunks, entities));
		}
		return dimensions;
	}

	@Override
	public boolean supportsDimensionTickEvents() {
		// Paper ticks all worlds on one thread at one rate and exposes no
		// per-world tick timing, so no per-dimension tick metrics are emitted.
		return false;
	}

	/**
	 * Count entities by type in a world, merging all items under {@code "Item"}
	 * and excluding players (tracked separately).
	 */
	private static List<EntityTypeCount> countEntities(World world) {
		Map<String, Integer> totals = new HashMap<>();
		for (Entity entity : world.getEntities()) {
			if (entity instanceof Player) {
				continue;
			}
			String type = (entity instanceof Item) ? "Item" : entity.getType().name();
			totals.merge(type, 1, Integer::sum);
		}
		List<EntityTypeCount> counts = new ArrayList<>(totals.size());
		totals.forEach((type, count) -> counts.add(new EntityTypeCount(type, count)));
		return counts;
	}

	/**
	 * Map a Bukkit world to the legacy integer dimension id, for compatibility
	 * with the Forge/Fabric exporter and existing dashboards.
	 */
	private static int getDimensionId(World world) {
		return switch (world.getEnvironment()) {
			case NORMAL -> DimensionIds.OVERWORLD;
			case THE_END -> DimensionIds.THE_END;
			case NETHER -> DimensionIds.THE_NETHER;
			default -> DimensionIds.customId(world.getName());
		};
	}

	/**
	 * Map a Bukkit world to the canonical dimension name.
	 */
	private static String getDimensionName(World world) {
		return switch (world.getEnvironment()) {
			case NORMAL -> world.getName().equals("world") ? "overworld" : world.getName();
			case THE_END -> "the_end";
			case NETHER -> "the_nether";
			default -> world.getName();
		};
	}
}
