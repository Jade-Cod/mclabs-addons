package dev.jade.labsaddons.crate;

import dev.jade.labsaddons.casino.CasinoPanel;
import dev.jade.labsaddons.casino.SlotView;
import dev.jade.labsaddons.config.LabsAddonsConfig;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * The voter crate: three reels, then a choice.
 *
 * <p>A voter crate works nothing like a supply crate. There are no rarities, no coloured
 * panes and no elimination — three slots cycle like a fruit machine, stop half a second
 * apart, and then the server reopens the menu as "Choose a reward!" and lets you keep one of
 * the three.
 *
 * <p>What it never tells you is which of the three is the rare one. The odds exist, but only
 * on the menu you get from punching the crate, and by the time you are choosing, that screen
 * is long gone. So {@link VoteOddsReader} remembers it and this puts the figure under each
 * draw — the single most useful thing the mod can do here, since the whole decision is
 * otherwise made blind.
 *
 * <p>It says <em>rarest</em> and never <em>best</em>. A $75,000 at 3% against a Mystery Crate
 * Key at 1% is not a ranking the odds can settle, and picking for the player would be the mod
 * overstepping.
 */
public final class VoteRollBoard extends CasinoPanel {
	public static final VoteRollBoard INSTANCE = new VoteRollBoard();

	private static final int VOTE_SLOTS = 27;
	/**
	 * How many reels there are.
	 *
	 * <p>A plain {@code int} rather than {@code REELS.length}, and that is load-bearing: this
	 * class declares {@code INSTANCE} first, like every other board, so the constructor runs
	 * before any static array below it has been assigned. An array is never a compile-time
	 * constant, so it is still null at that point — sizing the instance arrays off its length
	 * threw on class-init and took every container screen in the game down with it. An
	 * {@code int} constant is inlined by the compiler and cannot be null.
	 */
	private static final int REEL_COUNT = 3;
	/** Which slots those reels are, used only from methods, long after class-init. */
	private static final int[] REELS = {11, 13, 15};

	private static final int CENTRE_X = PANEL_W / 2;
	private static final int REEL_GAP = 78;
	private static final int REEL_Y = 62;
	private static final int TAG_Y = REEL_Y - 24;
	private static final int NAME_Y = REEL_Y + 22;
	private static final int CHANCE_Y = NAME_Y + 11;
	private static final int ONE_IN_Y = CHANCE_Y + 10;
	private static final int FOOT_Y = 154;
	private static final int PICK_W = 72;
	private static final int PICK_H = 62;

	/**
	 * How long a reel must hold the same reward before it reads as stopped.
	 *
	 * <p>ponytail: self-correcting rather than exact. The cycle runs at about 100ms, so three
	 * identical draws in a row would settle this early — and then simply unsettle on the next
	 * change. The authoritative stop is the server reopening the menu as the choice, so being
	 * a frame optimistic here costs a flicker, not a wrong reading.
	 */
	private static final long LOCK_MS = 260L;
	/** How far a still-spinning reward shivers, in pixels. */
	private static final int SHIVER = 1;

	private final String[] showing = new String[REEL_COUNT];
	private final long[] changedAtMs = new long[REEL_COUNT];
	private boolean choosing;

	private VoteRollBoard() {
	}

	@Override
	protected boolean enabled() {
		return LabsAddonsConfig.get().crateOverlay;
	}

	@Override
	protected int containerSlots() {
		return VOTE_SLOTS;
	}

	/** One slot, and only whether it holds something that is not a pane. */
	@Override
	protected boolean looksLike(ScreenHandler handler) {
		ItemStack middle = stackOf(handler, REELS[1]);
		return !middle.isEmpty();
	}

	@Override
	protected boolean titleAllows(String title) {
		return CrateTitles.isVoteRoll(title) || CrateTitles.isVoteChoice(title);
	}

	@Override
	protected boolean parses(List<SlotView> slots) {
		return false;
	}

	/** All three reels holding something is what tells this from a chest that shares the title. */
	@Override
	protected boolean parses(List<SlotView> slots, String title) {
		if (!titleAllows(title) || slots.size() < VOTE_SLOTS) {
			return false;
		}
		for (int reel : REELS) {
			if (slots.get(reel).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Both voter screens count as the same menu, so the board rides the handover from the roll
	 * to the choice instead of letting the real chest through for the frame or two the new
	 * container is empty.
	 *
	 * <p>Only those two share the key. A constant would also match an unrelated chest that
	 * happens to open empty, and hold this board over it for the whole reopen grace.
	 */
	@Override
	protected String holdKey(String title) {
		if (titleAllows(title)) {
			return "voter-crate";
		}
		return title == null ? "" : title;
	}

	@Override
	protected void onContainerChange() {
		for (int i = 0; i < REEL_COUNT; i++) {
			showing[i] = null;
			changedAtMs[i] = 0L;
		}
	}

	@Override
	protected void draw(DrawContext context, TextRenderer font, List<SlotView> slots,
			String title, float deviceScale) {
		long now = Util.getMeasuringTimeMs();
		choosing = CrateTitles.isVoteChoice(title);
		List<String> drawn = track(slots, now);

		VoteOdds.Table table = tableFor(drawn);
		int rarest = choosing ? VoteOdds.rarest(table, drawn) : -1;

		header(context, font, "VOTER CRATE", choosing ? "keep one" : "rolling", TEXT_DIM);

		for (int i = 0; i < REEL_COUNT; i++) {
			reel(context, font, i, drawn.get(i), table, i == rarest, now);
		}
		foot(context, font, table, drawn);
	}

	/** Remembers what each reel shows and when it last changed, so a stop can be seen. */
	private List<String> track(List<SlotView> slots, long now) {
		List<String> drawn = new ArrayList<>(REEL_COUNT);
		for (int i = 0; i < REEL_COUNT; i++) {
			String name = SlotView.nameAt(slots, REELS[i]);
			if (!name.equals(showing[i])) {
				showing[i] = name;
				changedAtMs[i] = now;
			}
			drawn.add(name);
		}
		return drawn;
	}

	private boolean locked(int reel, long now) {
		return choosing || (changedAtMs[reel] > 0L && now - changedAtMs[reel] >= LOCK_MS);
	}

	private void reel(DrawContext context, TextRenderer font, int index, String name,
			VoteOdds.Table table, boolean rarest, long now) {
		int cx = CENTRE_X + (index - 1) * REEL_GAP;
		boolean settled = locked(index, now);
		// A reel still cycling shivers by a pixel, which reads as motion at a cadence too fast
		// to follow otherwise.
		int jitter = settled ? 0 : (((int) (now / 60L) + index) % 2 == 0 ? SHIVER : -SHIVER);
		boolean hot = choosing && hovered(cx - PICK_W / 2, REEL_Y - PICK_H / 2, PICK_W, PICK_H);

		if (choosing) {
			if (hot) {
				context.fill(cx - PICK_W / 2, REEL_Y - PICK_H / 2, cx + PICK_W / 2,
						REEL_Y + PICK_H / 2, ROW_HOVER);
			}
			clickable(cx - PICK_W / 2, REEL_Y - PICK_H / 2, PICK_W, PICK_H, REELS[index]);
		}

		int accent = rarest ? accent() : (hot ? 0xFFFFFFFF : TEXT_FAINT);
		// A voter crate has no rarities, so the frame carries the one thing worth marking.
		CrateChamber.mote(context, stackAt(REELS[index]),
				rarest ? accent() : CrateChamber.NEUTRAL, cx, REEL_Y + jitter,
				choosing ? 1.5f : 1.2f, settled ? 1f : 0.75f);

		if (rarest) {
			String tag = "rarest of the three";
			context.fill(cx - font.getWidth(tag) / 2 - 3, TAG_Y - 2,
					cx + font.getWidth(tag) / 2 + 3, TAG_Y + font.fontHeight, shade(accent(), 40));
			CrateChamber.centred(context, font, tag, cx, TAG_Y, accent());
		}

		String label = font.trimToWidth(name, PICK_W + 4);
		CrateChamber.centred(context, font, label, cx, NAME_Y, settled ? TEXT : TEXT_FAINT);

		if (!settled) {
			return;
		}
		Double chance = table.chance(name);
		if (chance == null) {
			CrateChamber.centred(context, font, "odds unknown", cx, CHANCE_Y, TEXT_FAINT);
			return;
		}
		CrateChamber.centred(context, font, trim(chance) + "%", cx, CHANCE_Y, accent);
		CrateChamber.centred(context, font, VoteOdds.oneIn(chance), cx, ONE_IN_Y, TEXT_FAINT);
	}

	private void foot(DrawContext context, TextRenderer font, VoteOdds.Table table,
			List<String> drawn) {
		if (table.isEmpty()) {
			CrateChamber.centred(context, font,
					"punch a voter crate once and the odds show up here", CENTRE_X, FOOT_Y,
					TEXT_FAINT);
			return;
		}
		if (!choosing) {
			CrateChamber.centred(context, font, "three draws, one to keep", CENTRE_X, FOOT_Y,
					TEXT_FAINT);
			return;
		}
		if (VoteOdds.rarest(table, drawn) < 0) {
			CrateChamber.centred(context, font, "no single rarest draw", CENTRE_X, FOOT_Y,
					TEXT_FAINT);
			return;
		}
		CrateChamber.centred(context, font, "rarest is not the same as best — your call",
				CENTRE_X, FOOT_Y, TEXT_FAINT);
	}

	/**
	 * Which crate's table these three draws came from.
	 *
	 * <p>Neither the roll nor the choice names its crate, so the crate is inferred from the
	 * rewards themselves: whichever remembered table accounts for more of the three. The two
	 * voter crates share only a handful of rewards, so this is usually decisive — and where it
	 * is not, an empty table shows no figures rather than the wrong crate's.
	 */
	private static VoteOdds.Table tableFor(List<String> drawn) {
		List<VoteOddsEntry> known = LabsAddonsConfig.get().voteCrateOdds;
		if (known == null || known.isEmpty()) {
			return VoteOdds.Table.EMPTY;
		}
		VoteOdds.Table best = VoteOdds.Table.EMPTY;
		int bestHits = 0;
		boolean tied = false;
		for (String crate : crates(known)) {
			VoteOdds.Table table = VoteOdds.tableFor(known, crate);
			int hits = 0;
			for (String name : drawn) {
				if (table.chance(name) != null) {
					hits++;
				}
			}
			if (hits > bestHits) {
				bestHits = hits;
				best = table;
				tied = false;
			} else if (hits == bestHits && hits > 0) {
				tied = true;
			}
		}
		return bestHits == 0 || tied ? VoteOdds.Table.EMPTY : best;
	}

	private static List<String> crates(List<VoteOddsEntry> known) {
		List<String> out = new ArrayList<>();
		for (VoteOddsEntry entry : known) {
			if (entry != null && entry.crate != null && !out.contains(entry.crate)) {
				out.add(entry.crate);
			}
		}
		return out;
	}

	/** "7.6" rather than "7.6000000000000005", and "3" rather than "3.0". */
	private static String trim(double chance) {
		if (chance == Math.floor(chance)) {
			return String.valueOf((long) chance);
		}
		return String.valueOf(Math.round(chance * 100d) / 100d);
	}

	private static ItemStack stackOf(ScreenHandler handler, int slot) {
		if (slot < 0 || slot >= handler.slots.size()) {
			return ItemStack.EMPTY;
		}
		return handler.slots.get(slot).getStack();
	}
}
