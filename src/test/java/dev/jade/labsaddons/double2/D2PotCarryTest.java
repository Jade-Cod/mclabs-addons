package dev.jade.labsaddons.double2;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class D2PotCarryTest {
	private static D2State frame(D2State.Phase phase, Map<Lab, Long> pot, int investors) {
		return new D2State(phase, -1, null, 0, null, 0, pot, investors, List.of(), null, -1);
	}

	@Test
	void settledFramesKeepTheSpinsPotUntilBettingReopens() {
		D2PotCarry carry = new D2PotCarry();
		carry.apply(frame(D2State.Phase.SPINNING, Map.of(Lab.EOL, 500L), 3));
		D2State settled = carry.apply(frame(D2State.Phase.SETTLED, Map.of(), 0));
		assertEquals(500L, settled.potTotal());
		assertEquals(3, settled.investors());

		carry.apply(frame(D2State.Phase.BETTING, Map.of(), 0));
		assertEquals(0L, carry.apply(frame(D2State.Phase.SETTLED, Map.of(), 0)).potTotal());
	}
}
