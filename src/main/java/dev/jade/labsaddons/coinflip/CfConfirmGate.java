package dev.jade.labsaddons.coinflip;

/** Whether a second CREATE press counts as a confirmation or as the same gesture repeating. */
final class CfConfirmGate {
	/** A double-click lands well inside this; a person reading the confirm state does not. */
	static final long MIN_GAP_MS = 400;

	private CfConfirmGate() {
	}

	/**
	 * @param keyStillHeld the key that armed the step has not been released since (key repeats
	 *                     and a held Enter both look like fresh presses to the screen)
	 */
	static boolean mayConfirm(long nowMs, long armedAtMs, boolean keyStillHeld) {
		return !keyStillHeld && nowMs - armedAtMs >= MIN_GAP_MS;
	}
}
