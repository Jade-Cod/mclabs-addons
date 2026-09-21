package dev.jade.labsaddons.crate;

import dev.jade.labsaddons.config.LabsAddonsConfig;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

import java.util.List;
import java.util.Map;

/**
 * Anchors the pity counters off the server's own odds menu, once per open.
 *
 * <p>Clicking a crate reward's {@code "┃ Click to view odds of each roll."} line opens
 * {@code "Your <Rarity> odds"} — nine {@code "Roll #N"} heads, on the page holding the player's
 * current roll. That page is the only place the server states where you are, so this reads the
 * lowest roll on it and hands that to {@link CratePity#anchored}, which keeps whichever of the
 * two numbers has more behind it.
 *
 * <p>Passive, like {@link VoteOddsReader}: no command is sent and no click is forwarded. A
 * player who never opens the menu simply gets a board that says so.
 */
public final class CratePityReader {
	/** The odds menu is a single chest, and the trailing 36 slots are the player's own. */
	private static final int PLAYER_SLOTS = 36;

	private CratePityReader() {
	}

	/** @return true if this was an odds menu that moved a counter. */
	public static boolean tryRead(HandledScreen<?> screen) {
		CrateRarity rarity = CratePity.oddsMenuRarity(screen.getTitle().getString());
		if (rarity == null) {
			return false;
		}
		int first = 0;
		ScreenHandler handler = screen.getScreenHandler();
		List<Slot> slots = handler.slots;
		for (int slot = 0; slot < slots.size() - PLAYER_SLOTS; slot++) {
			ItemStack stack = slots.get(slot).getStack();
			if (stack.isEmpty()) {
				continue;
			}
			int roll = CratePity.rollNumber(stack.getName().getString());
			if (roll > 0 && (first == 0 || roll < first)) {
				first = roll;
			}
		}
		if (first == 0) {
			// The title matched but nothing on it names a roll, so this is not the page — and
			// anchoring to nothing would move a good count for a bad one.
			return false;
		}
		LabsAddonsConfig config = LabsAddonsConfig.get();
		Map<String, Integer> next = CratePity.anchored(config.cratePityRolls, rarity, first);
		if (next == null) {
			return false;
		}
		config.cratePityRolls = next;
		config.save();
		return true;
	}
}
