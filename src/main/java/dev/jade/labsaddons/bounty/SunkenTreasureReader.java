package dev.jade.labsaddons.bounty;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Re-syncs the Sunken Treasure count from the {@code /fw} GUI, the same passive
 * once-per-open scrape as {@link dev.jade.labsaddons.runner.SupplierJobsReader}. Chat
 * alone leaves you blind if you join mid-wave, or miss the announcement; the menu always
 * states the current figure on its "Sunken Treasure Spotted!" head — {@code "6 crates
 * left!"}. Note the menu says <em>crates</em> where chat says <em>barrels</em>.
 *
 * <p>The read is authoritative in both directions: on the {@code /fw} screen with no
 * treasure head, the wave is over and the widget is cleared. The screen is identified by
 * its "Fishing Weekend Countdown" clock, so no other GUI can clear us by accident.
 */
public final class SunkenTreasureReader {
	private static final String SCREEN_MARKER = "fishing weekend countdown";
	private static final String TREASURE_MARKER = "sunken treasure";
	private static final Pattern CRATES_LEFT = Pattern.compile(
			"(\\d+)\\s+crates?\\s+left", Pattern.CASE_INSENSITIVE);

	private SunkenTreasureReader() {
	}

	/** @return true if this was the /fw GUI (and the count was reconciled). */
	public static boolean tryRead(HandledScreen<?> screen) {
		ScreenHandler handler = screen.getScreenHandler();
		boolean isFishingWeekend = false;
		Integer crates = null;
		for (Slot slot : handler.slots) {
			ItemStack stack = slot.getStack();
			if (stack.isEmpty()) {
				continue;
			}
			String name = stack.getName().getString().toLowerCase(Locale.ROOT);
			if (name.contains(SCREEN_MARKER)) {
				isFishingWeekend = true;
			} else if (name.contains(TREASURE_MARKER)) {
				crates = cratesLeft(stack);
			}
		}
		if (!isFishingWeekend) {
			return false;
		}
		if (crates == null) {
			SunkenTreasureTracker.clear();
		} else {
			SunkenTreasureTracker.reconcile(crates);
		}
		return true;
	}

	/** The "N crates left!" figure from a stack's lore, or null if it says no such thing. */
	private static Integer cratesLeft(ItemStack stack) {
		LoreComponent lore = stack.get(DataComponentTypes.LORE);
		if (lore == null) {
			return null;
		}
		for (Text line : lore.lines()) {
			Integer crates = cratesLeft(line.getString());
			if (crates != null) {
				return crates;
			}
		}
		return null;
	}

	/** Minecraft-free seam: the figure in one lore line, or null if it isn't in there. */
	static Integer cratesLeft(String line) {
		Matcher matcher = CRATES_LEFT.matcher(line);
		if (!matcher.find()) {
			return null;
		}
		try {
			return Integer.parseInt(matcher.group(1));
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
