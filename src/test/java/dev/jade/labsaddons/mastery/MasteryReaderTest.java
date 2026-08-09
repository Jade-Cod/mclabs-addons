package dev.jade.labsaddons.mastery;

import dev.jade.labsaddons.hud.ProgressHudObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Lore shapes below are copied verbatim from a real {@code /mastery} GUI dump.
 * The server splits the re-roll hint across lines after "new", which is why the
 * active marker only matches the first of those lines.
 */
public class MasteryReaderTest {
	/** The tail every active challenge carries, split exactly as the server sends it. */
	private static List<String> activeTail() {
		return List.of("", "Right-click to select new", "challenge (your current", "progress will be lost!)");
	}

	private static List<String> lore(String progressLine) {
		return java.util.stream.Stream.concat(
				java.util.stream.Stream.of("Completions: 0", "", progressLine),
				activeTail().stream()).toList();
	}

	@Test
	public void parsesFractionalCurrentWithGroupedTarget() {
		MasteryReader.Progress p = MasteryReader.parseProgress(lore("631076.685/1,152,000 (54%)"));
		assertNotNull(p);
		assertEquals(631076.685, p.current(), 1e-6);
		assertEquals(1_152_000, p.target(), 1e-6);
		assertEquals(54, p.percent());
	}

	@Test
	public void parsesPlainIntegerCounter() {
		MasteryReader.Progress p = MasteryReader.parseProgress(lore("6/15 (40%)"));
		assertNotNull(p);
		assertEquals(6, p.current(), 1e-6);
		assertEquals(15, p.target(), 1e-6);
		assertEquals(40, p.percent());
	}

	@Test
	public void parsesRemainingRealQuests() {
		MasteryReader.Progress chat = MasteryReader.parseProgress(lore("37/100 (37%)"));
		assertNotNull(chat);
		assertEquals(37, chat.current(), 1e-6);
		assertEquals(37, chat.percent());

		MasteryReader.Progress travelling = MasteryReader.parseProgress(lore("531179.149/1,152,000 (46%)"));
		assertNotNull(travelling);
		assertEquals(531179.149, travelling.current(), 1e-6);

		MasteryReader.Progress chem = MasteryReader.parseProgress(lore("319514.42/806,400 (39%)"));
		assertNotNull(chem);
		assertEquals(319514.42, chem.current(), 1e-6);
		assertEquals(806_400, chem.target(), 1e-6);
	}

	/** The challenge-picker sub-GUI must never be scraped — it has no real progress. */
	@Test
	public void rejectsChallengePickerItem() {
		assertNull(MasteryReader.parseProgress(List.of(
				"Sell 806,400 Betromelonide", "(~350 inventories)", "Completions: 0", "",
				"Click to start challenge.")));
	}

	/** A picker item would still be rejected even if it somehow carried a progress line. */
	@Test
	public void pickerMarkerWinsOverProgress() {
		assertNull(MasteryReader.parseProgress(List.of("6/15 (40%)", "Click to start challenge.")));
	}

	/** Decoration (glass panes, the Mastery Gear button) has no progress line. */
	@Test
	public void rejectsNonQuestLore() {
		assertNull(MasteryReader.parseProgress(List.of("Mastery Gear rewards. ", "", "Click to view.")));
	}

	/** Progress without the active marker is not an active quest. */
	@Test
	public void rejectsProgressWithoutActiveMarker() {
		assertNull(MasteryReader.parseProgress(List.of("6/15 (40%)")));
	}

	@Test
	public void clampsFractionForCompletedQuest() {
		MasteryQuest done = new MasteryQuest(null, "Done", 1_200_000, 1_152_000, 100);
		assertEquals(1.0, done.fraction(), 1e-9);
	}

	@Test
	public void zeroTargetDoesNotDivide() {
		assertEquals(0.0, new MasteryQuest(null, "Bad", 5, 0, 0).fraction(), 1e-9);
	}

	/**
	 * Names carry the shared verb; the icon alone cannot disambiguate (14 Pit
	 * challenges share the iron sword, and red wool is both a Cop patrol and a
	 * dealer sell), so the distinguishing word must survive.
	 */
	@Test
	public void stripsSharedVerbFromRealQuestNames() {
		assertEquals("Petrified Archer", ProgressHudObject.shortName("Kill Petrified Archer"));
		assertEquals("Giant Flame Elemental", ProgressHudObject.shortName("Kill Giant Flame Elemental"));
		assertEquals("Cod", ProgressHudObject.shortName("Catch Cod"));
		assertEquals("Papcactinide", ProgressHudObject.shortName("Sell Papcactinide"));
		assertEquals("Chat Reactions", ProgressHudObject.shortName("Complete Chat Reactions"));
	}

	/** "Sell to " must win over "Sell " so the dealer name survives intact. */
	@Test
	public void longerPrefixWinsOverShorter() {
		assertEquals("Red Dealer", ProgressHudObject.shortName("Sell to Red Dealer"));
		assertEquals("Traveling Dealer", ProgressHudObject.shortName("Sell to Traveling Dealer"));
	}

	/** A Cop patrol and a dealer sell share red wool — they must not collapse to the same label. */
	@Test
	public void redWoolQuestsStayDistinct() {
		assertNotEquals(ProgressHudObject.shortName("Red Patrol"),
				ProgressHudObject.shortName("Sell to Red Dealer"));
	}

	/** Names without a known verb are left alone. */
	@Test
	public void leavesUnprefixedNamesIntact() {
		assertEquals("Mini-Event Top 3", ProgressHudObject.shortName("Mini-Event Top 3"));
		assertEquals("Red Patrol", ProgressHudObject.shortName("Red Patrol"));
	}

	/** A name that is exactly a prefix must not be stripped to nothing. */
	@Test
	public void doesNotStripNameToEmpty() {
		assertEquals("Kill ", ProgressHudObject.shortName("Kill "));
	}
}
