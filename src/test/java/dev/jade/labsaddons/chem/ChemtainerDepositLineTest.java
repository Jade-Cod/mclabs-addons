package dev.jade.labsaddons.chem;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ChemtainerDepositLineTest {
	@Test
	public void personalDepositMatches() {
		assertTrue(ChemtainerTracker.DEPOSITED.matcher("Deposited 1,723 chems into your chemtainer.").find());
	}

	@Test
	public void companyDepositIsIgnored() {
		assertFalse(ChemtainerTracker.DEPOSITED
				.matcher("Companies » Deposited 1,723 chems into company chemtainer.").find());
	}
}
