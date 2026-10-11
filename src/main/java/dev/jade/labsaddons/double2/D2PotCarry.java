package dev.jade.labsaddons.double2;

import java.util.Map;

/**
 * The settled banner carries no pot or investor lines, so for the two seconds the result
 * is up the board would read "pot $0 · 0 investing". This holds what the spin showed and
 * lends it to the settled frames. Cleared when betting reopens or the container changes.
 */
final class D2PotCarry {
	private Map<Lab, Long> pot = Map.of();
	private int investors;

	D2State apply(D2State state) {
		switch (state.phase()) {
			case BETTING -> clear();
			case SPINNING -> {
				if (state.potTotal() > 0 || state.investors() > 0) {
					pot = state.pot();
					investors = state.investors();
				}
			}
			case SETTLED -> {
				if (state.potTotal() == 0 && state.investors() == 0 && !pot.isEmpty()) {
					return new D2State(state.phase(), state.secondsLeft(), state.selected(),
							state.stake(), state.betLab(), state.betAmount(), pot, investors,
							state.window(), state.settledLab(), state.eolDrought());
				}
			}
		}
		return state;
	}

	void clear() {
		pot = Map.of();
		investors = 0;
	}
}
