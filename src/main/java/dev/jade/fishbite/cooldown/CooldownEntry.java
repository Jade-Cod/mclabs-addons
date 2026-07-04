package dev.jade.fishbite.cooldown;

/**
 * One tracked cooldown row, as exposed by a {@link CooldownSource}: a stable
 * key (namespaced by source, e.g. "mcmmo:super_breaker"), a display label, and
 * when it will be ready. {@code active} marks an ability that is currently
 * running (no known end), shown as "Active" instead of a countdown.
 * {@code approximate} marks a countdown seeded from a default duration rather
 * than an authoritative server line; it renders with a "~" prefix.
 */
public record CooldownEntry(String key, String label, long readyAtEpochMs, boolean active,
		boolean approximate) {

	public long remainingMs(long nowMs) {
		return Math.max(0L, readyAtEpochMs - nowMs);
	}

	/** Ready = recharge finished and the ability is not mid-use. */
	public boolean isReady(long nowMs) {
		return !active && remainingMs(nowMs) == 0L;
	}
}
