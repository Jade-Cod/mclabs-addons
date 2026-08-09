package dev.jade.labsaddons.mastery;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MasteryGainsTest {
	private long now;

	@BeforeEach
	public void setUp() {
		now = 1_000_000L;
		MasteryGains.setClock(() -> now);
		MasteryGains.clear();
		MasteryTracker.clear();
	}

	@AfterEach
	public void tearDown() {
		MasteryGains.setClock(null);
		MasteryGains.clear();
		MasteryTracker.clear();
	}

	private static MasteryQuest quest(String name, double current) {
		return new MasteryQuest(null, name, current, 100, 0);
	}

	@Test
	public void gainAccumulatesAndRestartsTheClock() {
		MasteryGains.record("Win Chat Reactions", 1);
		now += 8_000;
		MasteryGains.record("Win Chat Reactions", 1);
		assertEquals(2, MasteryGains.delta("Win Chat Reactions"), "repeat gain accumulates");

		// 19s after the SECOND gain: still alive because the clock restarted.
		now += 19_000;
		assertEquals(1f, MasteryGains.alpha("Win Chat Reactions"), 1e-6, "timer restarted on repeat gain");
	}

	@Test
	public void rowFadesThenDisappears() {
		MasteryGains.record("Win Chat Reactions", 1);
		now += MasteryGains.LIFE_MS - 1;
		assertEquals(1f, MasteryGains.alpha("Win Chat Reactions"), 1e-6);

		now += 1 + MasteryGains.FADE_MS / 2;
		float mid = MasteryGains.alpha("Win Chat Reactions");
		assertTrue(mid > 0f && mid < 1f, "mid-fade should be partially transparent, was " + mid);

		now += MasteryGains.FADE_MS;
		assertEquals(0f, MasteryGains.alpha("Win Chat Reactions"), 1e-6);
		assertFalse(MasteryGains.hasRecent(), "expired rows are pruned");
	}

	@Test
	public void expiredGainDoesNotAccumulateIntoTheNextOne() {
		MasteryGains.record("Win Chat Reactions", 5);
		now += MasteryGains.LIFE_MS + MasteryGains.FADE_MS + 1;
		MasteryGains.record("Win Chat Reactions", 2);
		assertEquals(2, MasteryGains.delta("Win Chat Reactions"), "counter resets once the row is gone");
	}

	@Test
	public void mostRecentlyGainedComesFirst() {
		MasteryGains.record("Alpha", 1);
		now += 100;
		MasteryGains.record("Beta", 1);
		now += 100;
		MasteryGains.record("Gamma", 1);
		assertEquals(List.of("Gamma", "Beta", "Alpha"), MasteryGains.recentNames());

		now += 100;
		MasteryGains.record("Alpha", 1);
		assertEquals(List.of("Alpha", "Gamma", "Beta"), MasteryGains.recentNames(),
				"gaining again moves a quest back to the front");
	}

	/** Same-millisecond gains must still have a deterministic order. */
	@Test
	public void simultaneousGainsOrderByInsertion() {
		MasteryGains.record("First", 1);
		MasteryGains.record("Second", 1);
		assertEquals(List.of("Second", "First"), MasteryGains.recentNames());
	}

	@Test
	public void nonPositiveDeltaIsIgnored() {
		MasteryGains.record("Win Chat Reactions", 0);
		MasteryGains.record("Win Chat Reactions", -3);
		assertFalse(MasteryGains.hasRecent());
	}

	// --- gains sourced from a /mastery re-scrape ---

	@Test
	public void firstScrapeIsBaselineOnly() {
		MasteryTracker.setQuests(List.of(quest("Sell to Red Dealer", 631076.685)));
		assertFalse(MasteryGains.hasRecent(), "nothing to diff against on the first scrape");
	}

	@Test
	public void secondScrapeReportsTheDelta() {
		MasteryTracker.setQuests(List.of(quest("Sell to Red Dealer", 631076.685)));
		MasteryTracker.setQuests(List.of(quest("Sell to Red Dealer", 650000.0)));
		assertEquals(18923.315, MasteryGains.delta("Sell to Red Dealer"), 1e-6);
	}

	@Test
	public void unchangedQuestReportsNoGain() {
		MasteryTracker.setQuests(List.of(quest("Sell to Red Dealer", 631076.685)));
		MasteryTracker.setQuests(List.of(quest("Sell to Red Dealer", 631076.685)));
		assertFalse(MasteryGains.hasRecent());
	}

	/** A re-rolled challenge has no prior value, so it must not flash as a gain. */
	@Test
	public void newlySwappedQuestIsBaselineOnly() {
		MasteryTracker.setQuests(List.of(quest("Sell to Red Dealer", 100)));
		MasteryTracker.setQuests(List.of(quest("Kill Nyx", 4)));
		assertFalse(MasteryGains.hasRecent());
	}

	/**
	 * A chat win bumps the local value; the confirming scrape must not count it twice.
	 */
	@Test
	public void chatBumpThenConfirmingScrapeCountsOnce() {
		MasteryTracker.setQuests(List.of(quest("Win Chat Reactions", 37)));
		MasteryTracker.advance("Win Chat Reactions", 1);
		assertEquals(1, MasteryGains.delta("Win Chat Reactions"));

		// Server confirms 38 — the local value already reflects it, so delta is zero.
		MasteryTracker.setQuests(List.of(quest("Win Chat Reactions", 38)));
		assertEquals(1, MasteryGains.delta("Win Chat Reactions"), "no double count");
	}

	/** advance() matches case-insensitively but must file the gain under the real name. */
	@Test
	public void gainIsRecordedUnderTheQuestsOwnSpelling() {
		MasteryTracker.setQuests(List.of(quest("Win Chat Reactions", 37)));
		MasteryTracker.advance("win chat reactions", 1);
		assertEquals(1, MasteryGains.delta("Win Chat Reactions"));
		assertEquals(List.of("Win Chat Reactions"), MasteryGains.recentNames());
	}

	@Test
	public void inactiveQuestRecordsNoGain() {
		MasteryTracker.setQuests(List.of(quest("Kill Nyx", 1)));
		assertFalse(MasteryTracker.advance("Win Chat Reactions", 1));
		assertFalse(MasteryGains.hasRecent());
	}
}
