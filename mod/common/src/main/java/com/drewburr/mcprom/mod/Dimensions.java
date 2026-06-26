package com.drewburr.mcprom.mod;

import com.drewburr.mcprom.core.DimensionIds;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Maps a vanilla {@link Level} dimension key to the legacy integer id / canonical
 * name shared across platforms. Used by both the stats provider and the
 * per-loader tick hooks.
 */
public final class Dimensions {

	private Dimensions() {
	}

	/**
	 * The legacy integer dimension id.
	 */
	public static int id(ResourceKey<Level> dim) {
		if (dim.equals(Level.OVERWORLD)) {
			return DimensionIds.OVERWORLD;
		} else if (dim.equals(Level.END)) {
			return DimensionIds.THE_END;
		} else if (dim.equals(Level.NETHER)) {
			return DimensionIds.THE_NETHER;
		} else {
			return DimensionIds.customId(dim.location().getPath());
		}
	}

	/**
	 * The dimension name.
	 */
	public static String name(ResourceKey<Level> dim) {
		return dim.location().getPath();
	}
}
