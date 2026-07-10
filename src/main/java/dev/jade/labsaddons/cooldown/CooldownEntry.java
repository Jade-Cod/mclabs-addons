package dev.jade.labsaddons.cooldown;

/**
 * One tracked cooldown row, as exposed by a {@link CooldownSource}: a stable
 * key (namespaced by source, e.g. "mcmmo:super_breaker"), a display label, and
 * when it will be ready. {@code active} marks an ability that is currently
 * running (no known end), shown as "Active" instead of a countdown.
 * {@code approximate} marks a countdown seeded from a default duration rather
 * than an authoritative server line. {@code totalMs} is the full cooldown
 * length, used to compute how much of the ring has recharged.
 */
public record CooldownEntry(String key, String label, long readyAtEpochMs, boolean active,
		boolean approximate, long totalMs) {

	public long remainingMs(long nowMs) {
		return Math.max(0L, readyAtEpochMs - nowMs);
	}

	/** Ready = recharge finished and the ability is not mid-use. */
	public boolean isReady(long nowMs) {
		return !active && remainingMs(nowMs) == 0L;
	}

	/** Fraction of the cooldown recharged so far, clamped to [0, 1]. */
	public float elapsedFraction(long nowMs) {
		if (totalMs <= 0L) {
			return 1f;
		}
		float elapsed = totalMs - remainingMs(nowMs);
		return Math.max(0f, Math.min(1f, elapsed / (float) totalMs));
	}
}
