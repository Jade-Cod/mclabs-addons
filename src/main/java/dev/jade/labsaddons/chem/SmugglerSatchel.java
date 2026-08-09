package dev.jade.labsaddons.chem;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Remembers what a Smuggler Satchel is loaded with, read passively the moment the
 * player opens it — never by opening it ourselves, per the mod's GUI rule.
 *
 * <p>Selling to a dealer empties the satchel alongside the inventory, but only the
 * inventory half is visible to a client-side diff. The satchel's <em>count</em> can
 * be recovered arithmetically (the server's "sold N chems" total minus what left the
 * inventory); its <em>identity</em> cannot, and that is what this holds.
 *
 * <p>The server allows exactly one chem with one attribute set per satchel, so a
 * single {@link ChemItems.ChemKey} describes the whole contents and stays valid until
 * the player loads something else. That is why one read per fill is enough — and why
 * the largest stack is safe to trust: every real stack shares the one key, so a lone
 * decorative item can never outweigh them.
 */
public final class SmugglerSatchel {
	/** Matched against the screen title; the GUI is a plain chest named for the item. */
	private static final String TITLE_MARKER = "satchel";

	/** "MCLabs » 1,152x Cactatonate-2-2-2 has been loaded into your Smuggler Satchel." */
	private static final Pattern LOADED = Pattern.compile(
			"[\\d,]+x\\s+(\\S+)\\s+has been loaded into your Smuggler Satchel",
			Pattern.CASE_INSENSITIVE);

	/** Trailing " x1,152" on a summarised display item, which is not part of the label. */
	private static final Pattern COUNT_SUFFIX = Pattern.compile("\\s*x[\\d,]+\\s*$");

	private static volatile ChemItems.ChemKey contents;

	private SmugglerSatchel() {
	}

	/** @return true if this was the satchel GUI (and the remembered contents were refreshed). */
	public static boolean tryRead(HandledScreen<?> screen) {
		if (!screen.getTitle().getString().toLowerCase(Locale.ROOT).contains(TITLE_MARKER)) {
			return false;
		}
		ChemItems.ChemKey largest = null;
		int largestCount = 0;
		boolean anyItem = false;
		for (Slot slot : screen.getScreenHandler().slots) {
			// The player's own inventory is mirrored into the bottom of every chest
			// GUI; only the satchel's own slots say what the satchel holds.
			if (slot.inventory instanceof PlayerInventory) {
				continue;
			}
			ItemStack stack = slot.getStack();
			if (stack.isEmpty()) {
				continue;
			}
			anyItem = true;
			if (stack.getCount() < largestCount) {
				continue;
			}
			ChemItems.ChemKey key = chemOf(stack);
			if (key != null) {
				largest = key;
				largestCount = stack.getCount();
			}
		}
		if (largest != null) {
			contents = largest;
		} else if (!anyItem) {
			// Genuinely empty: forget the old chem, or the next sale's remainder would
			// be credited to a challenge the satchel no longer holds anything for.
			// Items we merely failed to parse are left alone rather than treated as
			// empty — that would discard what the load message already told us.
			contents = null;
		}
		return true;
	}

	/**
	 * Learns the contents from the server's own load confirmation, which names the chem
	 * and its purity outright. This is the path that actually fires in practice: loading
	 * a satchel by command never opens its GUI, so waiting for {@link #tryRead} left the
	 * satchel's share of a sale uncredited.
	 */
	public static void onMessage(String text) {
		if (text == null) {
			return;
		}
		Matcher loaded = LOADED.matcher(text);
		if (loaded.find()) {
			contents = ChemItems.parseLabel(loaded.group(1));
		}
	}

	/**
	 * The chem a satchel slot holds, or null if it isn't one. Prefers the authoritative
	 * NBT and falls back to the label, the same way {@link ChemtainerReader} does — a
	 * summarised "Cactatonate-2-2-2 x1152" display item carries no chem component of
	 * its own, so NBT alone would read the satchel as empty.
	 */
	private static ChemItems.ChemKey chemOf(ItemStack stack) {
		if (ChemItems.isChem(stack)) {
			return ChemItems.keyOf(stack);
		}
		Text custom = stack.get(DataComponentTypes.CUSTOM_NAME);
		String label = custom != null ? custom.getString() : stack.getName().getString();
		ChemItems.ChemKey parsed = ChemItems.parseLabel(COUNT_SUFFIX.matcher(label).replaceAll("").trim());
		return parsed.chem().isEmpty() ? null : parsed;
	}

	/** The chem the satchel is loaded with, or null if we have never seen it open. */
	public static ChemItems.ChemKey contents() {
		return contents;
	}

	public static void reset() {
		contents = null;
	}
}
