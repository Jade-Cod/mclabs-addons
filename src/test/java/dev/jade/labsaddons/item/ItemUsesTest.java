package dev.jade.labsaddons.item;

import net.minecraft.world.item.component.ItemLore;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The remaining-uses overlay is asked for every slot the game draws, every frame. A profile
 * with a chest open had it at 4.5% of the render thread, all of it re-deriving answers that had
 * not changed.
 *
 * <p>These count parses rather than milliseconds, because a parse count is the thing that
 * actually changed and it does not vary with the machine it runs on.
 */
class ItemUsesTest {
	/** A container plus the hotbar: what is on screen when the overlay costs the most. */
	private static final int SLOTS = 90;
	private static final int FRAMES = 60;

	@BeforeEach
	void forget() {
		ItemUses.forget();
	}

	private static ItemLore lore(String... lines) {
		List<Component> text = new ArrayList<>();
		for (String line : lines) {
			text.add(Component.literal(line));
		}
		return new ItemLore(text);
	}

	@Test
	void aChargeCountIsStillReadCorrectly() {
		assertEquals(3, ItemUses.charges(lore("Smoke Bomb", "Charges: 3", "Right-click to use")));
		assertEquals(12, ItemUses.charges(lore("Charges: 12")));
		// Case-insensitive, and whitespace either side of the colon is the server's to vary.
		assertEquals(7, ItemUses.charges(lore("charges:7")));
	}

	@Test
	void loreWithNoChargesReadsAsNothing() {
		assertEquals(-1, ItemUses.charges(lore("Just a sword", "+5 Sharpness")));
		assertEquals(-1, ItemUses.charges(lore()));
	}

	@Test
	void anOverlongDigitRunFailsSoftRatherThanMidRender() {
		assertEquals(-1, ItemUses.charges(lore("Charges: 99999999999999999999")));
	}

	@Test
	void aSlotHeldStillIsParsedOnceNoMatterHowLongItIsOnScreen() {
		// The whole point. One component, sixty frames.
		ItemLore held = lore("Smelling Salts", "Charges: 2");
		for (int frame = 0; frame < FRAMES; frame++) {
			assertEquals(2, ItemUses.charges(held));
		}
		assertEquals(1, ItemUses.reads(), "one parse for sixty frames of the same slot");
	}

	@Test
	void aScreenfulOfSlotsCostsOneParseEachPerScreenNotPerFrame() {
		List<ItemLore> onScreen = new ArrayList<>();
		for (int slot = 0; slot < SLOTS; slot++) {
			// Distinct instances, as ninety real slots would be.
			onScreen.add(lore("Item " + slot, "Charges: " + (slot % 9 + 1)));
		}
		for (int frame = 0; frame < FRAMES; frame++) {
			for (ItemLore slot : onScreen) {
				ItemUses.charges(slot);
			}
		}
		// Before the memo this was SLOTS * FRAMES = 5,400 lore flattens and regexes a second.
		assertEquals(SLOTS, ItemUses.reads(),
				"ninety parses for ninety slots, not ninety per frame");
		assertEquals(SLOTS * FRAMES / SLOTS, FRAMES);
	}

	@Test
	void theCommonCaseOfNoChargesAtAllIsRememberedToo() {
		// Most items have lore and no charge line. If that answer were not remembered the
		// memo would miss on exactly the items there are most of.
		ItemLore plain = lore("A Deluxe Voter Sword", "Sharpness V", "Unbreaking III");
		for (int frame = 0; frame < FRAMES; frame++) {
			assertEquals(-1, ItemUses.charges(plain));
		}
		assertEquals(1, ItemUses.reads());
	}

	@Test
	void newLoreOnTheSameSlotIsReadAgain() {
		// Spending a charge replaces the component rather than editing it, which is what makes
		// identity a safe key: a changed slot arrives as a different object and misses.
		assertEquals(3, ItemUses.charges(lore("Charges: 3")));
		assertEquals(2, ItemUses.charges(lore("Charges: 2")));
		assertEquals(1, ItemUses.charges(lore("Charges: 1")));
		assertEquals(3, ItemUses.reads());
	}

	@Test
	void theMemoIsBounded() {
		for (int i = 0; i < 2_000; i++) {
			ItemUses.charges(lore("Charges: " + (i % 9 + 1), "unique " + i));
		}
		assertTrue(ItemUses.remembered() <= 512,
				"held " + ItemUses.remembered() + " answers; the cap is 512");
		// Every one of those was a distinct component, so every one had to be parsed.
		assertEquals(2_000, ItemUses.reads());
	}
}
