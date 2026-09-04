package dev.jade.labsaddons.mastery;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Mob tags are verbatim Pit name tags: the mob name, or a heart bar mid-fight. */
public class MasteryKillTrackerTest {
	private static final String KILL_ARCHER = "Kill Petrified Archer";
	private static final String HEARTS = "❤❤❤❤";
	private static final long T = 1_000_000L;

	@AfterEach
	public void reset() {
		MasteryTracker.clear();
		MasteryGains.clear();
		MasteryKillTracker.reset();
	}

	private static void active(String... names) {
		MasteryTracker.setQuests(java.util.Arrays.stream(names)
				.map(n -> new MasteryQuest(null, n, 0, 100, 0))
				.toList());
	}

	private static Map<String, String> targets() {
		return MasteryKillTracker.activeKillTargets();
	}

	private static double currentOf(String name) {
		return MasteryTracker.quests().stream()
				.filter(q -> q.name().equals(name))
				.findFirst().orElseThrow().current();
	}

	@Test
	public void activeKillTargetsStripsPrefixAndIgnoresNonKillQuests() {
		active(KILL_ARCHER, "Win Chat Reactions", "Sell to Red Dealer");
		assertEquals(Map.of("petrified archer", KILL_ARCHER), targets());
	}

	@Test
	public void aSightingThenDeathCreditsTheKill() {
		active(KILL_ARCHER);
		assertFalse(MasteryKillTracker.observe(1, "Petrified Archer", false, targets(), T), "alive: no credit yet");
		MasteryKillTracker.onPlayerHit(1, T);
		assertTrue(MasteryKillTracker.observe(1, "Petrified Archer", true, targets(), T), "death credits");
		assertEquals(1, currentOf(KILL_ARCHER));
	}

	/** The bug: a mob someone else fought and killed next to you is not your kill. */
	@Test
	public void aMobWeNeverHitIsNotCredited() {
		active(KILL_ARCHER);
		MasteryKillTracker.observe(1, "Petrified Archer", false, targets(), T);
		assertFalse(MasteryKillTracker.observe(1, "Petrified Archer", true, targets(), T),
				"another player's kill earns us nothing");
		assertEquals(0, currentOf(KILL_ARCHER));
	}

	/** Tagged it once, wandered off, someone else finished it much later — not ours. */
	@Test
	public void aStaleHitIsNotTheKillingBlow() {
		active(KILL_ARCHER);
		MasteryKillTracker.onPlayerHit(1, T);
		assertFalse(MasteryKillTracker.observe(1, "Petrified Archer", true, targets(),
						T + MasteryKillTracker.LAST_HIT_WINDOW_MS + 1),
				"our last hit is too old to be the killing blow");
		assertEquals(0, currentOf(KILL_ARCHER));
	}

	/** Hitting a mob does not credit anything on its own — it still has to die. */
	@Test
	public void aHitWithoutADeathCreditsNothing() {
		active(KILL_ARCHER);
		MasteryKillTracker.onPlayerHit(1, T);
		assertFalse(MasteryKillTracker.observe(1, "Petrified Archer", false, targets(), T));
		assertEquals(0, currentOf(KILL_ARCHER));
	}

	/** The death animation lingers ~20 ticks, all reporting dead — count the kill once. */
	@Test
	public void deathIsCreditedOnlyOnce() {
		active(KILL_ARCHER);
		MasteryKillTracker.onPlayerHit(1, T);
		MasteryKillTracker.observe(1, "Petrified Archer", true, targets(), T);
		MasteryKillTracker.observe(1, "Petrified Archer", true, targets(), T + 50);
		MasteryKillTracker.observe(1, "Petrified Archer", true, targets(), T + 100);
		assertEquals(1, currentOf(KILL_ARCHER), "one death, one kill");
	}

	/** The tag turns to hearts while you fight; the mob must still be credited when it dies. */
	@Test
	public void heartBarPhaseDoesNotLoseTheMob() {
		active(KILL_ARCHER);
		MasteryKillTracker.observe(1, "Petrified Archer", false, targets(), T); // seen, name cached
		MasteryKillTracker.onPlayerHit(1, T + 10);
		MasteryKillTracker.observe(1, HEARTS, false, targets(), T + 10);        // hit: tag is hearts
		assertTrue(MasteryKillTracker.observe(1, HEARTS, true, targets(), T + 20),
				"cached name still credits the kill");
		assertEquals(1, currentOf(KILL_ARCHER));
	}

	/** Name returns on death even if we never saw it alive with a real tag. */
	@Test
	public void deathTagAloneStillMatches() {
		active(KILL_ARCHER);
		MasteryKillTracker.onPlayerHit(2, T);
		assertTrue(MasteryKillTracker.observe(2, "Petrified Archer", true, targets(), T));
		assertEquals(1, currentOf(KILL_ARCHER));
	}

	/** A level prefix or trailing health figure around the mob name must not break matching. */
	@Test
	public void decoratedTagStillMatches() {
		active(KILL_ARCHER);
		MasteryKillTracker.onPlayerHit(3, T);
		assertTrue(MasteryKillTracker.observe(3, "Lv5 Petrified Archer ❤ 20", true, targets(), T));
		assertEquals(1, currentOf(KILL_ARCHER));
	}

	/** A mob whose challenge is not selected earns nothing, even if we killed it. */
	@Test
	public void unrelatedMobIsNotCounted() {
		active(KILL_ARCHER);
		MasteryKillTracker.onPlayerHit(4, T);
		assertFalse(MasteryKillTracker.observe(4, "Wandering Trader", true, targets(), T));
		assertEquals(0, currentOf(KILL_ARCHER));
	}

	/** No Kill challenge active: nothing to track, nothing derived. */
	@Test
	public void noKillChallengeMeansEmptyTargets() {
		active("Win Chat Reactions");
		assertTrue(targets().isEmpty());
	}

	@Test
	public void twoDistinctMobsEachCountOnce() {
		active(KILL_ARCHER);
		MasteryKillTracker.onPlayerHit(1, T);
		MasteryKillTracker.onPlayerHit(2, T);
		MasteryKillTracker.observe(1, "Petrified Archer", true, targets(), T);
		MasteryKillTracker.observe(2, "Petrified Archer", true, targets(), T);
		assertEquals(2, currentOf(KILL_ARCHER), "separate ids, separate kills");
	}

	/** Our hit on one mob must not credit the mob that dies next to it. */
	@Test
	public void aHitOnOneMobDoesNotCreditAnother() {
		active(KILL_ARCHER);
		MasteryKillTracker.onPlayerHit(1, T);
		assertFalse(MasteryKillTracker.observe(2, "Petrified Archer", true, targets(), T),
				"hits are tracked per entity id");
		assertEquals(0, currentOf(KILL_ARCHER));
	}

	/** Nameless entities (no custom name) are ignored without error. */
	@Test
	public void namelessEntityIsIgnored() {
		active(KILL_ARCHER);
		MasteryKillTracker.onPlayerHit(5, T);
		assertFalse(MasteryKillTracker.observe(5, null, true, targets(), T));
		assertEquals(0, currentOf(KILL_ARCHER));
	}

	/** Reset drops remembered hits, so a reused entity id after a reconnect starts clean. */
	@Test
	public void resetForgetsOurHits() {
		active(KILL_ARCHER);
		MasteryKillTracker.onPlayerHit(1, T);
		MasteryKillTracker.reset();
		assertFalse(MasteryKillTracker.observe(1, "Petrified Archer", true, targets(), T));
		assertEquals(0, currentOf(KILL_ARCHER));
	}
}
