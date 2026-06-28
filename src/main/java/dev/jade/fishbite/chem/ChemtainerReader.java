package dev.jade.fishbite.chem;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
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
 * Scrapes the main Chemtainer GUI ({@code /ch} or {@code /c}) once when the player
 * opens it — the same passive once-per-open pattern as
 * {@link dev.jade.fishbite.booster.BoosterRatesReader}. The GUI is an authoritative
 * snapshot: each stored chem is an item named "&lt;Chem&gt;-p-p-p x&lt;count&gt;"
 * (e.g. "Chowartusite-2-2-2 x8704") carrying {@code custom_data} {chem, purity}.
 * The "Deposit Chems" head confirms it really is the Chemtainer, so an empty
 * container reads as truly empty rather than "unknown".
 *
 * <p>Fail-soft (per the design council): a slot that doesn't match the expected
 * format is skipped, never guessed; if the "Deposit Chems" head is absent we treat
 * the screen as not-the-Chemtainer and leave the last-good snapshot untouched.
 */
public final class ChemtainerReader {
	private static final String DEPOSIT_HEAD = "deposit chems";
	private static final Pattern COUNT = Pattern.compile("x([\\d,]+)\\s*$");

	private ChemtainerReader() {
	}

	/** @return true if this was the Chemtainer GUI (and the snapshot was refreshed). */
	public static boolean tryRead(HandledScreen<?> screen) {
		ScreenHandler handler = screen.getScreenHandler();
		boolean isChemtainer = false;
		List<ChemtainerEntry> entries = new ArrayList<>();

		for (Slot slot : handler.slots) {
			ItemStack stack = slot.getStack();
			if (stack.isEmpty()) {
				continue;
			}
			String name = nameString(stack);
			if (name.toLowerCase(Locale.ROOT).contains(DEPOSIT_HEAD)) {
				isChemtainer = true;
				continue;
			}
			ChemtainerEntry entry = parse(stack, name);
			if (entry != null) {
				entries.add(entry);
			}
		}

		if (!isChemtainer) {
			return false;
		}
		ChemtainerTracker.snapshot(entries);
		return true;
	}

	/** "Chowartusite-2-2-2 x8704" -> entry, or null if it isn't a stored-chem slot. */
	private static ChemtainerEntry parse(ItemStack stack, String name) {
		Matcher matcher = COUNT.matcher(name);
		if (!matcher.find()) {
			return null;
		}
		long count;
		try {
			count = Long.parseLong(matcher.group(1).replace(",", ""));
		} catch (NumberFormatException e) {
			return null;
		}
		String label = name.substring(0, matcher.start()).trim();
		// Prefer the authoritative chem+purity NBT; fall back to parsing the label.
		ChemItems.ChemKey key = ChemItems.isChem(stack) ? ChemItems.keyOf(stack) : ChemItems.parseLabel(label);
		return new ChemtainerEntry(key.chem(), key.purity(), label, count);
	}

	private static String nameString(ItemStack stack) {
		Text name = stack.get(DataComponentTypes.CUSTOM_NAME);
		return name != null ? name.getString() : stack.getName().getString();
	}
}
