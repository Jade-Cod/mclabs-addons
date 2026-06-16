package dev.jade.fishbite.labwars;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the /lw rates chest GUI when the player opens it. Purely passive: never
 * sends commands or closes the screen.
 *
 * <p>Each category item stacks several boost blocks (e.g. "Revenue Shop Boost",
 * "Goal Boost Event", "&lt;player&gt;'s Booster", and "Lab Goal Boost"), each a
 * header line followed by "Multiplier:" / "Remaining:". We capture the first
 * block that is NOT the lab-wide daily-goal boost — that's the chat-announced
 * revenue booster — and read its own multiplier (not the combined Current Rate,
 * which folds in chum and the lab-goal boost).
 */
public final class LabWarsRatesReader {
	private static final Pattern CURRENT_RATE = Pattern.compile("Current Rate:", Pattern.CASE_INSENSITIVE);
	private static final Pattern MULTIPLIER = Pattern.compile("Multiplier:\\s*([0-9.]+)x", Pattern.CASE_INSENSITIVE);
	private static final Pattern REMAINING = Pattern.compile("Remaining:\\s*(.+)", Pattern.CASE_INSENSITIVE);
	private static final String LAB_GOAL = "lab goal";

	private LabWarsRatesReader() {
	}

	/** @return true if this looks like the /lw rates GUI. */
	public static boolean tryRead(HandledScreen<?> screen) {
		ScreenHandler handler = screen.getScreenHandler();
		boolean looksLikeRates = false;

		for (Slot slot : handler.slots) {
			ItemStack stack = slot.getStack();
			if (stack.isEmpty()) {
				continue;
			}
			List<String> lore = loreStrings(stack);
			if (lore.stream().noneMatch(line -> CURRENT_RATE.matcher(line).find())) {
				continue;
			}
			looksLikeRates = true;

			String key = LabWarsType.keyOf(nameString(stack));
			if (key != null) {
				LabWarsTracker.setCategory(key, readBoosters(key, lore));
			}
		}
		return looksLikeRates;
	}

	/** Every non-lab-goal boost block for this category (boosters stack). */
	private static List<LabWarsBooster> readBoosters(String key, List<String> lore) {
		List<LabWarsBooster> boosters = new ArrayList<>();
		for (int i = 0; i < lore.size(); i++) {
			Matcher mult = MULTIPLIER.matcher(lore.get(i));
			if (!mult.find()) {
				continue;
			}
			if (headerAbove(lore, i).toLowerCase(Locale.ROOT).contains(LAB_GOAL)) {
				continue;
			}
			long remainingMs = remainingNear(lore, i);
			if (remainingMs > 0) {
				boosters.add(new LabWarsBooster(key, Double.parseDouble(mult.group(1)),
						System.currentTimeMillis() + remainingMs, true));
			}
		}
		return boosters;
	}

	/** Nearest non-blank line above index i that isn't itself a Multiplier/Remaining line. */
	private static String headerAbove(List<String> lore, int i) {
		for (int j = i - 1; j >= Math.max(0, i - 3); j--) {
			String line = lore.get(j).trim();
			if (!line.isEmpty() && !MULTIPLIER.matcher(line).find() && !REMAINING.matcher(line).find()) {
				return line;
			}
		}
		return "";
	}

	private static long remainingNear(List<String> lore, int i) {
		for (int k = i; k < Math.min(lore.size(), i + 3); k++) {
			Matcher remaining = REMAINING.matcher(lore.get(k));
			if (remaining.find()) {
				return LabWarsDuration.parseMs(remaining.group(1));
			}
		}
		return 0;
	}

	private static String nameString(ItemStack stack) {
		Text name = stack.get(DataComponentTypes.CUSTOM_NAME);
		return name != null ? name.getString() : stack.getName().getString();
	}

	private static List<String> loreStrings(ItemStack stack) {
		LoreComponent lore = stack.get(DataComponentTypes.LORE);
		if (lore == null) {
			return List.of();
		}
		return lore.lines().stream().map(Text::getString).toList();
	}
}
