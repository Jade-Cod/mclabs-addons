package dev.jade.labsaddons.casino;

import dev.jade.labsaddons.config.LabsAddonsConfig;
import dev.jade.labsaddons.hud.editor.EditorPainter;
import dev.jade.labsaddons.mines.MinesOdds;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

import java.util.List;
import java.util.Locale;

/**
 * The betting screen, for both games.
 *
 * <p>This is the board with the most to add, because the server states nothing here. On
 * Mines it shows no payouts at all until you are already playing, so you commit blind:
 * the ladder on the right is the whole reason to replace this screen, and it recomputes as
 * the stake and the mine count change. On BondJoules it shows what each outcome returns,
 * with the one row nobody has ever measured marked as unmeasured rather than filled in.
 */
public final class BetBoard extends CasinoPanel {
	public static final BetBoard INSTANCE = new BetBoard();

	private static final int LEFT_W = 144;
	private static final int RAIL_X = PAD + LEFT_W + 12;
	private static final int RAIL_W = PANEL_W - PAD - RAIL_X;
	private static final int VALUE_SCALE = 2;
	private static final int CHIP_H = 14;
	private static final int CHIP_GAP = 3;
	private static final int STEPPER_H = 16;
	private static final int START_H = 18;
	private static final int START_Y = PANEL_H - PAD - START_H;
	private static final int LADDER_ROWS = 5;
	private static final int LADDER_ROW_H = 9;
	/** Measured: a dealt 21 returned two and a half times the stake, twice. */
	private static final double DEALT_21_RETURN = 2.5;
	/** Mines hands back a tenth of a percent as Investor Points; BondJoules a full one. */
	private static final String MINES_POINTS = "0.1% back in points";
	private static final String BJ_POINTS = "1% back in points";

	private static final int[][] CHIPS = {
			{BetReader.MINUS_10K_SLOT, BetReader.MINUS_1K_SLOT, BetReader.MINUS_100_SLOT},
			{BetReader.PLUS_100_SLOT, BetReader.PLUS_1K_SLOT, BetReader.PLUS_10K_SLOT}
	};
	private static final String[][] CHIP_LABELS = {
			{"−$10k", "−$1k", "−$100"},
			{"+$100", "+$1k", "+$10k"}
	};

	private BetBoard() {
	}

	/**
	 * Either game's toggle. Which one it really is cannot be known from the handler alone,
	 * so this is the wider test and {@link #parses} narrows it once the slots are read.
	 */
	@Override
	protected boolean enabled() {
		return LabsAddonsConfig.get().minesOverlay || LabsAddonsConfig.get().blackjackOverlay;
	}

	@Override
	protected boolean looksLike(ScreenHandler handler) {
		return BetReader.looksLikeBet(nameOf(handler, BetReader.START_SLOT),
				nameOf(handler, BetReader.PLUS_100_SLOT));
	}

	private static String nameOf(ScreenHandler handler, int slot) {
		ItemStack stack = handler.slots.get(slot).getStack();
		return stack.isEmpty() ? "" : stack.getName().getString();
	}

	@Override
	protected boolean parses(List<SlotView> slots) {
		if (!BetReader.isBet(slots)) {
			return false;
		}
		// Turning off one game's board has to give back its betting screen too, not just
		// the game itself.
		return BetReader.read(slots).isMines()
				? LabsAddonsConfig.get().minesOverlay
				: LabsAddonsConfig.get().blackjackOverlay;
	}

	@Override
	protected void draw(DrawContext context, TextRenderer font, List<SlotView> slots,
			String title, float deviceScale) {
		BetReader.BetState state = BetReader.read(slots);
		int accent = accent();

		header(context, font, state.isMines() ? "MINES" : "BONDJOULES",
				state.isMines() ? MINES_POINTS : BJ_POINTS, TEXT_FAINT);

		int y = CONTENT_Y;
		caption(context, font, PAD, y, "INVESTMENT");
		y += font.fontHeight + 1;
		big(context, font, PAD, y, state.investmentText(), TEXT);
		y += font.fontHeight * VALUE_SCALE + 5;

		y = chips(context, font, y, accent);
		if (state.isMines()) {
			stepper(context, font, state, y, accent);
		} else if (state.hasMinMax()) {
			minMax(context, font, y, accent);
		}

		button(context, font, PAD, START_Y, LEFT_W, START_H,
				state.isMines() ? "START GAME" : "START EXPERIMENT",
				BetReader.START_SLOT, accent);

		context.fill(RAIL_X - 6, CONTENT_Y, RAIL_X - 5, PANEL_H - PAD, DIVIDER);
		if (state.isMines()) {
			ladder(context, font, state);
		} else {
			payouts(context, font, state);
		}
	}

	/** Text at twice the font size, for the one figure this screen is about. */
	private void big(DrawContext context, TextRenderer font, int x, int y, String text,
			int color) {
		context.getMatrices().pushMatrix();
		context.getMatrices().translate(x, y);
		context.getMatrices().scale(VALUE_SCALE, VALUE_SCALE);
		context.drawText(font, font.trimToWidth(text, LEFT_W / VALUE_SCALE), 0, 0,
				color, false);
		context.getMatrices().popMatrix();
	}

	private int chips(DrawContext context, TextRenderer font, int top, int accent) {
		int chipW = (LEFT_W - CHIP_GAP * 2) / 3;
		for (int row = 0; row < CHIPS.length; row++) {
			int y = top + row * (CHIP_H + CHIP_GAP);
			for (int column = 0; column < CHIPS[row].length; column++) {
				int x = PAD + column * (chipW + CHIP_GAP);
				// The last chip in a row takes the rounding slack, so the row ends flush.
				int w = column == 2 ? LEFT_W - (chipW + CHIP_GAP) * 2 : chipW;
				button(context, font, x, y, w, CHIP_H, CHIP_LABELS[row][column],
						CHIPS[row][column], accent);
			}
		}
		return top + CHIPS.length * (CHIP_H + CHIP_GAP) + 3;
	}

	/**
	 * The mine count, with the tnt pair behind the arrows. The server's own two items are
	 * labelled "+1 Mine" and "-1 Mine" but share the same "increase" lore, so the arrows
	 * here say what each one does rather than repeating it.
	 */
	private void stepper(DrawContext context, TextRenderer font, BetReader.BetState state,
			int y, int accent) {
		caption(context, font, PAD, y, "MINES");
		int top = y + font.fontHeight + 1;
		int side = STEPPER_H;
		int middle = LEFT_W - side * 2 - CHIP_GAP * 2;

		button(context, font, PAD, top, side, STEPPER_H, "−",
				BetReader.FEWER_MINES_SLOT, accent);

		int x = PAD + side + CHIP_GAP;
		context.fill(x, top, x + middle, top + STEPPER_H, PANEL);
		EditorPainter.outline(context, x, top, middle, STEPPER_H, BUTTON_BORDER);
		String count = String.valueOf(state.mines());
		context.drawText(font, count, x + (middle - font.getWidth(count)) / 2,
				top + (STEPPER_H - font.fontHeight) / 2 + 1, TEXT, false);

		button(context, font, PAD + side + middle + CHIP_GAP * 2, top, side, STEPPER_H, "+",
				BetReader.MORE_MINES_SLOT, accent);
	}

	/**
	 * Real buttons for what the server hides behind shift-clicking a chip.
	 *
	 * <p>These send {@code QUICK_MOVE}, which is what the client sends for a shift-click —
	 * an ordinary click on those two slots is just another ten thousand either way, so a
	 * plain forward here would have made both buttons lie about what they do.
	 *
	 * <p>Only offered on BondJoules, which is the only menu that advertises it. Mines may
	 * well support the same thing, but it does not say so and this board does not guess.
	 */
	private void minMax(DrawContext context, TextRenderer font, int y, int accent) {
		int half = (LEFT_W - CHIP_GAP) / 2;
		button(context, font, PAD, y, half, CHIP_H, "MIN", BetReader.MINUS_10K_SLOT, accent,
				SlotActionType.QUICK_MOVE);
		button(context, font, PAD + half + CHIP_GAP, y, LEFT_W - half - CHIP_GAP, CHIP_H,
				"MAX", BetReader.PLUS_10K_SLOT, accent, SlotActionType.QUICK_MOVE);
	}

	/**
	 * What each rung would pay at this stake and mine count. All of it is derived — the
	 * server states no payout on this screen at all — so the rate it rests on is named.
	 */
	private void ladder(DrawContext context, TextRenderer font, BetReader.BetState state) {
		int y = CONTENT_Y;
		caption(context, font, RAIL_X, y, "PAYOUT LADDER");
		y += font.fontHeight + 1;

		int last = MinesOdds.safeTiles(state.mines());
		for (int stars = 1; stars <= Math.min(LADDER_ROWS, last); stars++) {
			long payout = MinesOdds.payoutCents(state.investmentCents(), state.mines(), stars);
			boolean underWater = payout < state.investmentCents();
			context.drawText(font, String.valueOf(stars), RAIL_X, y, TEXT_FAINT, false);
			String mult = MinesOdds.multiplierText(state.mines(), stars);
			context.drawText(font, mult, RAIL_X + 9, y, underWater ? WARN : TEXT, false);
			String money = Money.compact(payout);
			context.drawText(font, money, RAIL_X + RAIL_W - font.getWidth(money), y,
					underWater ? WARN : TEXT_DIM, false);
			y += LADDER_ROW_H;
		}

		y += 3;
		String chance = String.format(Locale.ROOT, "%.0f%%",
				MinesOdds.mineChance(state.mines(), 0) * 100);
		row(context, font, RAIL_X, y, RAIL_W, "first tile", chance + " mine", TEXT_DIM, LOSS);
		y += font.fontHeight + 1;
		row(context, font, RAIL_X, y, RAIL_W, "return", "70% of fair", TEXT_DIM, TEXT_DIM);
		y += font.fontHeight + 4;

		// Worth saying outright: at a low mine count the first rungs are a loss even when
		// you win them, and the server never mentions it.
		if (MinesOdds.payoutCents(state.investmentCents(), state.mines(), 1)
				< state.investmentCents()) {
			context.fill(RAIL_X, y, RAIL_X + 1, y + font.fontHeight * 2 + 2, WARN);
			context.drawText(font, "rungs below 1.00x", RAIL_X + 4, y, WARN, false);
			context.drawText(font, "pay back less", RAIL_X + 4, y + font.fontHeight, WARN, false);
		}
	}

	/**
	 * What each outcome returns. Three of the four are measured; the ordinary win is not,
	 * and is marked rather than guessed.
	 */
	private void payouts(DrawContext context, TextRenderer font, BetReader.BetState state) {
		int y = CONTENT_Y;
		caption(context, font, RAIL_X, y, "IF YOU WIN");
		y += font.fontHeight + 1;

		long stake = state.investmentCents();
		row(context, font, RAIL_X, y, RAIL_W, "21 dealt",
				Money.compact(Math.round(stake * DEALT_21_RETURN)), TEXT, WIN);
		y += font.fontHeight + 1;
		// The one figure nobody has ever seen the server pay.
		row(context, font, RAIL_X, y, RAIL_W, "beat the lab", "?", TEXT, TEXT_FAINT);
		y += font.fontHeight + 1;
		row(context, font, RAIL_X, y, RAIL_W, "neutralized", Money.compact(stake),
				TEXT, TEXT_DIM);
		y += font.fontHeight + 1;
		row(context, font, RAIL_X, y, RAIL_W, "lab wins", "$0", TEXT, LOSS);
		y += font.fontHeight + 5;

		context.fill(RAIL_X, y, RAIL_X + 1, y + font.fontHeight * 3 + 2, WARN);
		context.drawText(font, "both wins seen were", RAIL_X + 4, y, WARN, false);
		context.drawText(font, "dealt 21 at 2.5x — an", RAIL_X + 4, y + font.fontHeight,
				WARN, false);
		context.drawText(font, "ordinary win is untested", RAIL_X + 4,
				y + font.fontHeight * 2, WARN, false);
	}
}
