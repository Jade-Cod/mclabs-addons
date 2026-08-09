package dev.jade.labsaddons.prestige;

import dev.jade.labsaddons.mastery.MasteryGains;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Holds the player's chem prestige tracks for the HUD, in the same static
 * no-injection style as {@code MasteryTracker} / {@code RunnerTracker}.
 *
 * <p>Two inputs, both from {@link PrestigeChat}: {@code /prestige progress} supplies
 * authoritative current/target figures, and a sale's hover supplies an exact gain to
 * apply between syncs. No client-side arithmetic is involved in either — the server
 * states both, and it rounds in ways the client cannot reproduce.
 *
 * <p>Kept free of Minecraft and config types so it stays unit-testable without a game
 * runtime; persistence lives in {@link PrestigeStore}.
 */
public final class PrestigeTracker {
	/** Keyed by lowercased chem name; insertion-ordered so the HUD's row order is stable. */
	private static volatile Map<String, PrestigeChem> chems = Map.of();

	private PrestigeTracker() {
	}

	/**
	 * Folds an authoritative sync into the tracked set.
	 *
	 * <p>Deliberately a merge rather than a replace: it is not established whether a
	 * finished chem still appears in {@code /prestige progress}, and dropping a chem
	 * that merely went unlisted would erase a completed track. Anything absent keeps
	 * the figures it already had.
	 */
	public static void merge(List<PrestigeChem> update) {
		if (update == null || update.isEmpty()) {
			return;
		}
		Map<String, PrestigeChem> next = new LinkedHashMap<>(chems);
		for (PrestigeChem chem : update) {
			if (chem != null && chem.chem() != null && !chem.chem().isBlank() && chem.target() > 0) {
				next.put(key(chem.chem()), chem);
			}
		}
		// Published rather than mutated in place: every write builds a fresh map, so a
		// reader holding the previous one never sees a half-applied sync.
		chems = next;
	}

	/**
	 * Optimistically advances one chem by {@code delta}.
	 *
	 * <p>Only a chem already known from a sync is advanced. An unknown one has no goal
	 * to measure against, and inventing a track from a single sale would render a bar
	 * with a made-up target — showing nothing until the first {@code /prestige progress}
	 * is the honest failure. {@link PrestigeStore} restores the known set on launch so
	 * this gap does not reopen every session.
	 *
	 * @return true if a known, unfinished chem matched and moved.
	 */
	public static boolean advance(String chem, double delta) {
		if (chem == null || delta <= 0) {
			return false;
		}
		PrestigeChem existing = chems.get(key(chem));
		if (existing == null || existing.isComplete()) {
			return false;
		}
		PrestigeChem advanced = existing.advancedBy(delta);
		if (advanced.current() == existing.current()) {
			return false;
		}
		Map<String, PrestigeChem> next = new LinkedHashMap<>(chems);
		next.put(key(chem), advanced);
		chems = next;
		// Under the chem's own spelling, which is what the HUD renders and looks gains
		// up by. The applied amount, not the requested one: a bump clamped at the goal
		// moved less than asked, and "+8,273" on a smaller move would lie.
		MasteryGains.record(existing.chem(), advanced.current() - existing.current());
		return true;
	}

	/** Every tracked chem, in the order the server first listed them. */
	public static List<PrestigeChem> chems() {
		return List.copyOf(chems.values());
	}

	public static boolean hasData() {
		return !chems.isEmpty();
	}

	public static void clear() {
		chems = Map.of();
	}

	private static String key(String chem) {
		return chem.toLowerCase(Locale.ROOT).trim();
	}
}
