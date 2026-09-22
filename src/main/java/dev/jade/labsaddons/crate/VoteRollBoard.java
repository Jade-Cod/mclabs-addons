package dev.jade.labsaddons.crate;

import dev.jade.labsaddons.casino.CasinoPanel;
import dev.jade.labsaddons.casino.SlotView;
import dev.jade.labsaddons.config.LabsAddonsConfig;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.OrderedText;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * <p>It states the figures and stops there. It used to flag the rarest of the three as well,
 * which was one judgement too many on a screen already showing three percentages: a $75,000 at
 * 3% against a Mystery Crate Key at 1% is not a ranking the odds can settle, so the ranking was
 * never worth as much as the room it took.
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
	/**
	 * A daily reward gets a third line, because a daily reel has no figures under it to make
	 * room for and its names are the longest of any roll — "120-minute Enhanced Farming
	 * Voucher" does not fit two lines this narrow, and a cut reward name is the one thing on
	 * this screen a player cannot work out for themselves.
	 */
	private static final int DAILY_NAME_LINES = 3;
	private static final int LINE_H = 10;
	private static final int REEL_Y = 62;
	private static final int NAME_Y = REEL_Y + 22;
	/**
	 * Below both name lines whether or not the second is used, and below the duration whether
	 * or not the reward has one, so the three reels' figures stay on the same baselines.
	 */
	private static final int DURATION_Y = NAME_Y + NAME_LINES * LINE_H + 1;
	private static final int CHANCE_Y = DURATION_Y + 11;
	private static final int ONE_IN_Y = CHANCE_Y + 10;
	/** Sized to the three reels and their figures, not to a chest. */
	private static final int PANEL_HEIGHT = 144;
	/** The same minus the two figures a daily has none of, plus its extra name line. */
	private static final int DAILY_PANEL_HEIGHT =
			NAME_Y + DAILY_NAME_LINES * LINE_H + 8;
	/**
	 * The clickable card, which is the whole of a reel's column and not merely its icon.
	 *
	 * <p>A box around the item alone stopped just under the first line of the name, so clicking
	 * the reward's second line, its duration or its odds — the three things a player is reading
	 * when they decide — did nothing at all, and the hover highlight cut off mid-name. Spans the
	 * whole column down to the last figure, and stays inside {@link #REEL_GAP} so two cards
	 * never overlap.
	 */
	private static final int CARD_W = NAME_W;
	private static final int CARD_TOP = 34;
	private static final int CARD_H = ONE_IN_Y + LINE_H - CARD_TOP;
	private static final int DAILY_CARD_H = NAME_Y + DAILY_NAME_LINES * LINE_H - CARD_TOP;

	/**
	 * How long a reel must hold the same reward before it reads as stopped.
	 *
	 * <p>ponytail: self-correcting rather than exact. The cycle runs at about 100ms, so three
	 * identical draws in a row would settle this early — and then simply unsettle on the next
	 * change. The authoritative stop is the server reopening the menu as the choice, so being
	 * a frame optimistic here costs a flicker, not a wrong reading.
	 */
	private static final long LOCK_MS = 260L;
	/**
	 * One step of the belt: how far a reward travels between the server swapping the slot and
	 * swapping it again, and equally the height of the window it travels through. The two are
	 * the same number so exactly one reward is ever fully in view.
	 */
	private static final int REEL_STEP = 30;
	/**
	 * How long that step takes. A shade longer than the server's own hundred-millisecond
	 * cadence, so a reel still spinning is always mid-step and the belt never appears to stop
	 * between rewards — and the last step, which has no successor to interrupt it, lands the
	 * final reward in the middle.
	 */
	private static final long SLIDE_MS = 120L;
	private static final float ROLL_SCALE = 1.2f;
	private static final float PICK_SCALE = 1.5f;
	/** A spinning reward is dimmer than a settled one, which is most of what says it is moving. */
	private static final float ROLL_ALPHA = 0.75f;

	private final String[] showing = new String[REEL_COUNT];
	private final long[] changedAtMs = new long[REEL_COUNT];
	/** What each reel is showing, and what it showed a step ago — the two halves of the belt. */
	private final ItemStack[] shown = new ItemStack[REEL_COUNT];
	private final ItemStack[] previous = new ItemStack[REEL_COUNT];
	private boolean choosing;
	/** Whether this roll is {@code /daily}'s rather than a voter crate's; see {@link DailySpin}. */
	private boolean daily;
	/**
	 * Which crate this roll is, once the rewards have said so.
	 *
	 * <p>Held for the whole roll rather than inferred per frame. The reels cycle through a
	 * hundred rewards on the way down, and a frame whose three happen to sit in both crates'
	 * tables is not decisive — so re-deciding every frame let a settled reel's figure flicker
	 * between the two crates' numbers while its neighbours were still moving. One decisive frame
	 * is enough: everything a reel shows comes from the crate actually being opened, so a table
	 * that accounts for strictly more of them is that crate.
	 */
	private VoteOdds.Table crate = VoteOdds.Table.EMPTY;

	private VoteRollBoard() {
	}

	@Override
	protected boolean enabled() {
		return LabsAddonsConfig.get().crateOverlay;
	}

	@Override
	protected int panelHeight() {
		// Asked before draw() runs, so it reads the flag at source rather than the field.
		return DailySpin.isSpinning(Util.getMeasuringTimeMs()) ? DAILY_PANEL_HEIGHT
				: PANEL_HEIGHT;
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
			shown[i] = null;
			previous[i] = null;
		}
		crate = VoteOdds.Table.EMPTY;
	}

	@Override
	protected void draw(DrawContext context, TextRenderer font, List<SlotView> slots,
			String title, float deviceScale) {
		long now = Util.getMeasuringTimeMs();
		choosing = CrateTitles.isVoteChoice(title);
		daily = DailySpin.isSpinning(now);
		List<VoteOdds.Draw> drawn = track(slots, now);

		// MCLabs publishes the daily's odds nowhere, and its pool overlaps the voter crates'
		// in three rewards — so asking the voter tables about it would put a figure under a
		// daily reward that is simply another crate's.
		if (!daily && crate.isEmpty()) {
			crate = tableFor(drawn);
		}

		header(context, font, daily ? "DAILY SPIN" : "VOTER CRATE", status(), TEXT_DIM);

		for (int i = 0; i < REEL_COUNT; i++) {
			reel(context, font, i, drawn.get(i), crate, now);
		}
		hoveredTooltip(context, font);
	}

	/** What the header says on the right: the streak for a daily, the phase for a crate. */
	private String status() {
		if (daily) {
			return DailySpin.streak() > 0 ? "Streak: " + DailySpin.streak() : "daily";
		}
		return choosing ? "Choose One" : "rolling";
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
				previous[i] = shown[i];
				showing[i] = name;
				changedAtMs[i] = now;
			}
			shown[i] = stackAt(REELS[i]);
			drawn.add(new VoteOdds.Draw(name,
					VoteOdds.intrinsicLore(slot == null ? List.of() : slot.lore())));
		}
		return drawn;
	}

	private boolean locked(int reel, long now) {
		return choosing || (changedAtMs[reel] > 0L && now - changedAtMs[reel] >= LOCK_MS);
	}

	private void reel(DrawContext context, TextRenderer font, int index, VoteOdds.Draw draw,
			VoteOdds.Table table, long now) {
		String name = draw.item();
		int cx = CENTRE_X + (index - 1) * REEL_GAP;
		boolean settled = locked(index, now);
		int cardX = cardX(index);
		int cardH = cardHeight();
		boolean hot = choosing && hovered(cardX, CARD_TOP, CARD_W, cardH);

		if (choosing) {
			if (hot) {
				context.fill(cardX, CARD_TOP, cardX + CARD_W, CARD_TOP + cardH, ROW_HOVER);
			}
			clickable(cardX, CARD_TOP, CARD_W, cardH, REELS[index]);
		}

		if (settled) {
			CrateChamber.mote(context, stackAt(REELS[index]), CrateChamber.NEUTRAL, cx, REEL_Y,
					choosing ? PICK_SCALE : ROLL_SCALE, 1f);
		} else {
			belt(context, index, cx, now);
		}

		name(context, font, index, name, cx, settled);

		if (!settled || daily) {
			// Nothing is published about a daily reward beyond its name, and a caption
			// saying so would be the same nothing taking up a line.
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
		CrateChamber.centred(context, font, trim(chance) + "%", cx, CHANCE_Y,
				hot ? 0xFFFFFFFF : TEXT);
		CrateChamber.centred(context, font, VoteOdds.oneIn(chance), cx, ONE_IN_Y, TEXT_FAINT);
	}

	/**
	 * A reel's reward, named the way the server names it.
	 *
	 * <p>The server's own colours once the reel has stopped: every reward is coloured and
	 * five of them are two-tone — "Mega Millions" gold against "Cash Roll" grey — which is
	 * the crate's own shorthand for what a reward is, and flattening it to one grey threw it
	 * away. The colour passed is only what an unstyled name falls back to, so a plain reward
	 * still reads as the rest of the board does.
	 *
	 * <p>Still moving, it stays grey. That dimming is as much of what says a reel has not
	 * settled as the sliding icon is, and the colour arriving is itself the moment it stops.
	 */
	private void name(DrawContext context, TextRenderer font, int index, String plain, int cx,
			boolean settled) {
		if (!settled) {
			List<String> label = wrap(font, plain, NAME_W);
			for (int line = 0; line < label.size(); line++) {
				CrateChamber.centred(context, font, label.get(line), cx,
						NAME_Y + line * LINE_H, TEXT_FAINT);
			}
			return;
		}
		ItemStack stack = stackAt(REELS[index]);
		if (stack == null || stack.isEmpty()) {
			return;
		}
		List<OrderedText> lines = font.wrapLines(stack.getName(), NAME_W);
		int room = daily ? DAILY_NAME_LINES : NAME_LINES;
		for (int line = 0; line < Math.min(lines.size(), room); line++) {
			OrderedText text = lines.get(line);
			context.drawText(font, text, cx - font.getWidth(text) / 2,
					NAME_Y + line * LINE_H, TEXT, false);
		}
	}

	private int cardHeight() {
		return daily ? DAILY_CARD_H : CARD_H;
	}

	/**
	 * A reel mid-spin: the outgoing reward sliding down out of the window as the new one drops
	 * in behind it, clipped so neither escapes into the name underneath.
	 *
	 * <p>The server swaps the slot about every hundred milliseconds and never says what is
	 * coming, so there is no strip to scroll — but two rewards moving one step apart is what a
	 * strip looks like through a window this size, and the steps run together into one belt at
	 * that cadence. It stops the way a real reel does, because the last step still has to finish
	 * after the server has stopped sending: the final reward slides to the middle and stays.
	 */
	private void belt(DrawContext context, int index, int cx, long now) {
		float step = Math.clamp((now - changedAtMs[index]) / (float) SLIDE_MS, 0f, 1f);
		context.enableScissor(cx - CARD_W / 2, REEL_Y - REEL_STEP / 2,
				cx + CARD_W / 2, REEL_Y + REEL_STEP / 2);
		if (previous[index] != null) {
			CrateChamber.mote(context, previous[index], CrateChamber.NEUTRAL, cx,
					REEL_Y + REEL_STEP * step, ROLL_SCALE, ROLL_ALPHA);
		}
		CrateChamber.mote(context, stackAt(REELS[index]), CrateChamber.NEUTRAL, cx,
				REEL_Y - REEL_STEP * (1f - step), ROLL_SCALE, ROLL_ALPHA);
		context.disableScissor();
	}

	/**
	 * The hovered card's real item tooltip. Last, so it is queued after everything drawn under
	 * it, and only while choosing — during the roll it would follow the cursor through a reward
	 * a second later, and the choice is where a player needs to read the enchantments.
	 */
	private void hoveredTooltip(DrawContext context, TextRenderer font) {
		if (!choosing) {
			return;
		}
		for (int i = 0; i < REEL_COUNT; i++) {
			if (hovered(cardX(i), CARD_TOP, CARD_W, cardHeight())) {
				tooltip(context, font, stackAt(REELS[i]));
				return;
			}
		}
	}

	/** The left edge of a reel's card. Shared so the hit test and the tooltip cannot drift. */
	private static int cardX(int index) {
		return CENTRE_X + (index - 1) * REEL_GAP - CARD_W / 2;
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
		for (VoteOdds.Table table : tables().values()) {
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
	 * Every crate's table, by crate: everything the mod shipped with, plus anything seen since.
	 *
	 * <p>Built once and kept. Grouping twenty-seven rewards into a table is not free, and this
	 * used to happen for both crates on every frame of a five-second animation — two tables,
	 * four maps and fifty-odd lowercased strings, sixty to two hundred and forty times a second,
	 * to reach an answer that had not changed.
	 *
	 * <p>Keyed on the remembered list by identity, which is the whole of the invalidation: a
	 * scrape publishes a fresh list rather than editing the old one in place, so a table the
	 * server has changed is rebuilt the moment the crate is next punched.
	 */
	private static Map<String, VoteOdds.Table> tables;
	private static List<VoteOddsEntry> tablesFrom;

	private static Map<String, VoteOdds.Table> tables() {
		List<VoteOddsEntry> known = LabsAddonsConfig.get().voteCrateOdds;
		if (tables != null && tablesFrom == known) {
			return tables;
		}
		Map<String, VoteOdds.Table> built = new LinkedHashMap<>();
		for (String crate : VoteOddsDefaults.get().keySet()) {
			built.put(crate, tableFor(crate));
		}
		if (known != null) {
			for (VoteOddsEntry entry : known) {
				if (entry != null && entry.crate != null && !built.containsKey(entry.crate)) {
					built.put(entry.crate, tableFor(entry.crate));
				}
			}
		}
		tables = built;
		tablesFrom = known;
		return built;
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
