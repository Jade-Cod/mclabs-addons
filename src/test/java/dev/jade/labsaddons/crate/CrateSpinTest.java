package dev.jade.labsaddons.crate;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Two real spins, replayed frame for frame.
 *
 * <p>Each row is one container frame captured with the GUI dumper, as
 * {@code "<ms since the menu opened>|<items>|<pane rarities>"}: nine columns of {@code x}
 * for a candidate and {@code .} for a grey pane, then nine of {@code c u r v s e} for the
 * rarity the column's pane is coloured for and {@code -} for grey. Nothing here is
 * invented, which is the point — the beat this most needs to get right is the winner
 * walking to the centre, and no amount of reasoning about it beats replaying one.
 */
class CrateSpinTest {
	/**
	 * Supply Crate II, won on Common. The survivor starts in column 8 and takes four steps
	 * left, which naively reads as four more culls and four more arrivals.
	 */
	private static final String[] SUPPLY_II = {
			"0|.........|---------",
			"448|x........|u--------",
			"549|xx.......|ur-------",
			"697|xxx......|uru------",
			"749|xxxx.....|uruc-----",
			"850|xxxxx....|urucu----",
			"997|xxxxxx...|urucuu---",
			"1047|xxxxxxx..|urucuuc--",
			"1149|xxxxxxxx.|urucuucu-",
			"1297|xxxxxxxxx|urucuucuc",
			"2148|xxxxxxx.x|urucuuc-c",
			"2248|xx.xxxx.x|ur-cuuc-c",
			"2449|xx..xxx.x|ur--uuc-c",
			"2748|xx..x.x.x|ur--u-c-c",
			"3147|xx..x...x|ur--u---c",
			"3698|x...x...x|u---u---c",
			"4296|x.......x|u-------c",
			"4946|........x|--------c",
			"5195|.......x.|-------c-",
			"5347|......x..|------c--",
			"5448|.....x...|-----c---",
			"5498|....x....|----c----",
			"5649|....x....|ccccccccc",
	};

	/**
	 * Favourites Crate, won on Rare — with two Exceedingly Rare candidates in the field, one
	 * of which survives until the very last cull. The best-alive reading is the whole point
	 * of the board, so this is the spin that proves it.
	 */
	private static final String[] FAVOURITES = {
			"0|.........|---------",
			"452|x........|u--------",
			"598|xx.......|ur-------",
			"700|xxx......|ure------",
			"751|xxxx.....|urec-----",
			"899|xxxxx....|urecc----",
			"1000|xxxxxx...|ureccr---",
			"1050|xxxxxxx..|ureccru--",
			"1197|xxxxxxxx.|ureccrue-",
			"1300|xxxxxxxxx|ureccruec",
			"2202|x.xxxxxxx|u-eccruec",
			"2299|x.xxxx.xx|u-eccr-ec",
			"2501|x.xx.x.xx|u-ec-r-ec",
			"2752|x.xx.x.x.|u-ec-r-e-",
			"3152|..xx.x.x.|--ec-r-e-",
			"3698|..x..x.x.|--e--r-e-",
			"4300|.....x.x.|-----r-e-",
			"5000|.....x...|-----r---",
			"5251|....x....|----r----",
			"5301|....x....|rrrrrrrrr",
	};

	private static CrateRarity rarity(char code) {
		return switch (code) {
			case 'c' -> CrateRarity.COMMON;
			case 'u' -> CrateRarity.UNCOMMON;
			case 'r' -> CrateRarity.RARE;
			case 'v' -> CrateRarity.VERY_RARE;
			case 's' -> CrateRarity.SUPER_RARE;
			case 'e' -> CrateRarity.EXCEEDINGLY_RARE;
			default -> null;
		};
	}

	/** Feeds every frame up to and including {@code untilMs}. */
	private static CrateSpin replay(String[] frames, long untilMs) {
		CrateSpin spin = new CrateSpin();
		for (String frame : frames) {
			String[] parts = frame.split("\\|");
			long at = Long.parseLong(parts[0]);
			if (at > untilMs) {
				break;
			}
			List<CrateSpin.Cell> cells = new ArrayList<>(CrateSpin.COLUMNS);
			for (int i = 0; i < CrateSpin.COLUMNS; i++) {
				boolean item = parts[1].charAt(i) == 'x';
				char code = parts[2].charAt(i);
				// Named after the rarity rather than the column, because that is how the
				// server behaves: the winner walking left is the same item being re-sent to
				// the next slot, so its name travels with it. Naming by column would make
				// the fixture claim a move renames the reward, which it does not.
				cells.add(new CrateSpin.Cell(item, rarity(code), item ? "item-" + code : ""));
			}
			spin.observe(cells, at);
		}
		return spin;
	}

	private static CrateSpin replay(String[] frames) {
		return replay(frames, Long.MAX_VALUE);
	}

	@Test
	void aWholeSpinEndsLandedOnTheWinnerInTheCentre() {
		CrateSpin spin = replay(SUPPLY_II);

		assertSame(CrateSpin.Phase.LANDED, spin.phase());
		assertEquals(CrateSpin.CENTRE_COLUMN, spin.winnerColumn());
		assertNotNull(spin.winner());
		assertSame(CrateRarity.COMMON, spin.winner().rarity());
		assertEquals(1, spin.aliveCount());
	}

	@Test
	void theWalkToTheCentreIsNotMistakenForMoreCulls() {
		// Eight culls take nine candidates to one. The four steps that follow must add none.
		assertEquals(CrateSpin.TOTAL_CULLS, replay(SUPPLY_II).culls());
	}

	@Test
	void theColumnTheWinnerWalksIntoStopsReportingWhatDiedThere() {
		// Column 4 was culled at 4296 holding an Uncommon; the Common from column 8 then
		// walks into it. Reading the corpse instead would put the wrong colour on the
		// landing and the wrong rarity in the banner.
		CrateSpin spin = replay(SUPPLY_II);
		assertEquals(CrateSpin.CENTRE_COLUMN, spin.winnerColumn());
		assertSame(CrateRarity.COMMON, spin.column(CrateSpin.CENTRE_COLUMN).rarity());
		assertEquals("item-c", spin.winner().name());
	}

	@Test
	void theWinnerKeepsItsRarityThroughAFrameWhereThePaneLags() {
		// The carry-over in follow() is what this depends on. Every captured spin sent the
		// item and its pane in the same tick, but the column the winner walks into was grey a
		// moment ago and remembers a different candidate — so if a frame ever arrives with
		// the item moved and the pane not yet caught up, the glow must not fall back to what
		// died there. Column 7 holds the Very Rare; the Common in column 8 walks into it.
		CrateSpin spin = new CrateSpin();
		long at = 0L;
		spin.observe(frame("xxxxxxxxx", "cccccccvc"), at += 100);
		assertSame(CrateRarity.VERY_RARE, spin.bestAlive());

		// Cull columns 0 to 7, leaving only column 8.
		String items = "xxxxxxxxx";
		String panes = "cccccccvc";
		for (int i = 0; i < 8; i++) {
			items = items.substring(0, i) + '.' + items.substring(i + 1);
			panes = panes.substring(0, i) + '-' + panes.substring(i + 1);
			spin.observe(frame(items, panes), at += 100);
		}
		assertEquals(CrateSpin.TOTAL_CULLS, spin.culls());
		assertSame(CrateRarity.COMMON, spin.bestAlive());

		// The walk into column 7, with its pane still grey.
		spin.observe(frame(".......x.", "---------"), at += 100);
		assertEquals(7, spin.winnerColumn());
		assertSame(CrateRarity.COMMON, spin.winner().rarity(),
				"the walker's own rarity, not the Very Rare that died in column 7");
	}

	private static List<CrateSpin.Cell> frame(String items, String panes) {
		List<CrateSpin.Cell> cells = new ArrayList<>(CrateSpin.COLUMNS);
		for (int i = 0; i < CrateSpin.COLUMNS; i++) {
			boolean item = items.charAt(i) == 'x';
			cells.add(new CrateSpin.Cell(item, rarity(panes.charAt(i)),
					item ? "item-" + panes.charAt(i) : ""));
		}
		return cells;
	}

	@Test
	void theFieldFillsBeforeAnythingIsCulled() {
		CrateSpin filling = replay(SUPPLY_II, 850);
		assertSame(CrateSpin.Phase.FILLING, filling.phase());
		assertEquals(0, filling.culls());

		CrateSpin held = replay(SUPPLY_II, 1297);
		assertSame(CrateSpin.Phase.HOLDING, held.phase());
		assertEquals(CrateSpin.COLUMNS, held.aliveCount());
	}

	@Test
	void oneCandidateLeftIsSettlingUntilTheScreenFlashes() {
		// 4946 is the last cull; 5498 is the final step of the walk. Neither is the landing.
		assertSame(CrateSpin.Phase.SETTLING, replay(SUPPLY_II, 4946).phase());
		assertSame(CrateSpin.Phase.SETTLING, replay(SUPPLY_II, 5498).phase());
		assertSame(CrateSpin.Phase.LANDED, replay(SUPPLY_II, 5649).phase());
	}

	@Test
	void theBestRarityAliveFallsAsTheFieldNarrows() {
		// Favourites held two Exceedingly Rare candidates. One dies at 4300, the other at
		// 5000 — so the top of the field only drops on the very last cull.
		assertSame(CrateRarity.EXCEEDINGLY_RARE, replay(FAVOURITES, 1300).bestAlive());
		assertSame(CrateRarity.EXCEEDINGLY_RARE, replay(FAVOURITES, 4300).bestAlive());
		assertSame(CrateRarity.RARE, replay(FAVOURITES, 5000).bestAlive());
		assertSame(CrateRarity.RARE, replay(FAVOURITES).winner().rarity());
	}

	@Test
	void aColumnTheWinnerWalksOutOfIsNotACasualty() {
		// The vent animation draws any column that is CULLED and changed recently. The winner
		// changes column four times on its way to the centre, about 100ms apart — so calling
		// those columns culled dropped a second copy of the winning item out of every one of
		// them, chasing it across the chamber.
		CrateSpin spin = new CrateSpin();
		long at = 0L;
		spin.observe(frame("xxxxxxxxx", "ccccccccc"), at += 100);
		String items = "xxxxxxxxx";
		for (int i = 0; i < 8; i++) {
			items = items.substring(0, i) + '.' + items.substring(i + 1);
			spin.observe(frame(items, "ccccccccc"), at += 100);
		}
		assertEquals(8, spin.winnerColumn());

		// Two steps of the walk. The first leaves column 8, the second leaves column 7 — and it
		// is the second that matters: column 7 became the winner's a tick ago, so a CULLED there
		// is inside the vent window and would be drawn.
		spin.observe(frame(".......x.", "ccccccccc"), at += 100);
		spin.observe(frame("......x..", "ccccccccc"), at += 100);
		assertEquals(6, spin.winnerColumn());
		assertSame(CrateSpin.ColumnState.UNSEEN, spin.column(7).state(),
				"the column the winner stepped out of, one tick after it stepped in");
		assertSame(CrateSpin.ColumnState.UNSEEN, spin.column(8).state());
		assertEquals(CrateSpin.TOTAL_CULLS, spin.culls(), "the walk kills nothing");
	}

	@Test
	void aCulledColumnRemembersWhatDiedInIt() {
		// The vent animation needs the candidate's rarity after the server has taken it away.
		CrateSpin spin = replay(FAVOURITES, 4300);
		CrateSpin.Column gone = spin.column(2);
		assertSame(CrateSpin.ColumnState.CULLED, gone.state());
		assertSame(CrateRarity.EXCEEDINGLY_RARE, gone.rarity());
		assertEquals(4300L, gone.changedAtMs());
	}

	@Test
	void anEmptyScreenIsNotAWinner() {
		CrateSpin spin = replay(SUPPLY_II, 0);
		assertNull(spin.winner());
		assertNull(spin.bestAlive());
		assertEquals(0, spin.aliveCount());
	}

	@Test
	void aShortOrMissingFrameIsIgnoredRatherThanThrown() {
		CrateSpin spin = new CrateSpin();
		spin.observe(null, 1L);
		spin.observe(List.of(CrateSpin.Cell.EMPTY), 2L);
		assertEquals(0, spin.aliveCount());
		assertSame(CrateSpin.Phase.FILLING, spin.phase());
	}
}
