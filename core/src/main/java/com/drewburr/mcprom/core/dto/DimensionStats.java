package com.drewburr.mcprom.core.dto;

import java.util.List;

/**
 * A snapshot of one dimension (world), feeding the
 * {@code mc_dimension_chunks_loaded} and {@code mc_entities_total} metrics.
 *
 * <p>The {@code id} uses the legacy integer dimension-id scheme (see
 * {@link com.drewburr.mcprom.core.DimensionIds}) so existing dashboards keep
 * working across platforms.</p>
 *
 * @param id The legacy integer dimension id.
 * @param name The dimension name (e.g. {@code overworld}, {@code the_nether}).
 * @param loadedChunks The number of loaded chunks in this dimension.
 * @param entities Per-type entity counts; empty when entity collection is
 *     disabled.
 */
public record DimensionStats(
	int id,
	String name,
	int loadedChunks,
	List<EntityTypeCount> entities
) {
}
