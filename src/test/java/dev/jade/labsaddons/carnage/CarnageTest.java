package dev.jade.labsaddons.carnage;

import dev.jade.labsaddons.casino.SlotView;
import dev.jade.labsaddons.carnage.CarnageTracker.Daily;
import dev.jade.labsaddons.carnage.CarnageTracker.Hunt;
import dev.jade.labsaddons.carnage.CarnageTracker.Mission;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lore copied from the /carnage menus on 2026-10-01, day one of Stage I. */
public class CarnageTest {
	private static final long NOW = 1_000_000_000L;
	private static final long HOUR = 3_600_000L;
	private static final String BAR = "[|||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||]";

	private static final List<SlotView> DASHBOARD = List.of(
			item("Daily Missions", List.of(
					"[0/3] Complete all daily missions for:", "  • 0.2 Event Points", "  • 100 Store Points", "",
					"[0/4] Kill 4x Poltergeist", BAR, "• 1x Halloween Crate Key", "",
					"[0/125] Kill 125x Geist", BAR, "• Next Tag: /carnage tags", "",
					"[0/3] Kill 3x Scarecrow", BAR, "• 200 Store Points")),
			item("Daily Score Goal", List.of("", "Progress: 0/5,000", BAR, "", "Reward:", "• 2x Halloween Crate Key")),
			item("Daily Raffle", List.of("You have 0/10 tickets.", "", "Next Ticket: 0/500", BAR, "",
					"Prize #1:", "• $1,000,000", "", "Drawing in: 1d:06h:06m")),
			item("Daily Score Goal Bonus", List.of("", "Goals Completed: 0/16", "")),
			item("Carnage Countdown", List.of("", "Current day ends in: 1d:06h:06m", "",
					"Stage I ends in: 8d:06h:06m")));

	private static SlotView item(String name, List<String> lore) {
		return new SlotView(0, name, lore, 1);
	}

	@Test
	public void readsTheDashboard() {
		Daily daily = CarnageReader.dashboard(DASHBOARD, NOW);
		assertEquals(NOW + 30 * HOUR + 6 * 60_000L, daily.dayEndMs());
		assertEquals("Stage I", daily.stage());
		assertEquals(NOW + 8 * 24 * HOUR + 6 * HOUR + 6 * 60_000L, daily.stageEndMs());
		assertEquals(5_000, daily.scoreGoal());
		assertEquals(10, daily.maxTickets());
		assertEquals(500, daily.ticketBase());
		assertEquals(16, daily.goalsTotal());
		assertEquals(List.of(
				new Mission("Kill 4x Poltergeist", "poltergeist", 0, 4, "1x Halloween Crate Key"),
				new Mission("Kill 125x Geist", "geist", 0, 125, "Next Tag: /carnage tags"),
				new Mission("Kill 3x Scarecrow", "scarecrow", 0, 3, "200 Store Points")), daily.missions());
	}

	@Test
	public void anotherMenuIsNotTheDashboard() {
		assertNull(CarnageReader.dashboard(List.of(item("Carnage Countdown", List.of())), NOW));
	}

	@Test
	public void readsTheHunt() {
		Hunt hunt = CarnageReader.hunt(List.of(
				item("Master Hunter [3/60]", List.of()),
				item("Giant Pumpkins [2/12]", List.of()),
				item("Crows [1/4]", List.of()),
				item("Halloween Hunt", List.of())));
		assertEquals(3, hunt.found());
		assertEquals(60, hunt.total());
		assertEquals(2, hunt.sets().size());
		assertEquals("Giant Pumpkins", hunt.sets().get(0).name());
	}

	@Test
	public void theActionbarCarriesTodaysScore() {
		assertEquals(67.0, CarnageTracker.parseScore("+4.3 Carnage Points (67 - 67)"));
		assertEquals(1740.0, CarnageTracker.parseScore("+6.6 Carnage Points [1.5x] (1,740 - 1,740)"));
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

	private static List<SlotView> menu(SlotView... filled) {
		List<SlotView> slots = new java.util.ArrayList<>();
		for (int i = 0; i < 54; i++) {
			slots.add(SlotView.empty(i));
		}
		for (SlotView slot : filled) {
			slots.set(slot.index(), slot);
		}
		return slots;
	}

	@Test
	public void namesEveryPageByItsTitle() {
		assertEquals(CarnageMenu.Page.LEADERBOARD, CarnageMenu.Page.of("Carnage - Stage I (1/2)"));
		assertEquals(CarnageMenu.Page.SHOP, CarnageMenu.Page.of("Carnage Shop (Stage I)"));
		assertEquals(CarnageMenu.Page.TAGS, CarnageMenu.Page.of("Halloween Tags [7/7]"));
		assertNull(CarnageMenu.Page.of("Carnage Crate"));
		assertEquals(7, CarnageMenu.pageOf("Halloween Tags [7/7]")[1]);
	}

	@Test
	public void readsLeadersShopMonstersAndTags() {
		List<SlotView> slots = menu(
				new SlotView(18, "#1. Kojee53", List.of("", "8,941 Carnage Points.", "", "Set to win:"), 1),
				new SlotView(20, "Halloween Crate Key", List.of("", "Cost: 32 Souls of Fright", "", "Click to purchase."), 1),
				new SlotView(30, "Golden Apple x4", List.of("", "Cost: 1 Soul of Fright"), 1),
				new SlotView(21, "[October] Tag", List.of("", "1 tags away!"), 1),
				new SlotView(9, "???", List.of("Stage II Monster"), 1),
				new SlotView(10, "???", List.of("Kill to discover!"), 1));
		CarnageMenu.Leader leader = CarnageMenu.leaders(slots).get(0);
		assertEquals("Kojee53", leader.player());
		assertEquals("8,941", leader.points());
		assertEquals(List.of(new CarnageMenu.ShopItem(20, "Halloween Crate Key", 32),
				new CarnageMenu.ShopItem(30, "Golden Apple x4", 1)), CarnageMenu.shop(slots));
		CarnageMenu.Tag tag = CarnageMenu.tags(slots).get(0);
		assertEquals("[October]", tag.name());
		assertEquals("1 away", tag.status());
		List<CarnageMenu.Monster> monsters = CarnageMenu.bestiary(slots);
		assertEquals("II", monsters.get(0).stage());
		assertEquals("", monsters.get(1).stage());
	}

	/** The scope button cycles stage, overall, daily; its label is where it goes next. */
	@Test
	public void theScopeButtonNamesTheNextScope() {
		assertEquals("OVERALL", CarnageMenu.nextScope(new SlotView(29, "Stage I Leaderboard",
				List.of("Viewing this stage's leaderboard.", "", "Click to switch to", "overall leaderboard."), 1)));
		assertEquals("DAILY", CarnageMenu.nextScope(new SlotView(29, "Overall Leaderboard", List.of(), 1)));
		assertEquals("THIS STAGE", CarnageMenu.nextScope(new SlotView(29, "Daily Leaderboard", List.of(), 1)));
	}

	/** Read off one day: "Next Ticket: 298.6/781.25" with two held at 1,423.6. */
	@Test
	public void ticketsCostMoreAsTheyGo() {
		Daily daily = CarnageReader.dashboard(DASHBOARD, NOW);
		assertEquals(500, CarnageTracker.ticketThreshold(daily, 1), 1e-9);
		assertEquals(1_125, CarnageTracker.ticketThreshold(daily, 2), 1e-9);
		assertEquals(1_906.25, CarnageTracker.ticketThreshold(daily, 3), 1e-9);
		assertEquals(2, CarnageTracker.withScore(daily, 1_423.6).tickets());
		assertEquals(500, CarnageReader.ticketBase(781.25, 2), 1e-9);
	}

	@Test
	public void theServerSaysWhenATicketIsEarned() {
		int[] ticket = CarnageTracker.ticketEarned(
				"Carnage » Carnage Daily Raffle Ticket 1/10 earned! Next ticket in 512.32 Carnage score.");
		assertEquals(1, ticket[0]);
		assertEquals(10, ticket[1]);
		assertNull(CarnageTracker.ticketEarned("[VIP] Bob: Carnage » Carnage Daily Raffle Ticket 9/10 earned!"));
	}

	@Test
	public void onlyYourOwnFreeSoulsClaimCounts() {
		String line = "MCLabs Ophiliah just claimed their 5 Free Souls of Fright. Click this message to get yours.";
		assertTrue(CarnageTracker.claimedFreeSouls(line, "Ophiliah"));
		assertFalse(CarnageTracker.claimedFreeSouls(line, "Bob"));
		assertFalse(CarnageTracker.claimedFreeSouls("[VIP] Bob: MCLabs Ophiliah just claimed their 5 Free Souls of Fright", "Ophiliah"));
	}

	/** The dashboard at 19:47 on day one, after the daily goal and two missions. */
	private static final List<SlotView> AFTER_GOAL = List.of(
			item("Daily Missions", List.of(
					"[2/3] Complete all daily missions for:", "  • 0.2 Event Points", "  • 100 Store Points", "",
					"[2/4] Kill 4x Poltergeist", BAR, "• 1x Halloween Crate Key", "",
					"✔ Kill 125x Geist", "Complete!", "• Next Tag: [Mummy] ", "",
					"✔ Kill 3x Scarecrow", "Complete!", "• 200 Store Points")),
			item("Daily Score Goal", List.of("", "Daily Goal Complete!", "", "Repeatable Goal:",
					"Progress: 391.65/50,000", BAR, "", "Reward:", "• Next Tag: [Mummy] ")),
			item("Daily Raffle", List.of("You have 5/10 tickets.", "", "Next Ticket: 1,448.09/1,525.79", BAR)),
			item("Daily Score Goal Bonus", List.of("", "Goals Completed: 1/16", "")),
			// Same day as DASHBOARD, so a known total carries across.
			item("Carnage Countdown", List.of("", "Current day ends in: 1d:06h:06m", "", "Stage I ends in: 8d:06h:06m")));

	@Test
	public void afterTheGoalTheDashboardTracksTheRepeatGoal() {
		Daily synced = CarnageReader.dashboard(AFTER_GOAL, NOW);
		assertTrue(synced.goalDone());
		assertEquals(50_000, synced.repeatGoal());
		assertEquals(391.65, synced.repeatProgress(), 1e-9);
		assertEquals(List.of(
				new Mission("Kill 4x Poltergeist", "poltergeist", 2, 4, "1x Halloween Crate Key"),
				new Mission("Kill 125x Geist", "geist", 125, 125, "Next Tag: [Mummy]"),
				new Mission("Kill 3x Scarecrow", "scarecrow", 3, 3, "200 Store Points")), synced.missions());
	}

	@Test
	public void aKnownTotalAnchorsTheRepeatGoalAndKillsMoveIt() {
		Daily known = CarnageTracker.withScore(CarnageReader.dashboard(DASHBOARD, NOW), 5_551.6);
		Daily merged = CarnageTracker.merge(known, CarnageReader.dashboard(AFTER_GOAL, NOW));
		assertEquals(5_551.6, merged.score(), 1e-9);
		assertEquals(391.65, merged.repeatProgress(), 1e-6);
		assertEquals(401.65, CarnageTracker.withScore(merged, 5_561.6).repeatProgress(), 1e-6);
	}

	@Test
	public void withNoTotalTheNextKillAnchorsTheRepeatGoal() {
		Daily pending = CarnageTracker.merge(null, CarnageReader.dashboard(AFTER_GOAL, NOW));
		assertEquals(391.65, pending.repeatProgress(), 1e-9);
		Daily anchored = CarnageTracker.withScore(pending, 5_560);
		assertEquals(391.65, anchored.repeatProgress(), 1e-6);
		assertEquals(401.65, CarnageTracker.withScore(anchored, 5_570).repeatProgress(), 1e-6);
	}

	@Test
	public void theGoalCompletesOnceAndTheRepeatStartsThere() {
		Daily daily = CarnageReader.dashboard(DASHBOARD, NOW);
		Daily crossed = CarnageTracker.withScore(daily, 5_160);
		assertTrue(crossed.goalDone());
		assertEquals(1, crossed.goalsDone());
		assertEquals(0, crossed.repeatProgress(), 1e-9);
		assertEquals(1, CarnageTracker.goalCompleted(crossed).goalsDone());
		assertEquals(40, CarnageTracker.withScore(crossed, 5_200).repeatProgress(), 1e-9);
	}

	@Test
	public void theServerSaysWhenAMissionIsDone() {
		Daily daily = CarnageReader.dashboard(DASHBOARD, NOW);
		Daily after = CarnageTracker.missionCompleted(daily, "Kill 125x Geist");
		assertTrue(after.missions().get(1).done());
		assertEquals(0, after.missions().get(0).current());
	}

	/** Page one of the tags on day one's evening: earned, next, and still to come. */
	@Test
	public void tagsAreEarnedNextOrSomeWayOff() {
		List<SlotView> slots = menu(
				new SlotView(18, "[October] Tag", List.of("", "Earned!"), 1),
				new SlotView(19, "[Mummy] Tag", List.of("", "Next tag!", "", "Complete your daily goal"), 1),
				new SlotView(20, "[Ghost] Tag", List.of("", "1 tags away!", "", "Complete your daily goal"), 1));
		List<CarnageMenu.Tag> tags = CarnageMenu.tags(slots);
		assertTrue(tags.get(0).unlocked());
		assertFalse(tags.get(1).unlocked());
		assertTrue(tags.get(1).next());
		assertEquals("next up", tags.get(1).status());
		assertFalse(tags.get(2).unlocked());
		assertEquals("1 away", tags.get(2).status());
	}
}
