package dev.jade.labsaddons.prestige;

import dev.jade.labsaddons.config.LabsAddonsConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Persists the prestige tracks to the config so they survive a client restart.
 *
 * <p>This is what keeps {@link PrestigeTracker#advance} useful. It only moves chems it
 * already knows, so without a restored set the first sale of every session would be
 * dropped until the player happened to run {@code /prestige progress}.
 *
 * <p>No expiry, unlike the Mastery board: prestige goals are fixed per chem and progress
 * only ever accumulates, so a stale figure is merely out of date — never wrong about
 * which track it belongs to — and the next sync corrects it.
 */
public final class PrestigeStore {
	private PrestigeStore() {
	}

	/** Restores the last saved tracks. Safe to call before the item registry exists. */
	public static void load() {
		List<PrestigeChemEntry> saved = LabsAddonsConfig.get().prestigeChems;
		if (saved == null || saved.isEmpty()) {
			return;
		}
		List<PrestigeChem> restored = new ArrayList<>(saved.size());
		for (PrestigeChemEntry entry : saved) {
			restored.add(new PrestigeChem(entry.chem, entry.current, entry.target, entry.unlocked));
		}
		PrestigeTracker.merge(restored);
	}

	/** Writes the current tracks back to the config. */
	public static void save() {
		LabsAddonsConfig config = LabsAddonsConfig.get();
		List<PrestigeChemEntry> entries = new ArrayList<>();
		for (PrestigeChem chem : PrestigeTracker.chems()) {
			entries.add(new PrestigeChemEntry(chem.chem(), chem.current(), chem.target(), chem.unlocked()));
		}
		config.prestigeChems = entries;
		config.save();
	}

	/** Drops the tracks from memory and from disk, so "Clear Prestige" actually sticks. */
	public static void clear() {
		PrestigeTracker.clear();
		LabsAddonsConfig config = LabsAddonsConfig.get();
		config.prestigeChems = new ArrayList<>();
		config.save();
	}
}
