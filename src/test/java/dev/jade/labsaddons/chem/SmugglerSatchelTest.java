package dev.jade.labsaddons.chem;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Load lines are verbatim from the server. */
public class SmugglerSatchelTest {
	@AfterEach
	public void reset() {
		SmugglerSatchel.reset();
	}

	/** The line that actually fires: loading by command never opens the satchel's GUI. */
	@Test
	public void aLoadConfirmationNamesTheChemAndItsPurity() {
		SmugglerSatchel.onMessage(
				"MCLabs » 1,152x Cactatonate-2-2-2 has been loaded into your Smuggler Satchel.");
		assertEquals(new ChemItems.ChemKey("cactatonate", "2-2-2"), SmugglerSatchel.contents());
	}

	/** Reloading with a different chem replaces the identity rather than keeping the old one. */
	@Test
	public void aSecondLoadReplacesTheFirst() {
		SmugglerSatchel.onMessage("MCLabs » 64x Chowartusite-2-2-2 has been loaded into your Smuggler Satchel.");
		SmugglerSatchel.onMessage("MCLabs » 1,152x Chorberrium-3-3-3 has been loaded into your Smuggler Satchel.");
		assertEquals(new ChemItems.ChemKey("chorberrium", "3-3-3"), SmugglerSatchel.contents());
	}

	/** The withdraw line names the same chem but says nothing about the satchel. */
	@Test
	public void unrelatedChemLinesAreIgnored() {
		SmugglerSatchel.onMessage("MCLabs » Withdrew 2,112 Cactatonate-2-2-2 from your chemtainer.");
		SmugglerSatchel.onMessage("» You've sold 3,264 chems for $107k (5% company tax)");
		SmugglerSatchel.onMessage(null);
		assertNull(SmugglerSatchel.contents(), "nothing has been loaded, so nothing is known");
	}
}
