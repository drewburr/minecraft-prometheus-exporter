package com.drewburr.mcprom.core;

/**
 * How to handle an unbalanced dimension tick (a stop without a matching start,
 * or a start without a matching stop). On loaders that run some dimension ticks
 * off the main thread, these can occur legitimately.
 */
public enum TickErrorPolicy {

	/** Silently ignore the mismatched tick. */
	IGNORE,

	/** Log a warning but continue. */
	LOG,

	/** Throw an exception. Intended for debugging. */
	STRICT;

	/**
	 * Parse a policy from a config string, defaulting to {@link #LOG}.
	 *
	 * @param value The configured value (case-insensitive); may be null.
	 * @return The matching policy, or {@link #LOG} if unrecognized.
	 */
	public static TickErrorPolicy fromString(String value) {
		if (value != null) {
			for (TickErrorPolicy policy : values()) {
				if (policy.name().equalsIgnoreCase(value.trim())) {
					return policy;
				}
			}
		}
		return LOG;
	}
}
