package dev.jade.labsaddons.carnage;

import dev.jade.labsaddons.carnage.CarnageTracker.Award;
import dev.jade.labsaddons.carnage.CarnageTracker.Daily;
import dev.jade.labsaddons.carnage.CarnageTracker.Mission;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The repeatable goal carrying across days, the actionbar's two totals, and mission names. The
 * figures are Stage I's, from the totals recorded on every kill and every dashboard read.
 */
public class CarnageRepeatGoalTest {
	private static final long DAY_END = 1_000_000_000L;

	private static Daily freshDay(double carry) {
		return new Daily(DAY_END, "Stage I", DAY_END + 5 * CarnageTracker.DAY_MS, 0, 5_000, 0, 10, 500, 0, 16,
				List.of(), 0, 0, carry);
	}

	/** A day's kills: the one that crosses the daily goal, then the rest of the day's total. */
	private static Daily play(Daily day, double beforeCrossing, double crossingPoints, double endOfDay) {
		Daily crossed = CarnageTracker.withScore(CarnageTracker.withScore(day, beforeCrossing),
				beforeCrossing + crossingPoints, crossingPoints);
		return CarnageTracker.withScore(crossed, endOfDay);
	}

	private static Daily nextDay(Daily daily) {
		return CarnageTracker.rolled(daily, daily.dayEndMs() + 60_001);
	}

	@Test
	public void theRepeatGoalCarriesAcrossDays() {
		// Oct 6 ended on 19,608.58; Oct 7 made 5,568; Oct 8 never finished the daily goal
		Daily oct7 = play(nextDay(freshDay(19_608.58)), 4_994, 11.6, 5_568);
		assertEquals(19_608.58 + 5_568 - 5_005.6, oct7.repeatProgress(), 1e-6);
		Daily oct8 = CarnageTracker.withScore(nextDay(oct7), 912);
		assertFalse(oct8.goalDone());
		Daily oct9 = play(nextDay(oct8), 4_994, 14.3, 34_558);
		// the menu said 49,721.55; the totals the bar shows are whole numbers
		assertEquals(49_721.55, oct9.repeatProgress(), 1.0);
	}

	@Test
	public void theDailyGoalsOvershootIsLost() {
		// a boss kill took the day from 4,997 to 5,189; none of the 189 reached the repeatable goal
		Daily crossed = play(freshDay(7_534.06), 4_997, 192, 5_189);
		assertEquals(7_534.06, crossed.repeatProgress(), 1e-6);
	}

	@Test
	public void theRepeatGoalStartsOverAtItsSizeKeepingTheOvershoot() {
		Daily near = play(freshDay(43_000), 4_990, 20, 10_545.25);
		assertEquals(48_535.25, near.repeatProgress(), 1e-6);
		Daily over = CarnageTracker.withScore(near, 12_093.25, 1_548);
		assertEquals(83.25, over.repeatProgress(), 1e-6);
	}

	@Test
	public void aDashboardBeforeTheGoalKeepsTheCarry() {
		Daily known = freshDay(20_171.58);
		Daily synced = new Daily(DAY_END, "Stage I", DAY_END, 1_393.8, 5_000, 2, 10, 500, 2, 16, List.of());
		assertEquals(20_171.58, CarnageTracker.merge(known, synced).repeatCarry(), 1e-9);
	}

	@Test
	public void aDashboardAfterTheGoalAnchorsOnTodaysTotal() {
		Daily known = play(freshDay(20_171.58), 4_994, 14.3, 6_392);
		Daily synced = new Daily(DAY_END, "Stage I", DAY_END, 0, 0, 10, 10, 500, 2, 16, List.of(), 50_000, -1,
				21_555.02);
		Daily merged = CarnageTracker.merge(known, synced);
		assertEquals(21_555.02, merged.repeatProgress(), 1e-9);
		assertEquals(21_569.02, CarnageTracker.withScore(merged, 6_406, 14).repeatProgress(), 1e-6);
	}

	@Test
	public void withNoTotalTheNextKillAnchorsOnTheTotalBeforeIt() {
		Daily synced = new Daily(DAY_END, "Stage I", DAY_END, 0, 0, 10, 10, 500, 2, 16, List.of(), 50_000, -1,
				21_555.02);
		Daily pending = CarnageTracker.merge(null, synced);
		assertEquals(21_555.02, pending.repeatProgress(), 1e-9);
		assertEquals(21_569.02, CarnageTracker.withScore(pending, 6_406, 14).repeatProgress(), 1e-6);
	}

	@Test
	public void aKillInTheCountdownsLastMinuteStaysOnTheOldDay() {
		Daily done = play(freshDay(0), 4_990, 20, 8_000);
		long justAfter = done.dayEndMs() + 30_000;
		assertSame(done, CarnageTracker.rolled(done, justAfter));
		Daily kill = CarnageTracker.withScore(CarnageTracker.rolled(done, justAfter), 8_010, 10);
		assertEquals(done.goalsDone(), kill.goalsDone());
		assertEquals(done.dayEndMs(), kill.dayEndMs());
	}

	@Test
	public void anAfterGoalFirstSyncFallsBackToTheDailyGoal() {
		Daily synced = new Daily(DAY_END, "Stage II", DAY_END, 0, 0, 10, 10, 500, 2, 16, List.of(), 50_000, -1, 1);
		assertEquals(CarnageTracker.DAILY_GOAL, CarnageTracker.merge(null, synced).scoreGoal());
	}

	@Test
	public void todaysTotalGoingDownIsANewDay() {
		Daily late = play(freshDay(1_000), 4_990, 20, 8_000);
		Daily next = CarnageTracker.withScore(late, 12, 12);
		assertEquals(12, next.score(), 1e-9);
		assertFalse(next.goalDone());
		assertEquals(1_000 + 8_000 - 5_010, next.repeatCarry(), 1e-6);
	}

	@Test
	public void theActionbarCarriesThePointsAndBothTotals() {
		assertEquals(new Award(4.3, 67, 67), CarnageTracker.parseAward("+4.3 Carnage Points (67 - 67)"));
		assertEquals(new Award(6.6, 1_740, 1_745),
				CarnageTracker.parseAward("+6.6 Carnage Points [1.5x] (1,740 - 1,745)"));
		assertEquals(new Award(12, 1_234.5, -1), CarnageTracker.parseAward("+12 Carnage Points (1,234.5)"));
	}

	@Test
	public void missionNamesMatchTheTagsSpelling() {
		Daily daily = new Daily(DAY_END, "Stage I", DAY_END, 0, 5_000, 0, 10, 500, 0, 16, List.of(
				mission("Kill 10x Bog Walker"), mission("Kill 3x The Slimed"), mission("Kill 3x Dr. Frankenstein"),
				mission("Kill 50x Jack o' Sploder")));
		for (String tag : List.of("Bogwalker", "Slimed", "Frankenstein", "Jack o' 'Sploder")) {
			assertNotSame(daily, CarnageTracker.withKill(daily, tag), tag);
		}
	}

	@Test
	public void aLongerNameIsAnotherMob() {
		Daily daily = new Daily(DAY_END, "Stage I", DAY_END, 0, 5_000, 0, 10, 500, 0, 16, List.of(
				mission("Kill 3x Vampire"), mission("Kill 125x Geist")));
		assertSame(daily, CarnageTracker.withKill(daily, "Vampire Bat"));
		assertSame(daily, CarnageTracker.withKill(daily, "Poltergeist"));
		assertTrue(CarnageTracker.withKill(daily, "Vampire").missions().get(0).current() == 1);
	}

	private static Mission mission(String name) {
		return new Mission(name, CarnageTracker.killMob(name), 0, 10, "");
	}
}
