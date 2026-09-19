package dev.jade.labsaddons.mines;

import dev.jade.labsaddons.casino.CasinoPanel;
import dev.jade.labsaddons.casino.Glyphs;
import dev.jade.labsaddons.casino.Money;
import dev.jade.labsaddons.casino.SlotView;
import dev.jade.labsaddons.config.LabsAddonsConfig;
import dev.jade.labsaddons.hud.editor.EditorPainter;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;

import java.util.List;
import java.util.Locale;

/**
 * The Mines board: the 5x5 grid at the size it deserves, with the payout ladder and the
 * next-tile odds beside it.
 *
 * <p>The server states two figures and no more — what cashing out pays now, and what one
 * more star would pay. Those two are shown as the server gives them; the rest of the
 * ladder is worked out from {@link MinesOdds} and marked as derived, because only one
 * mine count has ever been measured.
 */
public final class MinesBoard extends CasinoPanel {
	public static final MinesBoard INSTANCE = new MinesBoard();

	private static final int TILE = 22;
	private static final int TILE_GAP = 2;
	private static final int GRID = 5 * TILE + 4 * TILE_GAP;
	private static final int GRID_X = PAD;
	private static final int GRID_Y = CONTENT_Y + 2;
	private static final int RAIL_X = GRID_X + GRID + 11;
	private static final int RAIL_W = PANEL_W - PAD - RAIL_X;
	/** Rungs shown at once; enough to see where the next two steps land. */
	private static final int LADDER_ROWS = 5;
	private static final int LADDER_ROW_H = 9;
	private static final int CASH_H = 22;

	private static final int TILE_BG = 0xFF23262E;
	private static final int TILE_BORDER = 0xFF343945;
	private static final int SAFE_BG = 0xFF2A2418;
	private static final int MINE_BG = 0xFF2E1B1B;
	private static final int CASH_BG = 0xFF17342F;
	private static final int CASH_BORDER = 0xFF45C08A;

	private MinesBoard() {
	}

	@Override
	protected boolean enabled() {
		return LabsAddonsConfig.get().minesOverlay;
	}

	@Override
	protected boolean looksLike(ScreenHandler handler) {
		return MinesReader.looksLikeMines(nameOf(handler, MinesReader.MINE_COUNT_SLOT),
				nameOf(handler, MinesReader.STAKE_SLOT));
	}

	private static String nameOf(ScreenHandler handler, int slot) {
		ItemStack stack = handler.slots.get(slot).getStack();
		return stack.isEmpty() ? "" : stack.getName().getString();
	}

	@Override
	protected boolean parses(List<SlotView> slots) {
		return MinesReader.isMines(slots);
	}

	@Override
	protected void draw(DrawContext context, TextRenderer font, List<SlotView> slots,
			String title, float deviceScale) {
		MinesState state = MinesReader.read(slots);
		int accent = accent();

		header(context, font, "MINES",
				state.mines() + (state.mines() == 1 ? " mine · " : " mines · ")
						+ state.stakeText(),
				state.blown() ? LOSS : TEXT_DIM);

		grid(context, state, accent);
		odds(context, font, state);

		context.fill(RAIL_X - 6, CONTENT_Y, RAIL_X - 5, PANEL_H - PAD, DIVIDER);
		rail(context, font, state, accent);
	}

	private void grid(DrawContext context, MinesState state, int accent) {
		for (int row = 0; row < 5; row++) {
			for (int column = 0; column < 5; column++) {
				int x = GRID_X + column * (TILE + TILE_GAP);
				int y = GRID_Y + row * (TILE + TILE_GAP);
				MinesState.Tile tile = state.tiles().get(row * 5 + column);
				// Only a face-down tile on a live game is worth a click; once a mine is
				// showing the server ignores the grid anyway.
				boolean live = tile == MinesState.Tile.HIDDEN && !state.blown();
				boolean hot = live && hovered(x, y, TILE, TILE);

				context.fill(x, y, x + TILE, y + TILE, switch (tile) {
					case HIDDEN -> hot ? ROW_HOVER : TILE_BG;
					case SAFE -> SAFE_BG;
					case MINE -> MINE_BG;
				});
				EditorPainter.outline(context, x, y, TILE, TILE, switch (tile) {
					case HIDDEN -> hot ? accent : TILE_BORDER;
					case SAFE -> WARN;
					case MINE -> LOSS;
				});
				if (tile == MinesState.Tile.SAFE) {
					Glyphs.centred(context, Glyphs.STAR, x, y, TILE, TILE, 2, WARN);
				} else if (tile == MinesState.Tile.MINE) {
					Glyphs.centred(context, Glyphs.MINE, x, y, TILE, TILE, 2, LOSS);
				}
				if (live) {
					clickable(x, y, TILE, TILE, MinesReader.tileSlot(row, column));
				}
			}
		}
	}

	/** The exact chance the next tile is a mine, under the grid where the eye already is. */
	private void odds(DrawContext context, TextRenderer font, MinesState state) {
		int y = GRID_Y + GRID + 4;
		if (state.blown()) {
			context.drawText(font, "mine hit — grid revealed", GRID_X, y, LOSS, false);
			return;
		}
		// The mine count is already in the header, so this line only carries the odds —
		// anything more is wider than the space beside the divider.
		String chance = String.format(Locale.ROOT, "%.0f%%",
				MinesOdds.mineChance(state.mines(), state.stars()) * 100);
		context.drawText(font, "next tile", GRID_X, y, TEXT_DIM, false);
		int x = GRID_X + font.getWidth("next tile ");
		context.drawText(font, chance, x, y, LOSS, false);
		context.drawText(font, " mine", x + font.getWidth(chance), y, TEXT_DIM, false);
	}

	private void rail(DrawContext context, TextRenderer font, MinesState state, int accent) {
		int y = CONTENT_Y + 2;
		caption(context, font, RAIL_X, y, "STAKE");
		y += font.fontHeight;
		context.drawText(font, state.stakeText(), RAIL_X, y, TEXT, false);
		y += font.fontHeight + 4;

		caption(context, font, RAIL_X, y, "LADDER");
		y += font.fontHeight + 1;
		ladder(context, font, state, y, accent);
		y += LADDER_ROWS * LADDER_ROW_H + 4;

		cashOut(context, font, state, y);
		y += CASH_H + 3;

		result(context, font, state, y);
	}

	/**
	 * Five rungs around where you stand. The window slides rather than scrolling from the
	 * top, so the rung you are on and the two above it are always in view — those are the
	 * only ones a decision turns on.
	 */
	private void ladder(DrawContext context, TextRenderer font, MinesState state, int top,
			int accent) {
		int here = state.stars();
		int last = MinesOdds.safeTiles(state.mines());
		int first = Math.clamp(here - 1, 1, Math.max(1, last - LADDER_ROWS + 1));

		for (int i = 0; i < LADDER_ROWS; i++) {
			int stars = first + i;
			int y = top + i * LADDER_ROW_H;
			if (stars > last) {
				return;
			}
			boolean current = stars == here;
			boolean reached = stars <= here;
			boolean underWater = state.rungUnderWater(stars);
			int color = current ? accent : underWater ? WARN : reached ? TEXT_DIM : TEXT;

			String index = String.valueOf(stars);
			context.drawText(font, index, RAIL_X, y, current ? accent : TEXT_FAINT, false);

			String mult = MinesOdds.multiplierText(state.mines(), stars);
			int multW = font.getWidth(mult);
			context.drawText(font, mult, RAIL_X + RAIL_W - multW, y, color, false);

			// A bar rather than a second number: it shows the shape of the climb without
			// spending width the multiplier needs.
			int barX = RAIL_X + 10;
			int barW = RAIL_W - multW - 14;
			if (barW > 0) {
				int filled = Math.clamp(
						(int) Math.round(barW * Math.min(1.0,
								MinesOdds.multiplier(state.mines(), stars) / 10.0)),
						1, barW);
				context.fill(barX, y + 3, barX + barW, y + 6, TRACK);
				context.fill(barX, y + 3, barX + filled, y + 6,
						current ? accent : shade(color, 0x80));
			}
		}
	}

	private void cashOut(DrawContext context, TextRenderer font, MinesState state, int y) {
		boolean live = state.canCashOut();
		boolean hot = live && hovered(RAIL_X, y, RAIL_W, CASH_H);
		context.fill(RAIL_X, y, RAIL_X + RAIL_W, y + CASH_H, live ? CASH_BG : PANEL);
		EditorPainter.outline(context, RAIL_X, y, RAIL_W, CASH_H,
				hot ? 0xFFFFFFFF : live ? CASH_BORDER : DIVIDER);

		// No offer and a star already turned means the game ended — the server takes the
		// item away the instant it does, whichever way it went.
		boolean over = !live && (state.blown() || state.stars() > 0);
		String label = live ? "CASH OUT" : over ? "GAME OVER" : "PICK A TILE";
		context.drawText(font, label, RAIL_X + (RAIL_W - font.getWidth(label)) / 2, y + 3,
				live ? WIN : TEXT_FAINT, false);

		String amount = live
				? Money.format(state.cashOutCents())
				: over ? "" : Money.format(
						MinesOdds.payoutCents(state.stakeCents(), state.mines(), 1));
		if (!amount.isEmpty()) {
			String fit = font.trimToWidth(amount, RAIL_W - 4);
			context.drawText(font, fit, RAIL_X + (RAIL_W - font.getWidth(fit)) / 2, y + 12,
					live ? TEXT : TEXT_FAINT, false);
		}

		if (live) {
			clickable(RAIL_X, y, RAIL_W, CASH_H, MinesReader.CASH_OUT_SLOT);
		}
	}

	/**
	 * One line, shared: what one more star is worth while the game runs, and what the
	 * game paid once it is over. The menu stops stating a figure the moment it ends, so
	 * the settled line comes from chat.
	 */
	private void result(DrawContext context, TextRenderer font, MinesState state, int y) {
		MinesChat.Outcome outcome = MinesChat.lastOutcome();
		if (state.blown() || !state.canCashOut()) {
			if (outcome != null) {
				String settle = (outcome.won() ? "+" : "−") + Money.format(outcome.amountCents());
				row(context, font, RAIL_X, y, RAIL_W, outcome.won() ? "won" : "lost", settle,
						TEXT_DIM, outcome.won() ? WIN : LOSS);
			}
			return;
		}
		int next = state.stars() + 1;
		if (next > MinesOdds.safeTiles(state.mines())) {
			return;
		}
		String amount = Money.compact(state.rungCents(next));
		row(context, font, RAIL_X, y, RAIL_W, "one more", amount, TEXT_DIM,
				state.rungIsDerived(next) ? TEXT_DIM : TEXT);
	}
}
