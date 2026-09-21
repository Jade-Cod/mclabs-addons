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
	/**
	 * Spacing between the three reels, and how wide a name may be.
	 *
	 * <p>The two are the same number on purpose: at anything wider the outer reels' names run
	 * under the middle one's. "Mount Rental Coupon (15m)" is a real reward, so a name that does
	 * not fit wraps to a second line rather than being cut — {@code Mount Rental C} tells you
	 * nothing about which coupon you drew.
	 */
	private static final int REEL_GAP = 92;
	private static final int NAME_W = REEL_GAP - 6;
	private static final int NAME_LINES = 2;
	private static final int LINE_H = 10;
	private static final int REEL_Y = 62;
	private static final int TAG_Y = REEL_Y - 24;
	private static final int NAME_Y = REEL_Y + 22;
	/**
	 * Below both name lines whether or not the second is used, and below the duration whether
	 * or not the reward has one, so the three reels' figures stay on the same baselines.
	 */
	private static final int DURATION_Y = NAME_Y + NAME_LINES * LINE_H + 1;
	private static final int CHANCE_Y = DURATION_Y + 11;
	private static final int ONE_IN_Y = CHANCE_Y + 10;
	private static final int FOOT_Y = 145;
	/** Sized to the three reels and their figures, not to a chest. */
	private static final int PANEL_HEIGHT = 161;
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
	protected int panelHeight() {
		return PANEL_HEIGHT;
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
		List<VoteOdds.Draw> drawn = track(slots, now);

		VoteOdds.Table table = tableFor(drawn);
		int rarest = choosing ? VoteOdds.rarest(table, drawn) : -1;

		header(context, font, "VOTER CRATE", choosing ? "keep one" : "rolling", TEXT_DIM);

		for (int i = 0; i < REEL_COUNT; i++) {
			reel(context, font, i, drawn.get(i), table, i == rarest, now);
		}
		foot(context, font, table, drawn);
	}

	/**
	 * Remembers what each reel shows and when it last changed, so a stop can be seen.
	 *
	 * <p>The lore comes along because the name alone does not always identify the reward: three
	 * of the rewards across the two crates are called Enhanced Farming Access and differ only
	 * in how long they last.
	 */
	private List<VoteOdds.Draw> track(List<SlotView> slots, long now) {
		List<VoteOdds.Draw> drawn = new ArrayList<>(REEL_COUNT);
		for (int i = 0; i < REEL_COUNT; i++) {
			SlotView slot = SlotView.at(slots, REELS[i]);
			String name = slot == null ? "" : slot.name();
			if (!name.equals(showing[i])) {
				showing[i] = name;
				changedAtMs[i] = now;
			}
			drawn.add(new VoteOdds.Draw(name,
					VoteOdds.intrinsicLore(slot == null ? List.of() : slot.lore())));
		}
		return drawn;
	}

	private boolean locked(int reel, long now) {
		return choosing || (changedAtMs[reel] > 0L && now - changedAtMs[reel] >= LOCK_MS);
	}

	private void reel(DrawContext context, TextRenderer font, int index, VoteOdds.Draw draw,
			VoteOdds.Table table, boolean rarest, long now) {
		String name = draw.item();
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

		List<String> label = wrap(font, name, NAME_W);
		for (int line = 0; line < label.size(); line++) {
			CrateChamber.centred(context, font, label.get(line), cx, NAME_Y + line * LINE_H,
					settled ? TEXT : TEXT_FAINT);
		}

		if (!settled) {
			return;
		}
		// The one thing a voter reward's name can leave out and still change what it is worth.
		String duration = VoteOdds.duration(draw.lore());
		if (!duration.isEmpty()) {
			CrateChamber.centred(context, font, font.trimToWidth(duration, NAME_W), cx,
					DURATION_Y, TEXT_DIM);
		}

		Double chance = table.chance(name, draw.lore());
		if (chance == null) {
			CrateChamber.centred(context, font, "odds unknown", cx, CHANCE_Y, TEXT_FAINT);
			return;
		}
		CrateChamber.centred(context, font, trim(chance) + "%", cx, CHANCE_Y, accent);
		CrateChamber.centred(context, font, VoteOdds.oneIn(chance), cx, ONE_IN_Y, TEXT_FAINT);
	}

	private void foot(DrawContext context, TextRenderer font, VoteOdds.Table table,
			List<VoteOdds.Draw> drawn) {
		if (choosing && VoteOdds.rarest(table, drawn) < 0) {
			// Worth saying only because the absence of a flag would otherwise look like the
			// odds failed to load. Everything else this line used to say — that there are
			// three draws, that rarest is not the same as best — the screen already shows.
			CrateChamber.centred(context, font, "no single rarest draw", CENTRE_X, FOOT_Y,
					TEXT_FAINT);
		}
	}

	/**
	 * Which crate's table these three draws came from.
	 *
	 * <p>Neither the roll nor the choice names its crate, so the crate is inferred from the
	 * rewards themselves: whichever remembered table accounts for more of the three. The two
	 * voter crates share only a handful of rewards, so this is usually decisive — and where it
	 * is not, an empty table shows no figures rather than the wrong crate's.
	 */
	private static VoteOdds.Table tableFor(List<VoteOdds.Draw> drawn) {
		VoteOdds.Table best = VoteOdds.Table.EMPTY;
		int bestHits = 0;
		boolean tied = false;
		for (String crate : crates()) {
			VoteOdds.Table table = tableFor(crate);
			int hits = 0;
			for (VoteOdds.Draw draw : drawn) {
				if (table.chance(draw.item(), draw.lore()) != null) {
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

	/**
	 * One crate's table: what was scraped from it if anything, otherwise what shipped.
	 *
	 * <p>Not merged. A scrape is the whole of that crate's current table, so mixing it with the
	 * bundled one would resurrect rewards the server has since removed.
	 */
	private static VoteOdds.Table tableFor(String crate) {
		List<VoteOddsEntry> scraped =
				VoteOdds.entriesFor(LabsAddonsConfig.get().voteCrateOdds, crate);
		if (!scraped.isEmpty()) {
			return VoteOdds.Table.of(scraped);
		}
		return VoteOdds.Table.of(VoteOddsDefaults.get().get(crate));
	}

	/** Every crate either shipped with the mod or seen since. */
	private static List<String> crates() {
		List<String> out = new ArrayList<>(VoteOddsDefaults.get().keySet());
		List<VoteOddsEntry> known = LabsAddonsConfig.get().voteCrateOdds;
		if (known != null) {
			for (VoteOddsEntry entry : known) {
				if (entry != null && entry.crate != null && !out.contains(entry.crate)) {
					out.add(entry.crate);
				}
			}
		}
		return out;
	}

	/**
	 * A reward's name over at most {@link #NAME_LINES} lines, broken on spaces.
	 *
	 * <p>Only the last line is ever cut, and only when a single run of words is wider than a
	 * reel on its own — which none of the fifty-four rewards across the two crates is.
	 */
	private static List<String> wrap(TextRenderer font, String text, int width) {
		if (text == null || text.isEmpty()) {
			return List.of();
		}
		if (font.getWidth(text) <= width) {
			return List.of(text);
		}
		String[] words = text.split(" ");
		StringBuilder head = new StringBuilder();
		int taken = 0;
		while (taken < words.length) {
			String candidate = head.isEmpty() ? words[taken] : head + " " + words[taken];
			// The first word goes on whatever it measures: a line has to hold something.
			if (!head.isEmpty() && font.getWidth(candidate) > width) {
				break;
			}
			head.setLength(0);
			head.append(candidate);
			taken++;
		}
		if (taken >= words.length) {
			return List.of(head.toString());
		}
		String rest = String.join(" ", List.of(words).subList(taken, words.length));
		return List.of(head.toString(), font.trimToWidth(rest, width));
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
