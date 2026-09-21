package dev.jade.labsaddons.crate;

import dev.jade.labsaddons.config.LabsAddonsConfig;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.Component;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.ComponentType;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Anchors the pity counters off the server's own odds menu, once per open.
 *
 * <p>Clicking a crate reward's {@code "┃ Click to view odds of each roll."} line opens
 * {@code "Your <Rarity> odds"} — nine {@code "Roll #N"} heads, on the page holding the player's
 * current roll, and that page is the only place the server states where you are. It states it
 * exactly: a roll already spent is drawn as an opened crate and one still ahead as a closed
 * one, so the boundary between the two is the roll about to happen.
 *
 * <p>What is compared is every component of the head except the two that differ on each of
 * them anyway — its name ({@code "Roll #219"}) and its lore (the chance). Whatever carries the
 * difference is therefore picked up without this having to know which component it is, and a
 * page where every head differs is read as saying nothing rather than as a boundary.
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
		Map<Integer, String> page = new LinkedHashMap<>();
		ScreenHandler handler = screen.getScreenHandler();
		List<Slot> slots = handler.slots;
		for (int slot = 0; slot < slots.size() - PLAYER_SLOTS; slot++) {
			ItemStack stack = slots.get(slot).getStack();
			if (stack.isEmpty()) {
				continue;
			}
			int roll = CratePity.rollNumber(stack.getName().getString());
			if (roll > 0) {
				page.put(roll, mark(stack));
			}
		}
		int current = CratePity.currentRoll(page);
		if (current <= 0) {
			// Either not the odds page at all, or one the player has paged away to, where the
			// heads are all of a kind and nothing marks where they are. Moving a good count on
			// the strength of that would be worse than leaving it.
			return false;
		}
		LabsAddonsConfig config = LabsAddonsConfig.get();
		Map<String, Integer> next = CratePity.anchored(config.cratePityRolls, rarity, current);
		if (next == null) {
			return false;
		}
		config.cratePityRolls = next;
		config.save();
		return true;
	}

	/**
	 * How this head is drawn, with the parts that say which roll it is left out.
	 *
	 * <p>Sorted, because a component map's iteration order is its own business and two heads
	 * drawn the same way have to key the same. The keys are only ever compared with each other,
	 * so what is in them past that does not matter.
	 */
	private static String mark(ItemStack stack) {
		Set<String> parts = new TreeSet<>();
		for (Component<?> component : stack.getComponents()) {
			ComponentType<?> type = component.type();
			if (type == DataComponentTypes.CUSTOM_NAME || type == DataComponentTypes.ITEM_NAME
					|| type == DataComponentTypes.LORE) {
				continue;
			}
			parts.add(String.valueOf(component));
		}
		return String.join("|", parts);
	}
}
