package dev.jade.fishbite.labwars;

import dev.jade.fishbite.config.FishBiteConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tracks Lab Wars revenue boosters (council decision: passive only). Boosters
 * STACK multiplicatively (e.g. a 1.5x for 1h plus a 1.1x shop boost for 5m show
 * as Current/Active 1.65x), each with its own timer — so we keep a flat list and
 * may show several rows for one category. Sources:
 * <ol>
 *   <li>"Revenue Booster Activated" / "Shop Boost Activated" — one booster, exact timer.</li>
 *   <li>"Shop Boost Extended" — extends a matching booster.</li>
 *   <li>"Active Revenue Boosts: - Fishing: 1.65x" — the COMBINED multiplier, no timer
 *       (only used as a placeholder when nothing else is known for that category).</li>
 *   <li>The /lw rates GUI, read while the player has it open — authoritative per-category breakdown.</li>
 * </ol>
 */
public final class LabWarsTracker {
	private static final Logger LOGGER = LoggerFactory.getLogger("fishbite");
	private static final double MULT_EPSILON = 0.005;

	private static final Pattern ACTIVATED = Pattern.compile(
			"revenue rate of\\s+(.+?)\\s+has been boosted by\\s+([0-9.]+)x\\s+for the next\\s+(.+?)\\s+by",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern EXTENDED = Pattern.compile(
			"The\\s+([0-9.]+)x\\s+revenue rate of\\s+(.+?)\\s+has been extended\\s+(?:another\\s+)?(.+?)\\s+by",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern ACTIVE_HEADER =
			Pattern.compile("Active Revenue Boosts", Pattern.CASE_INSENSITIVE);
	private static final Pattern ACTIVE_LINE =
			Pattern.compile("[-•]\\s*([A-Za-z ]+?):\\s*([0-9.]+)x", Pattern.CASE_INSENSITIVE);

	private LabWarsTracker() {
	}

	public static void onMessage(String text) {
		Matcher activated = ACTIVATED.matcher(text);
		if (activated.find()) {
			String key = LabWarsType.keyOf(activated.group(1));
			if (key != null) {
				addOrRefresh(key, parseDouble(activated.group(2)),
						System.currentTimeMillis() + LabWarsDuration.parseMs(activated.group(3)));
			}
			return;
		}

		Matcher extended = EXTENDED.matcher(text);
		if (extended.find()) {
			String key = LabWarsType.keyOf(extended.group(2));
			if (key != null) {
				extend(key, parseDouble(extended.group(1)), LabWarsDuration.parseMs(extended.group(3)));
			}
			return;
		}

		if (ACTIVE_HEADER.matcher(text).find()) {
			applyActiveSummary(text);
		}
	}

	private static List<LabWarsBooster> list() {
		return FishBiteConfig.get().labWarsActive;
	}

	/** Adds a new booster, or refreshes the timer of an equal-multiplier one in the same category. */
	private static void addOrRefresh(String key, double multiplier, long expiry) {
		LabWarsBooster match = find(key, multiplier);
		if (match != null) {
			match.expiryEpochMs = expiry;
			match.timerKnown = true;
		} else {
			list().add(new LabWarsBooster(key, multiplier, expiry, true));
		}
		FishBiteConfig.get().save();
		LOGGER.info("[fishbite] Lab Wars booster: {} {}x", key, multiplier);
	}

	private static void extend(String key, double multiplier, long durationMs) {
		LabWarsBooster match = find(key, multiplier);
		if (match != null && match.timerKnown) {
			match.expiryEpochMs += durationMs;
		} else if (match == null) {
			list().add(new LabWarsBooster(key, multiplier, 0L, false));
		}
		FishBiteConfig.get().save();
	}

	private static LabWarsBooster find(String key, double multiplier) {
		for (LabWarsBooster b : list()) {
			if (b.key.equals(key) && Math.abs(b.multiplier - multiplier) < MULT_EPSILON) {
				return b;
			}
		}
		return null;
	}

	/** Combined-multiplier login summary: only seeds a placeholder for categories we know nothing about. */
	private static void applyActiveSummary(String text) {
		FishBiteConfig config = FishBiteConfig.get();
		Set<String> listed = new HashSet<>();
		Matcher line = ACTIVE_LINE.matcher(text);
		while (line.find()) {
			String key = LabWarsType.keyOf(line.group(1));
			if (key == null) {
				continue;
			}
			listed.add(key);
			boolean known = config.labWarsActive.stream().anyMatch(b -> b.key.equals(key));
			if (!known) {
				config.labWarsActive.add(new LabWarsBooster(key, parseDouble(line.group(2)), 0L, false));
			}
		}
		// Drop placeholders (timer-less) for categories the summary no longer lists.
		config.labWarsActive.removeIf(b -> !b.timerKnown && b.expiryEpochMs == 0L && !listed.contains(b.key));
		config.save();
	}

	/** Authoritative per-category breakdown from /lw rates: replaces this category's boosters. */
	public static void setCategory(String key, List<LabWarsBooster> boosters) {
		FishBiteConfig config = FishBiteConfig.get();
		config.labWarsActive.removeIf(b -> b.key.equals(key));
		config.labWarsActive.addAll(boosters);
		config.save();
	}

	/** Active boosters, known-timers first (soonest expiry), then placeholders. Prunes expired. */
	public static List<LabWarsBooster> active() {
		FishBiteConfig config = FishBiteConfig.get();
		if (config.labWarsActive.removeIf(b -> b == null || (b.timerKnown && b.remainingMs() <= 0))) {
			config.save();
		}
		List<LabWarsBooster> list = new ArrayList<>(config.labWarsActive);
		list.sort(Comparator
				.comparing((LabWarsBooster b) -> !b.timerKnown)
				.thenComparingLong(b -> b.expiryEpochMs));
		return list;
	}

	public static void clear() {
		FishBiteConfig.get().labWarsActive.clear();
		FishBiteConfig.get().save();
	}

	private static double parseDouble(String value) {
		try {
			return Double.parseDouble(value);
		} catch (NumberFormatException e) {
			return 1.0;
		}
	}
}
