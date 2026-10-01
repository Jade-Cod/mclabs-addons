package dev.jade.labsaddons.carnage;

import dev.jade.labsaddons.carnage.CarnageReader.Item;
import dev.jade.labsaddons.carnage.CarnageTracker.Daily;
import dev.jade.labsaddons.carnage.CarnageTracker.Hunt;
import dev.jade.labsaddons.carnage.CarnageTracker.Mission;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lore copied from the /carnage menus on 2026-10-01, day one of Stage I. */
public class CarnageTest {
	private static final long NOW = 1_000_000_000L;
	private static final long HOUR = 3_600_000L;
	private static final String BAR = "[|||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||]";

	private static final List<Item> DASHBOARD = List.of(
			new Item("Daily Missions", List.of(
					"[0/3] Complete all daily missions for:", "  • 0.2 Event Points", "  • 100 Store Points", "",
					"[0/4] Kill 4x Poltergeist", BAR, "• 1x Halloween Crate Key", "",
					"[0/125] Kill 125x Geist", BAR, "• Next Tag: /carnage tags", "",
					"[0/3] Kill 3x Scarecrow", BAR, "• 200 Store Points")),
			new Item("Daily Score Goal", List.of("", "Progress: 0/5,000", BAR, "", "Reward:", "• 2x Halloween Crate Key")),
			new Item("Daily Raffle", List.of("You have 0/10 tickets.", "", "Next Ticket: 0/500", BAR, "",
					"Prize #1:", "• $1,000,000", "", "Drawing in: 1d:06h:06m")),
			new Item("Daily Score Goal Bonus", List.of("", "Goals Completed: 0/16", "")),
			new Item("Carnage Countdown", List.of("", "Current day ends in: 1d:06h:06m", "",
					"Stage I ends in: 8d:06h:06m")));

	@Test
	public void readsTheDashboard() {
		Daily daily = CarnageReader.dashboard(DASHBOARD, NOW);
		assertEquals(NOW + 30 * HOUR + 6 * 60_000L, daily.dayEndMs());
		assertEquals("Stage I", daily.stage());
		assertEquals(NOW + 8 * 24 * HOUR + 6 * HOUR + 6 * 60_000L, daily.stageEndMs());
		assertEquals(5_000, daily.scoreGoal());
		assertEquals(10, daily.maxTickets());
		assertEquals(500, daily.ticketCost());
		assertEquals(16, daily.goalsTotal());
		assertEquals(List.of(
				new Mission("Kill 4x Poltergeist", "poltergeist", 0, 4, "1x Halloween Crate Key"),
				new Mission("Kill 125x Geist", "geist", 0, 125, "Next Tag: /carnage tags"),
				new Mission("Kill 3x Scarecrow", "scarecrow", 0, 3, "200 Store Points")), daily.missions());
	}

	@Test
	public void anotherMenuIsNotTheDashboard() {
		assertNull(CarnageReader.dashboard(List.of(new Item("Carnage Countdown", List.of())), NOW));
	}

	@Test
	public void readsTheHunt() {
		Hunt hunt = CarnageReader.hunt(List.of(
				new Item("Master Hunter [3/60]", List.of()),
				new Item("Giant Pumpkins [2/12]", List.of()),
				new Item("Crows [1/4]", List.of()),
				new Item("Halloween Hunt", List.of())));
		assertEquals(3, hunt.found());
		assertEquals(60, hunt.total());
		assertEquals(2, hunt.sets().size());
		assertEquals("Giant Pumpkins", hunt.sets().get(0).name());
	}

	@Test
	public void theActionbarCarriesTodaysScore() {
		assertEquals(67.0, CarnageTracker.parseScore("+4.3 Carnage Points (67 - 67)"));
		assertEquals(1234.5, CarnageTracker.parseScore("+12 Carnage Points (1,234.5 ➜ 9,999)"));
		assertNull(CarnageTracker.parseScore("+4.3 Mastery Points (67)"));
	}

	@Test
	public void scoreBuysTicketsAndCrossesTheGoalOnce() {
		Daily daily = CarnageReader.dashboard(DASHBOARD, NOW);
		Daily after = CarnageTracker.withScore(daily, 1_250);
		assertEquals(2, after.tickets());
		Daily goal = CarnageTracker.withScore(CarnageTracker.withScore(after, 5_000), 5_300);
		assertEquals(1, goal.goalsDone());
		assertEquals(10, CarnageTracker.withScore(goal, 99_999).tickets());
	}

	@Test
	public void aKillCreditsOnlyItsOwnMission() {
		Daily daily = CarnageReader.dashboard(DASHBOARD, NOW);
		Daily after = CarnageTracker.withKill(daily, "Geist");
		assertEquals(1, after.missions().get(1).current());
		assertEquals(0, after.missions().get(0).current());
		assertSame(daily, CarnageTracker.withKill(daily, "Pumpkin King"));
	}

	@Test
	public void aNewDayWipesTheBoardButKeepsGoals() {
		Daily daily = CarnageTracker.withScore(CarnageReader.dashboard(DASHBOARD, NOW), 6_000);
		Daily next = CarnageTracker.rolled(daily, daily.dayEndMs() + HOUR);
		assertEquals(daily.dayEndMs() + CarnageTracker.DAY_MS, next.dayEndMs());
		assertEquals(0, next.score());
		assertEquals(0, next.tickets());
		assertTrue(next.missions().isEmpty());
		assertEquals(1, next.goalsDone());
		assertSame(daily, CarnageTracker.rolled(daily, NOW));
	}
}
