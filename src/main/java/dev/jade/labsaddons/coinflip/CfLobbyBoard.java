package dev.jade.labsaddons.coinflip;

import dev.jade.labsaddons.casino.CasinoPanel;
import dev.jade.labsaddons.casino.Money;
import dev.jade.labsaddons.casino.SlotView;
import dev.jade.labsaddons.config.LabsAddonsConfig;
import dev.jade.labsaddons.hud.PlayerSkinCache;
import dev.jade.labsaddons.hud.editor.EditorPainter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.Util;

import org.lwjgl.glfw.GLFW;

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

	// --- create form ---
	private static final int CREATE_GAP = 6;
	private static final long MIN_WAGER = 200;
	private static final long MAX_WAGER = 1_000_000;
	/** Seven digits covers the max; anything longer is already invalid. */
	private static final int MAX_DIGITS = 7;
	private static final long CONFIRM_MS = 4_000;
	/** How long after /cf create the lobby is refreshed, so the new flip is listed. */
	// ponytail: a fixed wait; key off the server's reply once its wording is known.
	private static final long REFRESH_AFTER_MS = 750;
	/** A gap this long between frames means the lobby was closed; the form starts fresh. */
	private static final long REOPEN_GAP_MS = 250;
	private static final String[] CHIP_LABELS = {"1k", "10k", "100k", "1M"};
	private static final long[] CHIP_VALUES = {1_000, 10_000, 100_000, 1_000_000};

	private final Map<Integer, CfLobbyReader.Row> rowsBySlot = new HashMap<>();
	private int scroll;
	private boolean creating;
	private String digits = "";
	private boolean allIn;
	private long confirmUntilMs;
	private long refreshAtMs;
	private long lastFrameMs;

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
	protected boolean looksLike(ScreenHandler handler) {
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
					Util.getMeasuringTimeMs());
		}
	}

	/**
	 * Typing goes to the form while it is open: digits, Backspace, Enter to create, Escape to
	 * close the form. Every other key is held too, so the inventory key can't close the menu
	 * out from under a half-typed wager.
	 */
	@Override
	protected boolean onKey(int keyCode) {
		if (!creating) {
			return false;
		}
		int digit = digitOf(keyCode);
		if (digit >= 0) {
			if (allIn) {
				allIn = false;
				digits = "";
			}
			if (digits.length() < MAX_DIGITS && !(digits.isEmpty() && digit == 0)) {
				digits += digit;
			}
			confirmUntilMs = 0;
		} else if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
			digits = allIn || digits.isEmpty() ? "" : digits.substring(0, digits.length() - 1);
			allIn = false;
			confirmUntilMs = 0;
		} else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			submit();
		} else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			closeForm();
		}
		return true;
	}

	private static int digitOf(int keyCode) {
		if (keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9) {
			return keyCode - GLFW.GLFW_KEY_0;
		}
		if (keyCode >= GLFW.GLFW_KEY_KP_0 && keyCode <= GLFW.GLFW_KEY_KP_9) {
			return keyCode - GLFW.GLFW_KEY_KP_0;
		}
		return -1;
	}

	@Override
	protected void draw(DrawContext context, TextRenderer font, List<SlotView> slots,
			String title, float deviceScale) {
		long now = Util.getMeasuringTimeMs();
		if (now - lastFrameMs > REOPEN_GAP_MS) {
			closeForm();
		}
		lastFrameMs = now;
		if (refreshAtMs > 0 && now >= refreshAtMs) {
			refreshAtMs = 0;
			clickSlot(CfLobbyReader.REFRESH_SLOT);
		}
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
		// The pair centred on the list it belongs to, not shoved against the rail divider.
		int pairX = LIST_X + (LIST_W - REFRESH_W * 2 - CREATE_GAP) / 2;
		button(context, font, pairX, REFRESH_Y, REFRESH_W,
				REFRESH_H, "REFRESH", CfLobbyReader.REFRESH_SLOT, accent());
		localButton(context, font, pairX + REFRESH_W + CREATE_GAP, REFRESH_Y, REFRESH_W, REFRESH_H,
				creating ? "CANCEL" : "CREATE", true, creating, creating ? this::closeForm : this::openForm);
		context.fill(RAIL_X - 6, CONTENT_Y, RAIL_X - 5, PANEL_H - PAD, DIVIDER);
		if (creating) {
			createForm(context, font, now);
		} else {
			rail(context, font);
		}
	}

	private void list(DrawContext context, TextRenderer font, List<CfLobbyReader.Row> rows) {
		if (rows.isEmpty()) {
			context.drawText(font, "nothing posted", LIST_X, CONTENT_Y + 4, TEXT_FAINT, false);
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
			context.drawText(font, more, LIST_X, FOOTER_Y, TEXT_FAINT, false);
			return;
		}
		hint(context, font);
	}

	private void hint(DrawContext context, TextRenderer font) {
		context.drawText(font, "/cf create <wager> <heads/tails>", LIST_X, FOOTER_Y,
				TEXT_FAINT, false);
	}

	private void row(DrawContext context, TextRenderer font, CfLobbyReader.Row row, int y) {
		boolean hot = hovered(LIST_X, y, LIST_W, ROW_H);
		if (hot) {
			context.fill(LIST_X, y, LIST_X + LIST_W, y + ROW_H, ROW_HOVER);
		}
		PlayerSkinDrawer.draw(context, PlayerSkinCache.skin(row.player()), LIST_X + 1, y + 1,
				HEAD);

		String wager = Money.compact(row.wagerCents());
		int nameW = LIST_W - LIST_GUTTER - NAME_X - WAGER_W - FACE_W - 4;
		context.drawText(font, font.trimToWidth(row.player(), nameW), LIST_X + NAME_X, y + 2,
				hot ? TEXT : TEXT_DIM, false);
		// One letter for the side you would be taking: the full word does not fit, and the
		// side is the one thing about a flip that changes nothing about its price.
		String side = row.yourFace().isEmpty() ? "" : row.yourFace().substring(0, 1)
				.toUpperCase(Locale.ROOT);
		int right = LIST_X + LIST_W - LIST_GUTTER;
		context.drawText(font, side, right - WAGER_W - FACE_W, y + 2,
				hot ? accent() : TEXT_FAINT, false);
		context.drawText(font, wager, right - font.getWidth(wager), y + 2,
				hot ? TEXT : TEXT_DIM, false);

		clickable(LIST_X, y, LIST_W, ROW_H, row.slot());
	}

	private void rail(DrawContext context, TextRenderer font) {
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
	private int stats(DrawContext context, TextRenderer font, int y) {
		CfStats.Record kept = CfStats.current();
		if (kept.isEmpty()) {
			context.drawText(font, CfStats.seeded() ? "no flips yet" : "run /cf stats",
					RAIL_X, y, TEXT_FAINT, false);
			return y + BLOCK_GAP;
		}
		String wins = String.valueOf(kept.won());
		String slash = "/";
		String losses = String.valueOf(kept.lost());
		int x = RAIL_X;
		context.drawText(font, wins, x, y, WIN, false);
		x += font.getWidth(wins);
		context.drawText(font, slash, x, y, TEXT_FAINT, false);
		x += font.getWidth(slash);
		context.drawText(font, losses, x, y, LOSS, false);
		String rate = kept.winRateText();
		context.drawText(font, rate, PANEL_W - PAD - font.getWidth(rate), y, TEXT_DIM, false);

		y += ROW_GAP;
		row(context, font, y, "profit", Money.compact(kept.profitCents()),
				kept.profitCents() >= 0 ? WIN : LOSS);
		return y + BLOCK_GAP;
	}

	/** Your own last few flips: what each one moved, and who moved it. */
	private void recent(DrawContext context, TextRenderer font, int y) {
		List<CfPlayed> played = CfStats.recent();
		if (played.isEmpty()) {
			context.drawText(font, "none yet", RAIL_X, y, TEXT_FAINT, false);
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
			context.drawText(font, amount, RAIL_X, y, flip.won ? WIN : LOSS, false);
			String who = font.trimToWidth(flip.opponent, width - RECENT_INDENT);
			context.drawText(font, who, RAIL_X + RECENT_INDENT, y + ROW_GAP, TEXT_FAINT, false);
			y += RECENT_ENTRY_H;
		}
	}

	// --- create form ---

	private void openForm() {
		creating = true;
		digits = "";
		allIn = false;
		confirmUntilMs = 0;
	}

	private void closeForm() {
		creating = false;
		confirmUntilMs = 0;
	}

	private static boolean heads() {
		return !"tails".equals(LabsAddonsConfig.get().coinflipLastSide);
	}

	private static void setSide(boolean heads) {
		LabsAddonsConfig.get().coinflipLastSide = heads ? "heads" : "tails";
		LabsAddonsConfig.get().save();
	}

	private long wager() {
		return digits.isEmpty() ? 0 : Long.parseLong(digits);
	}

	/** Why the wager can't be sent, or null when it can. */
	private String problem() {
		if (allIn) {
			return null;
		}
		long wager = wager();
		if (wager == 0) {
			return "";
		}
		if (wager < MIN_WAGER) {
			return "min " + Money.format(Money.fromDollars(MIN_WAGER));
		}
		return wager > MAX_WAGER ? "max " + Money.format(Money.fromDollars(MAX_WAGER)) : null;
	}

	/** First press asks, a second within {@link #CONFIRM_MS} sends. */
	private void submit() {
		if (problem() != null) {
			return;
		}
		long now = Util.getMeasuringTimeMs();
		if (now >= confirmUntilMs) {
			confirmUntilMs = now + CONFIRM_MS;
			return;
		}
		ClientPlayNetworkHandler network = MinecraftClient.getInstance().getNetworkHandler();
		if (network == null) {
			return;
		}
		network.sendChatCommand("cf create " + (allIn ? "balance" : String.valueOf(wager())) + " "
				+ (heads() ? "heads" : "tails"));
		closeForm();
		refreshAtMs = now + REFRESH_AFTER_MS;
	}

	/**
	 * The rail as a form: the amount, quick picks, the side, and a create button that asks
	 * once before it spends anything.
	 */
	private void createForm(DrawContext context, TextRenderer font, long now) {
		int x = RAIL_X;
		int w = PANEL_W - PAD - RAIL_X;
		int y = CONTENT_Y;
		caption(context, font, x, y, "NEW FLIP");
		y += ROW_GAP;

		// The amount box. It always has focus while the form is open, so the caret always blinks.
		context.fill(x, y, x + w, y + 14, ROW_HOVER);
		EditorPainter.outline(context, x, y, w, 14, accent());
		String shown = allIn ? "Balance" : digits.isEmpty() ? "" : Money.format(Money.fromDollars(wager()));
		if (shown.isEmpty()) {
			context.drawText(font, "type an amount", x + 4, y + 3, TEXT_FAINT, false);
		} else {
			context.drawText(font, shown, x + 4, y + 3, TEXT, false);
		}
		if (!allIn && (now / 500) % 2 == 0) {
			int caretX = x + 4 + (shown.isEmpty() ? 0 : font.getWidth(shown) + 1);
			context.fill(caretX, y + 3, caretX + 1, y + 11, TEXT);
		}
		y += 18;

		int chipW = (w - 4) / 3;
		for (int i = 0; i < CHIP_LABELS.length; i++) {
			long value = CHIP_VALUES[i];
			int cx = x + (i % 3) * (chipW + 2);
			int cy = y + (i / 3) * 14;
			boolean picked = !allIn && wager() == value;
			localButton(context, font, cx, cy, chipW, 12, CHIP_LABELS[i], true, picked, () -> {
				digits = String.valueOf(value);
				allIn = false;
				confirmUntilMs = 0;
			});
		}
		// Balance is last, and wide enough to read as the all-in it is.
		localButton(context, font, x + chipW + 2, y + 14, w - chipW - 2, 12, "Balance", true, allIn, () -> {
			allIn = true;
			confirmUntilMs = 0;
		});
		y += 32;

		boolean heads = heads();
		int sideW = (w - 2) / 2;
		localButton(context, font, x, y, sideW, 13, "HEADS", true, heads, () -> {
			setSide(true);
			confirmUntilMs = 0;
		});
		localButton(context, font, x + sideW + 2, y, w - sideW - 2, 13, "TAILS", true, !heads, () -> {
			setSide(false);
			confirmUntilMs = 0;
		});
		y += 19;

		String problem = problem();
		boolean confirming = problem == null && now < confirmUntilMs;
		if (confirming) {
			String what = (allIn ? "Balance" : Money.format(Money.fromDollars(wager()))) + " on " + (heads ? "Heads" : "Tails") + "?";
			context.drawText(font, font.trimToWidth(what, w), x, y, WARN, false);
		} else if (problem != null && !problem.isEmpty()) {
			context.drawText(font, problem, x, y, LOSS, false);
		} else if (allIn) {
			context.drawText(font, "your whole balance", x, y, TEXT_FAINT, false);
		}
		y += 12;
		localButton(context, font, x, y, w, 15, confirming ? "CONFIRM" : "CREATE", problem == null,
				confirming, this::submit);
		y += 20;
		context.drawText(font, "esc to cancel", x, y, TEXT_FAINT, false);
	}

	/**
	 * A control of the board's own. {@code lit} draws it selected (a picked chip, the chosen
	 * side, the confirm step); a dead one is drawn but does nothing.
	 */
	private void localButton(DrawContext context, TextRenderer font, int x, int y, int w, int h,
			String label, boolean live, boolean lit, Runnable run) {
		boolean hot = live && hovered(x, y, w, h);
		context.fill(x, y, x + w, y + h, lit ? shade(accent(), 0x40) : hot ? ROW_HOVER : PANEL);
		EditorPainter.outline(context, x, y, w, h, lit || hot ? accent() : live ? BUTTON_BORDER : DIVIDER);
		String fit = font.trimToWidth(label, w - 4);
		context.drawText(font, fit, x + (w - font.getWidth(fit)) / 2, y + (h - font.fontHeight) / 2 + 1,
				lit || hot ? 0xFFFFFFFF : live ? TEXT : TEXT_FAINT, false);
		if (live) {
			action(x, y, w, h, run);
		}
	}

	private int divider(DrawContext context, int y) {
		context.fill(RAIL_X, y, PANEL_W - PAD, y + 1, DIVIDER);
		return y + 5;
	}

	/** A label and a value across the rail, which is too narrow for the shared helper. */
	private void row(DrawContext context, TextRenderer font, int y, String label, String value,
			int valueColor) {
		if (!label.isEmpty()) {
			context.drawText(font, label, RAIL_X, y, TEXT_FAINT, false);
		}
		context.drawText(font, value, PANEL_W - PAD - font.getWidth(value), y, valueColor,
				false);
	}

	private static String nameOf(ScreenHandler handler, int slot) {
		if (slot < 0 || slot >= handler.slots.size()) {
			return "";
		}
		ItemStack stack = handler.slots.get(slot).getStack();
		return stack.isEmpty() ? "" : stack.getName().getString();
	}
}
