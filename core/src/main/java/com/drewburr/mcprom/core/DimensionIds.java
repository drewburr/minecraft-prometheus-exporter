package com.drewburr.mcprom.core;

/**
 * Canonical legacy dimension-id scheme, shared by every platform so the
 * {@code id} label stays byte-for-byte compatible with the original
 * Forge/Fabric exporter (and existing dashboards).
 *
 * <p>Vanilla dimensions use fixed ids; custom dimensions derive an id from the
 * dimension name's hash code.</p>
 */
public final class DimensionIds {

	/** The overworld dimension id. */
	public static final int OVERWORLD = 0;

	/** The end dimension id. */
	public static final int THE_END = 1;

	/** The nether dimension id. */
	public static final int THE_NETHER = -1;

	private DimensionIds() {
	}

	/**
	 * The id for a custom (non-vanilla) dimension.
	 *
	 * @param name The dimension/world name.
	 * @return The derived dimension id.
	 */
	public static int customId(String name) {
		return name.hashCode();
	}
}
