package dev.jade.fishbite.booster;

/** One active server booster, persisted in the config so it survives relogs. */
public class BoosterState {
	public String item = "";
	public double multiplier = 1.0;
	public long expiryEpochMs;

	public BoosterState() {
	}

	public BoosterState(String item, double multiplier, long expiryEpochMs) {
		this.item = item;
		this.multiplier = multiplier;
		this.expiryEpochMs = expiryEpochMs;
	}

	public long remainingMs() {
		return Math.max(0L, expiryEpochMs - System.currentTimeMillis());
	}
}
