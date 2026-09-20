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

import net.minecraft.util.Util;

import java.util.Arrays;
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
	private static final int SAFE_EARNED_BG = 0xFF2D2315;
	private static final int SAFE_GHOST_BG = 0xFF201D17;
	private static final int MINE_FATAL_BG = 0xFF4A1414;
	private static final int MINE_CASCADE_BG = 0xFF281616;
	private static final int CASH_BG = 0xFF17342F;
	private static final int CASH_BORDER = 0xFF45C08A;

	/** How long a tile takes to turn over. */
	private static final long FLIP_MS = 180L;
	/** Per ring of distance from the mine that ended it, when the whole grid comes up. */
	private static final long CASCADE_STEP_MS = 45L;

	/** What the grid looked like last frame, so a turn can be spotted as it happens. */
	private final MinesState.Tile[] seen = new MinesState.Tile[MinesOdds.TILES];
	/** When each tile should start turning; 0 for one that was already face up. */
	private final long[] turnsAt = new long[MinesOdds.TILES];
	/** Tiles the player uncovered while the game was live (as opposed to post-game cascade). */
	private final boolean[] playerPicks = new boolean[MinesOdds.TILES];
	/** The mine that ended the game, which the losing cascade radiates from. */
	private int hitTile = -1;
	/**
	 * Stars the player actually turned. Once you lose, the server reveals the whole grid,
	 * and counting those would put the ladder somewhere nobody climbed to.
	 */
	private int playedStars;

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

	/** A new game: nothing is turned over, and nothing was. */
	@Override
	protected void onContainerChange() {
		Arrays.fill(seen, null);
		Arrays.fill(turnsAt, 0L);
		Arrays.fill(playerPicks, false);
		hitTile = -1;
		playedStars = 0;
	}

	@Override
	protected void draw(DrawContext context, TextRenderer font, List<SlotView> slots,
			String title, float deviceScale) {
		MinesState state = MinesReader.read(slots);
		int accent = accent();
		track(state);

		header(context, font, "MINES",
				state.mines() + (state.mines() == 1 ? " mine · " : " mines · ")
						+ state.stakeText(),
				state.blown() ? LOSS : TEXT_DIM);

		grid(context, state, accent);
		odds(context, font, state);

		context.fill(RAIL_X - 6, CONTENT_Y, RAIL_X - 5, PANEL_H - PAD, DIVIDER);
		rail(context, font, state, accent);
	}

	/**
	 * Notices what the server turned over since the last frame, and decides when each one
	 * starts moving.
	 *
	 * <p>Keyed to the grid's contents rather than to the clock, so a container re-send
	 * replays nothing: a tile that has not changed is not turned again.
	 */
	private void track(MinesState state) {
		long now = Util.getMeasuringTimeMs();
		for (int i = 0; i < MinesOdds.TILES; i++) {
			MinesState.Tile tile = state.tiles().get(i);
			if (seen[i] == tile) {
				continue;
			}
			boolean wasDown = seen[i] == null || seen[i] == MinesState.Tile.HIDDEN;
			if (wasDown && tile != MinesState.Tile.HIDDEN) {
				// The mine that ends the game turns two seconds before the rest, so the
				// first one seen is the one that was stepped on.
				if (hitTile < 0 && tile == MinesState.Tile.MINE) {
					hitTile = i;
				}
				turnsAt[i] = now + cascadeDelay(i);
			}
			seen[i] = tile;
		}
		if (!state.blown()) {
			for (int i = 0; i < MinesOdds.TILES; i++) {
				if (state.tiles().get(i) == MinesState.Tile.SAFE) {
					playerPicks[i] = true;
				}
			}
			playedStars = state.stars();
		}
	}

	/**
	 * The losing reveal arrives all at once. Staggering it outward from the mine that
	 * caused it reads as the grid coming up rather than as a jump cut — the data is the
	 * server's either way, only the moment each tile moves is ours.
	 */
	private long cascadeDelay(int tile) {
		if (hitTile < 0 || tile == hitTile) {
			return 0L;
		}
		int rings = Math.max(Math.abs(tile / 5 - hitTile / 5), Math.abs(tile % 5 - hitTile % 5));
		return rings * CASCADE_STEP_MS;
	}

	private void grid(DrawContext context, MinesState state, int accent) {
		long now = Util.getMeasuringTimeMs();
		for (int row = 0; row < 5; row++) {
			for (int column = 0; column < 5; column++) {
				int index = row * 5 + column;
				int x = GRID_X + column * (TILE + TILE_GAP);
				int y = GRID_Y + row * (TILE + TILE_GAP);
				MinesState.Tile tile = state.tiles().get(index);
				// Only a face-down tile on a live game is worth a click; once a mine is
				// showing the server ignores the grid anyway.
				boolean live = tile == MinesState.Tile.HIDDEN && !state.blown();
				boolean hot = live && hovered(x, y, TILE, TILE);

				float turn = turnsAt[index] == 0L ? 1f
						: Math.clamp((now - turnsAt[index]) / (float) FLIP_MS, 0f, 1f);
				// Under half way the tile still shows its back, and it is squeezed to
				// nothing at the moment it passes edge-on.
				boolean shown = turn >= 0.5f;
				float squeeze = turn >= 1f ? 1f : Math.abs(turn * 2f - 1f);

				// Ambient drop shadow beneath tile
				context.fill(x + 1, y + 1, x + TILE + 1, y + TILE + 1, 0x44000000);

				if (squeeze < 1f) {
					context.getMatrices().pushMatrix();
					context.getMatrices().translate(x + TILE / 2f, y + TILE / 2f);
					context.getMatrices().scale(Math.max(0.02f, squeeze), 1f);
					context.getMatrices().translate(-(x + TILE / 2f), -(y + TILE / 2f));
				}
				face(context, shown ? tile : MinesState.Tile.HIDDEN, index, x, y, hot, accent, state);
				if (turn >= 0.40f && turn <= 0.65f) {
					int shineAlpha = (int) (0x90 * (1f - Math.abs(turn - 0.52f) / 0.13f));
					context.fill(x, y, x + TILE, y + TILE, (shineAlpha << 24) | 0x00FFFFFF);
				}
				if (squeeze < 1f) {
					context.getMatrices().popMatrix();
				}

				if (live) {
					clickable(x, y, TILE, TILE, MinesReader.tileSlot(row, column));
				}
			}
		}
	}

	private void face(DrawContext context, MinesState.Tile tile, int index, int x, int y,
			boolean hot, int accent, MinesState state) {
		switch (tile) {
			case HIDDEN -> {
				int bg = hot ? ROW_HOVER : TILE_BG;
				context.fill(x, y, x + TILE, y + TILE, bg);

				// Specular top highlight & bottom shadow bevel
				context.fill(x + 1, y + 1, x + TILE - 1, y + 2, hot ? 0x30FFFFFF : 0x18FFFFFF);
				context.fill(x + 1, y + TILE - 2, x + TILE - 1, y + TILE - 1, 0x40000000);

				int border = hot ? accent : TILE_BORDER;
				EditorPainter.outline(context, x, y, TILE, TILE, border);
				// Interactive center pip ONLY when hovered under cursor
				if (hot) {
					context.fill(x + 10, y + 10, x + 12, y + 12, accent);
				}
			}
			case SAFE -> {
				boolean earned = playerPicks[index] || !state.blown();
				if (earned) {
					// Trophy Star (uncovered by player during active play)
					context.fill(x, y, x + TILE, y + TILE, SAFE_EARNED_BG);
					context.fill(x + 1, y + 1, x + TILE - 1, y + 2, 0x35FFE082);
					EditorPainter.outline(context, x, y, TILE, TILE, 0xFFE6A817);
					Glyphs.centred(context, Glyphs.STAR, x, y, TILE, TILE, 2, 0xFFFFD54F);
				} else {
					// Ghost / cascade revealed safe tile after loss
					context.fill(x, y, x + TILE, y + TILE, SAFE_GHOST_BG);
					EditorPainter.outline(context, x, y, TILE, TILE, 0xFF6E5D3B);
					Glyphs.centred(context, Glyphs.STAR, x, y, TILE, TILE, 2, 0xFF9E8A5E);
				}
			}
			case MINE -> {
				boolean fatal = index == hitTile;
				if (fatal) {
					// The fatal mine stepped on
					context.fill(x, y, x + TILE, y + TILE, MINE_FATAL_BG);
					context.fill(x + 1, y + 1, x + TILE - 1, y + 2, 0x50FF5252);
					EditorPainter.outline(context, x, y, TILE, TILE, 0xFFFF1744);
					EditorPainter.outline(context, x - 1, y - 1, TILE + 2, TILE + 2, 0x60FF1744);
					Glyphs.centred(context, Glyphs.MINE, x, y, TILE, TILE, 2, 0xFFFF3D00);
				} else {
					// Other hidden mines revealed in cascade
					context.fill(x, y, x + TILE, y + TILE, MINE_CASCADE_BG);
					EditorPainter.outline(context, x, y, TILE, TILE, 0xFF7A2E2E);
					Glyphs.centred(context, Glyphs.MINE, x, y, TILE, TILE, 2, 0xFFB84444);
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
		int here = playedStars;
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
		String amount = Money.format(state.rungCents(next));
		row(context, font, RAIL_X, y, RAIL_W, "one more", amount, TEXT_DIM,
				state.rungIsDerived(next) ? TEXT_DIM : TEXT);
	}
}
