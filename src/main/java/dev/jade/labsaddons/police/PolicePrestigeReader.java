package dev.jade.labsaddons.police;

import dev.jade.labsaddons.prestige.PrestigeChem;
import dev.jade.labsaddons.prestige.PrestigeTracker;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the police prestige ladder out of the {@code /prestige} GUI — the same
 * passive once-per-open scrape as {@link dev.jade.labsaddons.mastery.MasteryReader}.
 * Never sends a command, never closes the screen.
 *
 * <p>A tier is a slot named {@code "✘ Police - Collect Contraband III"} whose lore
 * carries a progress bar:
 *
 * <pre>
 * Goal:      Confiscate 225 inventories of contraband.
 * Progress:  [|||||||||| 404725/518400 ||||||||||]
 * </pre>
 *
 * <p>Only the nearest unmet tier is kept. The server runs a single counter behind all
 * of them — III, IV, V and VI every one of them reading 404,725 — so tracking more
 * than one would stack identical bars with different denominators. Finished tiers
 * (✔, "Prestige unlocked.") are skipped outright: there is nothing left to watch.
 *
 * <p>ponytail: the chem half of the same GUI ("✔ Chems - Sell Wheatium") is ignored,
 * because the chat listing already syncs those and no unfinished chem slot has been
 * seen to confirm its lore shape.
 */
public final class PolicePrestigeReader {
	private static final String TIER_MARKER = "Police - Collect Contraband";
	/** "404725/518400" out of the bar, ignoring the pipes drawn around it. */
	private static final Pattern PROGRESS =
			Pattern.compile("([\\d,]+(?:\\.\\d+)?)\\s*/\\s*([\\d,]+(?:\\.\\d+)?)");

	private PolicePrestigeReader() {
	}

	/** @return true if this was the prestige GUI (and the tracked tier was refreshed). */
	public static boolean tryRead(AbstractContainerScreen<?> screen) {
		PrestigeChem nearest = null;
		for (Slot slot : screen.getMenu().slots) {
			PrestigeChem tier = parseTier(slot.getItem());
			if (tier != null && (nearest == null || tier.target() < nearest.target())) {
				nearest = tier;
			}
		}
		if (nearest == null) {
			return false;
		}
		PrestigeTracker.merge(List.of(nearest));
		return true;
	}

	/** @return the tier this stack represents, or null if it is not an unmet police tier. */
	private static PrestigeChem parseTier(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		Component customName = stack.get(DataComponents.CUSTOM_NAME);
		ItemLore lore = stack.get(DataComponents.LORE);
		if (customName == null || lore == null) {
			return null;
		}
		return parseTier(customName.getString(), lore.lines().stream().map(Component::getString).toList());
	}

	/**
	 * Pure name-and-lore parse, split out so it is testable without a Minecraft
	 * bootstrap — the same split {@code MasteryReader.parseProgress} makes.
	 */
	static PrestigeChem parseTier(String displayName, List<String> loreLines) {
		int marker = displayName.indexOf(TIER_MARKER);
		if (marker < 0) {
			return null;
		}
		String tier = displayName.substring(marker + TIER_MARKER.length()).trim();
		if (tier.isEmpty()) {
			return null;
		}
		for (String line : loreLines) {
			Matcher progress = PROGRESS.matcher(line);
			if (!progress.find()) {
				continue;
			}
			double current = parseNumber(progress.group(1));
			double target = parseNumber(progress.group(2));
			// An already-met tier is one the server simply hasn't ticked over yet; the
			// next one down the ladder is the one worth watching.
			return target > 0 && current < target
					? new PrestigeChem(PoliceContraband.TRACK_PREFIX + tier, current, target)
					: null;
		}
		return null;
	}

	/** Strips digit grouping ("518,400" -> 518400.0). */
	private static double parseNumber(String raw) {
		try {
			return Double.parseDouble(raw.replace(",", ""));
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
