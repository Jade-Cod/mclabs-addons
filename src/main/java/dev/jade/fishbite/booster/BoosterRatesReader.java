package dev.jade.fishbite.booster;

import dev.jade.fishbite.chem.ChemIcons;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the "/chems → Booster(s) active!" chest GUI when the player opens it, so a
 * player who joins mid-booster (after the activation chat scrolled past) still tracks
 * it — the same way {@link dev.jade.fishbite.labwars.LabWarsRatesReader} scrapes
 * /lw rates. Purely passive: never sends commands or closes the screen.
 *
 * <p>Each active booster is one item named "&lt;Chem&gt; Price Booster" (or "All Chem
 * Price Booster") with lore "Boost: 1.2x" / "Boosted by &lt;player&gt;" / "Time left:
 * 13m:53s" and a {@code custom_model_data} string equal to the chem key.
 */
public final class BoosterRatesReader {
	private static final Pattern BOOST = Pattern.compile("Boost:\\s*([0-9]+(?:\\.[0-9]+)?)x", Pattern.CASE_INSENSITIVE);
	private static final Pattern TIME_LEFT = Pattern.compile("Time left:\\s*(.+)", Pattern.CASE_INSENSITIVE);
	private static final String BOOSTER_NAME = "price booster";

	private BoosterRatesReader() {
	}

	/** @return true if this looks like the active-chem-boosters GUI. */
	public static boolean tryRead(HandledScreen<?> screen) {
		ScreenHandler handler = screen.getScreenHandler();
		boolean looksLikeBoosters = false;

		for (Slot slot : handler.slots) {
			ItemStack stack = slot.getStack();
			if (stack.isEmpty() || !nameString(stack).toLowerCase(Locale.ROOT).contains(BOOSTER_NAME)) {
				continue;
			}
			List<String> lore = loreStrings(stack);
			double multiplier = firstMatch(lore, BOOST);
			long remainingMs = remaining(lore);
			if (multiplier <= 0 || remainingMs <= 0) {
				continue;
			}
			looksLikeBoosters = true;
			BoosterTracker.track(itemName(stack), multiplier, System.currentTimeMillis() + remainingMs);
		}
		return looksLikeBoosters;
	}

	/** Prefer the server model-data string (exact chem key); fall back to the display name. */
	private static String itemName(ItemStack stack) {
		CustomModelDataComponent model = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
		if (model != null && !model.strings().isEmpty()) {
			String key = model.strings().get(0);
			return ChemIcons.isAllBooster(key) ? "All Chems" : key;
		}
		// "Glocobinide Price Booster" -> "Glocobinide"
		return nameString(stack).replaceAll("(?i)\\s*price booster\\s*$", "").trim();
	}

	private static double firstMatch(List<String> lore, Pattern pattern) {
		for (String line : lore) {
			Matcher matcher = pattern.matcher(line);
			if (matcher.find()) {
				return Double.parseDouble(matcher.group(1));
			}
		}
		return 0;
	}

	private static long remaining(List<String> lore) {
		for (String line : lore) {
			Matcher matcher = TIME_LEFT.matcher(line);
			if (matcher.find()) {
				return BoosterTracker.parseDurationMs(matcher.group(1));
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
