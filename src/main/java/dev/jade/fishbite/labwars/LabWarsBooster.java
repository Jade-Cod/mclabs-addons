package dev.jade.fishbite.labwars;

/** One Lab Wars revenue booster. timerKnown=false = type+multiplier known but no countdown yet (run /lw rates). */
public class LabWarsBooster {
	public String key = "";
	public double multiplier = 1.0;
	public long expiryEpochMs = 0L;
	public boolean timerKnown = false;

	public LabWarsBooster() {
	}

	public LabWarsBooster(String key, double multiplier, long expiryEpochMs, boolean timerKnown) {
		this.key = key;
		this.multiplier = multiplier;
		this.expiryEpochMs = expiryEpochMs;
		this.timerKnown = timerKnown;
	}

	public long remainingMs() {
		return Math.max(0L, expiryEpochMs - System.currentTimeMillis());
	}
}
