package dev.jade.labsaddons.double2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LabTest {
	@Test
	void readsEveryFormTheServerPrints() {
		assertEquals(Lab.CQL, Lab.fromText("■ Crimson Quadri Lab (2.0x Profit)"));
		assertEquals(Lab.EOL, Lab.fromText("● Emerald Orb Lab"));
		assertEquals(Lab.ADL, Lab.fromText("ADL"));
		assertEquals(Lab.EOL, Lab.fromText("Investing in EOL."));
		assertEquals(Lab.CQL, Lab.fromText("Your investment in cql profited you $1,940!"));
		assertEquals(Lab.RDL, Lab.fromText("Select RDL."));
	}

	@Test
	void doesNotMatchACodeBuriedInAWord() {
		assertNull(Lab.fromText("SCADLING"));
		assertNull(Lab.fromText("Nobody profited."));
		assertNull(Lab.fromText(""));
		assertNull(Lab.fromText(null));
	}

	@Test
	void exactCodeOnlyMatchesTheBannerForm() {
		assertEquals(Lab.ADL, Lab.fromExactCode("ADL"));
		assertEquals(Lab.MSL, Lab.fromExactCode(" msl "));
		assertNull(Lab.fromExactCode("ADL Lab"));
		assertNull(Lab.fromExactCode("Total"));
	}

	@Test
	void multiplierTextMatchesTheLore() {
		assertEquals("2.0x", Lab.CQL.multiplierText());
		assertEquals("11.0x", Lab.RDL.multiplierText());
		assertEquals("24.0x", Lab.EOL.multiplierText());
	}
}
