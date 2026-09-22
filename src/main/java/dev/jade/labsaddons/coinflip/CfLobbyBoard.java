package dev.jade.labsaddons.coinflip;

import dev.jade.labsaddons.casino.CasinoPanel;
import dev.jade.labsaddons.casino.Money;
import dev.jade.labsaddons.casino.SlotView;
import dev.jade.labsaddons.config.LabsAddonsConfig;
import dev.jade.labsaddons.hud.PlayerSkinCache;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.util.Util;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The lobby, re-laid: every posted flip as a row, with the id it can be taken by and what
 * taking it is worth.
 *
 * <p>The chest states a wager and leaves the rest to you. The rail states the rest once —
 * a win pays 1.90x, the edge is a flat -5% — and the row under the cursor is priced out in
 * full, so the two figures a flip is choosing between are on screen before you commit to it.
 *
 * <p>Clicks go to the real slot. The only thing the board remembers is which flip that was,
 * because the screen that follows states neither its wager nor its id.
 */
public final class CfLobbyBoard extends CasinoPanel {
	public static final CfLobbyBoard INSTANCE = new CfLobbyBoard();

	private static final int COINFLIP_SLOTS = 45;

	private static final int LIST_X = PAD;
	private static final int LIST_W = 176;
	private static final int RAIL_X = 188;
	private static final int ROW_H = 11;
	private static final int VISIBLE_ROWS = 11;
	private static final int HEAD = 9;
	private static final int NAME_X = 12;
	/** Wide enough for "$100.0m", which is the widest the server allows. */
	private static final int WAGER_W = 42;
	private static final int FACE_W = 10;
	/**
	 * Breathing room between the money column and the rail divider.
	 *
	 * <p>The list is exactly as wide as the gap to the divider, so a right-aligned figure
	 * ended flush against the line with no gutter at all.
	 */
	private static final int LIST_GUTTER = 5;
	private static final int FOOTER_Y = CONTENT_Y + VISIBLE_ROWS * ROW_H + 3;
	private static final int REFRESH_W = 54;
	private static final int REFRESH_H = 13;
	private static final int REFRESH_Y = PANEL_H - PAD - REFRESH_H;
	private static final int ROW_GAP = 9;
	private static final int BLOCK_GAP = 12;
	/** A recent flip is two lines: what it moved, and who moved it. */
	private static final int RECENT_ENTRY_H = 20;
	private static final int RECENT_INDENT = 6;

	private final Map<Integer, CfLobbyReader.Row> rowsBySlot = new HashMap<>();
	private int scroll;

	private CfLobbyBoard() {
	}

	@Override
	protected boolean enabled() {
		return LabsAddonsConfig.get().coinflipOverlay;
	}

	@Override
	protected int containerSlots() {
		return COINFLIP_SLOTS;
	}

	@Override
	protected boolean looksLike(AbstractContainerMenu handler) {
		return CfLobbyReader.looksLikeLobby(nameOf(handler, CfLobbyReader.INFO_SLOT),
				nameOf(handler, CfLobbyReader.REFRESH_SLOT));
	}

	@Override
	protected boolean parses(List<SlotView> slots) {
		return CfLobbyReader.isLobby(slots);
	}

	@Override
	protected void onContainerChange() {
		scroll = 0;
		rowsBySlot.clear();
	}

	@Override
	protected void onScroll(double amount) {
		// Clamped against the list we drew last frame, which is the one the player is
		// looking at.
		int max = Math.max(0, rowsBySlot.size() - VISIBLE_ROWS);
		scroll = Math.clamp(scroll - (int) Math.signum(amount), 0, max);
	}

	@Override
	protected void onClick(int slot) {
		CfLobbyReader.Row row = rowsBySlot.get(slot);
		if (row != null) {
			CfChat.expect(row.id(), row.wagerCents(), row.player(), row.creatorFace(),
					Util.getMillis());
		}
	}

	@Override
	protected void draw(GuiGraphicsExtractor context, Font font, List<SlotView> slots,
			String title, float deviceScale) {
		CfLobbyReader.Lobby lobby = CfLobbyReader.read(slots);
		List<CfLobbyReader.Row> rows = lobby.rows();
		rowsBySlot.clear();
		for (CfLobbyReader.Row row : rows) {
			rowsBySlot.put(row.slot(), row);
		}
		scroll = Math.clamp(scroll, 0, Math.max(0, rows.size() - VISIBLE_ROWS));

		header(context, font, "COINFLIPS", rows.size() + " open",
				rows.isEmpty() ? TEXT_FAINT : TEXT_DIM);

		list(context, font, rows);
		// Centred on the list it belongs to, not shoved against the rail divider.
		button(context, font, LIST_X + (LIST_W - REFRESH_W) / 2, REFRESH_Y, REFRESH_W,
				REFRESH_H, "REFRESH", CfLobbyReader.REFRESH_SLOT, accent());
		context.fill(RAIL_X - 6, CONTENT_Y, RAIL_X - 5, PANEL_H - PAD, DIVIDER);
		rail(context, font);
	}

	private void list(GuiGraphicsExtractor context, Font font, List<CfLobbyReader.Row> rows) {
		if (rows.isEmpty()) {
			context.text(font, "nothing posted", LIST_X, CONTENT_Y + 4, TEXT_FAINT, false);
			hint(context, font);
			return;
		}
		int shown = Math.min(VISIBLE_ROWS, rows.size() - scroll);
		for (int i = 0; i < shown; i++) {
			row(context, font, rows.get(scroll + i), CONTENT_Y + i * ROW_H);
		}
		if (rows.size() > VISIBLE_ROWS) {
			int below = rows.size() - scroll - shown;
			String more = below > 0 ? "+" + below + " below · scroll" : "scroll up for more";
			context.text(font, more, LIST_X, FOOTER_Y, TEXT_FAINT, false);
			return;
		}
		hint(context, font);
	}

	private void hint(GuiGraphicsExtractor context, Font font) {
		context.text(font, "/cf create <wager> <heads/tails>", LIST_X, FOOTER_Y,
				TEXT_FAINT, false);
	}

	private void row(GuiGraphicsExtractor context, Font font, CfLobbyReader.Row row, int y) {
		boolean hot = hovered(LIST_X, y, LIST_W, ROW_H);
		if (hot) {
			context.fill(LIST_X, y, LIST_X + LIST_W, y + ROW_H, ROW_HOVER);
		}
		PlayerFaceExtractor.extractRenderState(context, PlayerSkinCache.skin(row.player()), LIST_X + 1, y + 1,
				HEAD);

		String wager = Money.compact(row.wagerCents());
		int nameW = LIST_W - LIST_GUTTER - NAME_X - WAGER_W - FACE_W - 4;
		context.text(font, font.plainSubstrByWidth(row.player(), nameW), LIST_X + NAME_X, y + 2,
				hot ? TEXT : TEXT_DIM, false);
		// One letter for the side you would be taking: the full word does not fit, and the
		// side is the one thing about a flip that changes nothing about its price.
		String side = row.yourFace().isEmpty() ? "" : row.yourFace().substring(0, 1)
				.toUpperCase(Locale.ROOT);
		int right = LIST_X + LIST_W - LIST_GUTTER;
		context.text(font, side, right - WAGER_W - FACE_W, y + 2,
				hot ? accent() : TEXT_FAINT, false);
		context.text(font, wager, right - font.width(wager), y + 2,
				hot ? TEXT : TEXT_DIM, false);

		clickable(LIST_X, y, LIST_W, ROW_H, row.slot());
	}

	private void rail(GuiGraphicsExtractor context, Font font) {
		int y = CONTENT_Y;
		caption(context, font, RAIL_X, y, "STATS");
		y += ROW_GAP;
		y = stats(context, font, y);

		y = divider(context, y);
		caption(context, font, RAIL_X, y, "RECENT");
		y += ROW_GAP;
		recent(context, font, y);
	}

	/** Wins and losses on one line, coloured, and what the two of them came to. */
	private int stats(GuiGraphicsExtractor context, Font font, int y) {
		CfStats.Record kept = CfStats.current();
		if (kept.isEmpty()) {
			context.text(font, CfStats.seeded() ? "no flips yet" : "run /cf stats",
					RAIL_X, y, TEXT_FAINT, false);
			return y + BLOCK_GAP;
		}
		String wins = String.valueOf(kept.won());
		String slash = "/";
		String losses = String.valueOf(kept.lost());
		int x = RAIL_X;
		context.text(font, wins, x, y, WIN, false);
		x += font.width(wins);
		context.text(font, slash, x, y, TEXT_FAINT, false);
		x += font.width(slash);
		context.text(font, losses, x, y, LOSS, false);
		String rate = kept.winRateText();
		context.text(font, rate, PANEL_W - PAD - font.width(rate), y, TEXT_DIM, false);

		y += ROW_GAP;
		row(context, font, y, "profit", Money.compact(kept.profitCents()),
				kept.profitCents() >= 0 ? WIN : LOSS);
		return y + BLOCK_GAP;
	}

	/** Your own last few flips: what each one moved, and who moved it. */
	private void recent(GuiGraphicsExtractor context, Font font, int y) {
		List<CfPlayed> played = CfStats.recent();
		if (played.isEmpty()) {
			context.text(font, "none yet", RAIL_X, y, TEXT_FAINT, false);
			return;
		}
		int width = PANEL_W - PAD - RAIL_X;
		for (CfPlayed flip : played) {
			if (y + RECENT_ENTRY_H > PANEL_H - PAD) {
				return;
			}
			// Abbreviated rather than compact: every row of this column has to read the
			// same way, and "+$9,000" beside "−$10.0k" does not.
			String amount = (flip.won ? "+" : "") + Money.abbreviated(flip.netCents);
			context.text(font, amount, RAIL_X, y, flip.won ? WIN : LOSS, false);
			String who = font.plainSubstrByWidth(flip.opponent, width - RECENT_INDENT);
			context.text(font, who, RAIL_X + RECENT_INDENT, y + ROW_GAP, TEXT_FAINT, false);
			y += RECENT_ENTRY_H;
		}
	}

	private int divider(GuiGraphicsExtractor context, int y) {
		context.fill(RAIL_X, y, PANEL_W - PAD, y + 1, DIVIDER);
		return y + 5;
	}

	/** A label and a value across the rail, which is too narrow for the shared helper. */
	private void row(GuiGraphicsExtractor context, Font font, int y, String label, String value,
			int valueColor) {
		if (!label.isEmpty()) {
			context.text(font, label, RAIL_X, y, TEXT_FAINT, false);
		}
		context.text(font, value, PANEL_W - PAD - font.width(value), y, valueColor,
				false);
	}

	private static String nameOf(AbstractContainerMenu handler, int slot) {
		if (slot < 0 || slot >= handler.slots.size()) {
			return "";
		}
		ItemStack stack = handler.slots.get(slot).getItem();
		return stack.isEmpty() ? "" : stack.getHoverName().getString();
	}
}
