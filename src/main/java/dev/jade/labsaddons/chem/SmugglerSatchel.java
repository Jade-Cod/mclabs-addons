package dev.jade.labsaddons.chem;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;

import java.util.Locale;

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
		for (Slot slot : screen.getScreenHandler().slots) {
			// The player's own inventory is mirrored into the bottom of every chest
			// GUI; only the satchel's own slots say what the satchel holds.
			if (slot.inventory instanceof PlayerInventory) {
				continue;
			}
			ItemStack stack = slot.getStack();
			if (!ChemItems.isChem(stack) || stack.getCount() <= largestCount) {
				continue;
			}
			largest = ChemItems.keyOf(stack);
			largestCount = stack.getCount();
		}
		// An empty satchel clears the memory: crediting its old chem after it has been
		// emptied would attribute the next sale's remainder to the wrong challenge.
		contents = largest;
		return true;
	}

	/** The chem the satchel is loaded with, or null if we have never seen it open. */
	public static ChemItems.ChemKey contents() {
		return contents;
	}

	public static void reset() {
		contents = null;
	}
}
