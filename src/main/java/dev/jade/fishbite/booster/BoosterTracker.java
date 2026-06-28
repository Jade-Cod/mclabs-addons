package dev.jade.fishbite.booster;

import dev.jade.fishbite.chem.ChemIcons;
import dev.jade.fishbite.config.FishBiteConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tracks server boosters from their chat announcements, e.g.
 * "Booster activated! Sugcarronide boosted 1.2x by Ophiliah for 30m."
 * Chat is the only reliable source: any player can pop a booster and it
 * applies to everyone, so inventory state says nothing about active boosts.
 */
public final class BoosterTracker {
	private static final Logger LOGGER = LoggerFactory.getLogger("fishbite");

	private static final Pattern ANNOUNCEMENT = Pattern.compile(
			"Booster activated!\\s+(.+?)\\s+boosted\\s+([0-9]+(?:\\.[0-9]+)?)x\\s+by\\s+.+?\\s+for\\s+((?:\\d+[dhms])+)",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern DURATION_PART = Pattern.compile("(\\d+)([dhms])");

	private BoosterTracker() {
	}

	/** Feed every incoming chat/system line through this. */
	public static void onMessage(String plainText) {
		Matcher matcher = ANNOUNCEMENT.matcher(plainText);
		if (!matcher.find()) {
			return;
		}

		String item = matcher.group(1).trim();
		double multiplier;
		try {
			multiplier = Double.parseDouble(matcher.group(2));
		} catch (NumberFormatException e) {
			return;
		}
		long durationMs = parseDurationMs(matcher.group(3));
		if (durationMs <= 0) {
			return;
		}

		track(item, multiplier, System.currentTimeMillis() + durationMs);
		LOGGER.info("[fishbite] Tracked booster: {} {}x for {}", item, multiplier, matcher.group(3));
	}

	/**
	 * Upsert one active booster. Shared by the chat announcement above and the
	 * /chems "Booster(s) active!" GUI scrape ({@link BoosterRatesReader}); the GUI's
	 * "Time left" simply refreshes the countdown, the same way /lw rates does.
	 */
	public static synchronized void track(String item, double multiplier, long expiryEpochMs) {
		FishBiteConfig config = FishBiteConfig.get();
		config.boosters.put(storageKey(item), new BoosterState(item, multiplier, expiryEpochMs));
		config.saveAsync();
	}

	/** Canonical map key: every "All Chems"/"all_chem_booster" variant collapses to one entry. */
	private static String storageKey(String item) {
		return ChemIcons.isAllBooster(item) ? "all" : item.toLowerCase(Locale.ROOT);
	}

	/** Parses server durations like "20m", "1h30m", or the GUI's "13m:53s". */
	public static long parseDurationMs(String text) {
		long totalMs = 0;
		Matcher part = DURATION_PART.matcher(text.toLowerCase(Locale.ROOT));
		while (part.find()) {
			long amount = Long.parseLong(part.group(1));
			totalMs += switch (part.group(2)) {
				case "d" -> amount * 86_400_000L;
				case "h" -> amount * 3_600_000L;
				case "m" -> amount * 60_000L;
				default -> amount * 1_000L;
			};
		}
		return totalMs;
	}

	/** Active boosters sorted by soonest expiry; prunes expired entries. */
	public static synchronized List<BoosterState> active() {
		FishBiteConfig config = FishBiteConfig.get();
		boolean pruned = config.boosters.values()
				.removeIf(booster -> booster == null || booster.remainingMs() <= 0);
		if (pruned) {
			config.saveAsync();
		}
		List<BoosterState> list = new ArrayList<>(config.boosters.values());
		list.sort(Comparator.comparingLong(booster -> booster.expiryEpochMs));
		return list;
	}

	public static synchronized void clear() {
		FishBiteConfig config = FishBiteConfig.get();
		config.boosters.clear();
		config.saveAsync();
	}

	public static String formatRemaining(BoosterState booster) {
		long totalSeconds = (booster.remainingMs() + 999L) / 1000L;
		long hours = totalSeconds / 3600L;
		long minutes = (totalSeconds % 3600L) / 60L;
		long seconds = totalSeconds % 60L;
		return hours > 0
				? String.format("%d:%02d:%02d", hours, minutes, seconds)
				: String.format("%d:%02d", minutes, seconds);
	}

	/** "2x" for whole numbers, otherwise "1.2x". */
	public static String formatMultiplier(double multiplier) {
		return multiplier == Math.floor(multiplier)
				? (long) multiplier + "x"
				: multiplier + "x";
	}
}
