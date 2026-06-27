package com.drewburr.mcprom.core.dto;

/**
 * The number of entities of a given type within a dimension. Items are merged
 * under the single type {@code "Item"} by the platform provider rather than
 * counted individually.
 *
 * @param type The entity type name.
 * @param count The number of entities of this type.
 */
public record EntityTypeCount(String type, int count) {
}
