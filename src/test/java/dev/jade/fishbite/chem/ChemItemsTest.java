package dev.jade.fishbite.chem;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ChemItemsTest {

	@Test
	public void testParseLabelWithPurity() {
		ChemItems.ChemKey key = ChemItems.parseLabel("Chowartusite-2-2-2");
		assertEquals("chowartusite", key.chem());
		assertEquals("2-2-2", key.purity());
	}

	@Test
	public void testParseLabelWithoutPurity() {
		ChemItems.ChemKey key = ChemItems.parseLabel("Wheatium");
		assertEquals("wheatium", key.chem());
		assertEquals("", key.purity());
	}

	@Test
	public void testWithdrawArg() {
		ChemItems.ChemKey keyWithPurity = new ChemItems.ChemKey("chowartusite", "2-2-2");
		assertEquals("chowartusite|2-2-2", ChemItems.withdrawArg(keyWithPurity));

		ChemItems.ChemKey keyNoPurity = new ChemItems.ChemKey("wheatium", "");
		assertEquals("wheatium|0-0-0", ChemItems.withdrawArg(keyNoPurity));
	}

	@Test
	public void testDisplayLabel() {
		ChemItems.ChemKey keyWithPurity = new ChemItems.ChemKey("chowartusite", "2-2-2");
		assertEquals("Chowartusite-2-2-2", ChemItems.displayLabel(keyWithPurity));

		ChemItems.ChemKey keyNoPurity = new ChemItems.ChemKey("wheatium", "");
		assertEquals("Wheatium", ChemItems.displayLabel(keyNoPurity));
	}
}
