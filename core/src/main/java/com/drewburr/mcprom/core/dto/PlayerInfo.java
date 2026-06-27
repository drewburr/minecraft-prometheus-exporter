package com.drewburr.mcprom.core.dto;

/**
 * An online player, as exported by the {@code mc_player_list} metric.
 *
 * @param id The player's unique id (UUID string).
 * @param name The player's name.
 */
public record PlayerInfo(String id, String name) {
}
