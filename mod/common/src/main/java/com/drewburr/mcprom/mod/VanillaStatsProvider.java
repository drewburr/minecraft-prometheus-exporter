package com.drewburr.mcprom.mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.drewburr.mcprom.core.ServerStatsProvider;
import com.drewburr.mcprom.core.dto.DimensionStats;
import com.drewburr.mcprom.core.dto.EntityTypeCount;
import com.drewburr.mcprom.core.dto.PlayerInfo;

import com.mojang.authlib.GameProfile;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Feeds server data into the core collector using {@code net.minecraft}
 * internals. Shared (Mojang-mapped) across the Fabric/Forge/NeoForge loaders.
 *
 * <p>The loaders deliver real per-dimension tick events, so {@link
 * #supportsDimensionTickEvents()} returns true and {@link
 * #getApproximateTickSeconds()} is unused.</p>
 */
public class VanillaStatsProvider implements ServerStatsProvider {

	private final MinecraftServer server;

	public VanillaStatsProvider(MinecraftServer server) {
		this.server = server;
	}

	@Override
	public List<PlayerInfo> getOnlinePlayers() {
		List<PlayerInfo> players = new ArrayList<>();
		for (ServerPlayer player : this.server.getPlayerList().getPlayers()) {
			GameProfile profile = player.getGameProfile();
			players.add(new PlayerInfo(profile.id().toString(), profile.name()));
		}
		return players;
	}

	@Override
	public List<DimensionStats> getDimensionStats(boolean withEntities) {
		List<DimensionStats> dimensions = new ArrayList<>();
		for (ServerLevel level : this.server.getAllLevels()) {
			ResourceKey<Level> dim = level.dimension();
			int id = Dimensions.id(dim);
			String name = Dimensions.name(dim);
			int loadedChunks = level.getChunkSource().getLoadedChunksCount();

			List<EntityTypeCount> entities = withEntities ? countEntities(level) : List.of();
			dimensions.add(new DimensionStats(id, name, loadedChunks, entities));
		}
		return dimensions;
	}

	@Override
	public boolean supportsDimensionTickEvents() {
		return true;
	}

	/**
	 * Count entities by type in a level, merging items under {@code "Item"} and
	 * excluding players.
	 */
	private static List<EntityTypeCount> countEntities(ServerLevel level) {
		Map<String, Integer> totals = new HashMap<>();
		for (Entity entity : level.getAllEntities()) {
			if (entity instanceof Player) {
				continue;
			}
			String type = (entity instanceof ItemEntity) ? "Item" : entity.getName().getString();
			totals.merge(type, 1, Integer::sum);
		}
		List<EntityTypeCount> counts = new ArrayList<>(totals.size());
		totals.forEach((type, count) -> counts.add(new EntityTypeCount(type, count)));
		return counts;
	}
}
