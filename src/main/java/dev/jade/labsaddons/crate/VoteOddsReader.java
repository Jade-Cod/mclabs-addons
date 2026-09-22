package dev.jade.labsaddons.crate;

import dev.jade.labsaddons.config.LabsAddonsConfig;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Scrapes a voter crate's published odds, once per open, the same passive read as
 * {@link dev.jade.labsaddons.bounty.SunkenTreasureReader}.
 *
 * <p>Punching a voter crate opens a single nine-by-five menu listing all twenty-seven rewards
 * with a {@code "┃ Chance: X%"} line each. That is the only place those figures exist — the
 * roll itself shows three items and no odds whatsoever — so it is worth remembering for the
 * next time a three-way choice comes up.
 */
public final class VoteOddsReader {
	/** A voter crate's menu is five rows; its rewards occupy the middle three. */
	private static final int MENU_SLOTS = 45;
	private static final int FIRST_REWARD = 9;
	private static final int LAST_REWARD = 35;

	private VoteOddsReader() {
	}

	/** @return true if this was a voter crate's odds menu (and the table was relearned). */
	public static boolean tryRead(AbstractContainerScreen<?> screen) {
		String title = screen.getTitle().getString();
		if (!VoteOdds.isVoterCrate(title)) {
			return false;
		}
		AbstractContainerMenu handler = screen.getMenu();
		if (handler.slots.size() < MENU_SLOTS) {
			return false;
		}
		// A list, not a map: two rewards can share a display name, and collapsing them here
		// would hide that from the table rather than letting it decline to guess.
		List<VoteOddsEntry> chances = new ArrayList<>();
		for (int slot = FIRST_REWARD; slot <= LAST_REWARD; slot++) {
			ItemStack stack = handler.slots.get(slot).getItem();
			if (stack.isEmpty()) {
				continue;
			}
			List<String> lore = lore(stack);
			Double chance = VoteOdds.chanceIn(lore);
			if (chance != null) {
				chances.add(new VoteOddsEntry(title.trim(), stack.getHoverName().getString(), chance,
						VoteOdds.intrinsicLore(lore)));
			}
		}
		if (chances.isEmpty()) {
			// The title matched but nothing stated a chance, so this is not the odds menu and
			// wiping the table on it would throw away a good reading for a bad one.
			return false;
		}
		LabsAddonsConfig config = LabsAddonsConfig.get();
		config.voteCrateOdds = VoteOdds.relearn(config.voteCrateOdds, title.trim(), chances);
		config.save();
		return true;
	}

	private static List<String> lore(ItemStack stack) {
		ItemLore component = stack.get(DataComponents.LORE);
		if (component == null) {
			return List.of();
		}
		List<Component> lines = component.lines();
		List<String> out = new ArrayList<>(lines.size());
		for (Component line : lines) {
			out.add(line.getString());
		}
		return out;
	}
}
