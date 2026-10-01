package dev.jade.labsaddons.carnage;

import dev.jade.labsaddons.carnage.CarnageBooster.State;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The two broadcasts seen on 2026-10-01, four minutes apart. */
public class CarnageBoosterTest {
	private static final long MINUTE = 60_000L;
	private static final long NOW = 1_000_000_000L;
	private static final String ACTIVATED = "\nCarnage » Spidrr has just activated a Halloween Carnage Booster!\n"
			+ "All Carnage Points and Soul of Fright drop rates are boosted by 1.5x for 60 minutes! "
			+ "Earn Souls of Fright by fighting spooky mobs at Spawn!\n";
	private static final String REMINDER_ZERO = "\nCarnage » A Halloween Carnage Booster is active, sponsored by Spidrr! "
			+ "All Soul of Fright drop chances are boosted 1.5x for 0 minutes. Earn Souls of Fright by fighting spooky mobs in Spawn.\n";

	@Test
	public void anActivationRunsForItsStatedTime() {
		State state = CarnageBooster.next(null, ACTIVATED, NOW);
		assertEquals(1.5, state.multiplier());
		assertEquals(NOW + 60 * MINUTE, state.endMs());
	}

	@Test
	public void aZeroMinuteReminderKeepsTheTimer() {
		State running = CarnageBooster.next(null, ACTIVATED, NOW);
		State after = CarnageBooster.next(running, REMINDER_ZERO, NOW + 4 * MINUTE);
		assertEquals(NOW + 60 * MINUTE, after.endMs());
	}

	@Test
	public void aReminderWithMinutesResetsTheTimer() {
		State running = CarnageBooster.next(null, ACTIVATED, NOW);
		State after = CarnageBooster.next(running, REMINDER_ZERO.replace("for 0 minutes", "for 50 minutes"), NOW + 4 * MINUTE);
		assertEquals(NOW + 54 * MINUTE, after.endMs());
	}

	@Test
	public void aSecondActivationExtends() {
		State running = CarnageBooster.next(null, ACTIVATED, NOW);
		State extended = CarnageBooster.next(running, ACTIVATED, NOW + 10 * MINUTE);
		assertEquals(NOW + 120 * MINUTE, extended.endMs());
	}

	@Test
	public void aReminderAloneIsActiveWithNoTimerUntilItGoesQuiet() {
		State seen = CarnageBooster.next(null, REMINDER_ZERO, NOW);
		assertEquals(0, seen.endMs());
		assertTrue(CarnageBooster.isActive(seen, NOW + 5 * MINUTE));
		assertFalse(CarnageBooster.isActive(seen, NOW + CarnageBooster.NO_TIMER_STALE_MS));
	}

	@Test
	public void aPlayerQuotingItMovesNothing() {
		assertNull(CarnageBooster.next(null, "[VIP] Bob: Carnage » Bob has just activated a Halloween Carnage Booster! boosted by 1.5x for 60 minutes", NOW));
	}
}
