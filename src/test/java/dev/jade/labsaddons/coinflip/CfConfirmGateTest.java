package dev.jade.labsaddons.coinflip;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CfConfirmGateTest {
	@Test
	void aDoubleClickIsNotAConfirmation() {
		assertFalse(CfConfirmGate.mayConfirm(1_100, 1_000, false));
	}

	@Test
	void aHeldKeyIsNotAConfirmationEvenAfterTheGap() {
		assertFalse(CfConfirmGate.mayConfirm(3_000, 1_000, true));
	}

	@Test
	void aDeliberateSecondPressConfirms() {
		assertTrue(CfConfirmGate.mayConfirm(1_000 + CfConfirmGate.MIN_GAP_MS, 1_000, false));
	}
}
