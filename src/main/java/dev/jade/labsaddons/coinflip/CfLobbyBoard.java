package dev.jade.labsaddons.coinflip;

import dev.jade.labsaddons.casino.CasinoPanel;
import dev.jade.labsaddons.casino.Money;
import dev.jade.labsaddons.casino.SlotView;
import dev.jade.labsaddons.config.LabsAddonsConfig;
import dev.jade.labsaddons.hud.PlayerSkinCache;
import dev.jade.labsaddons.hud.editor.EditorPainter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.util.Util;

import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
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
	/** Nine digits covers the $100M max; anything longer is already invalid. */
	private static final int MAX_DIGITS = 9;
	private static final long CONFIRM_MS = 4_000;
	/** How long after /cf create the lobby is refreshed, so the new flip is listed. */
	// ponytail: a fixed wait; key off the server's reply once its wording is known.
	private static final long REFRESH_AFTER_MS = 750;
	/** A gap this long between frames means the lobby was closed; the form starts fresh. */
	private static final long REOPEN_GAP_MS = 250;

	private final Map<Integer, CfLobbyReader.Row> rowsBySlot = new HashMap<>();
	private int scroll;
	private boolean creating;
	private String digits = "";
	private boolean allIn;
	private long confirmUntilMs;
	private long refreshAtMs;
	private long lastFrameMs;
	/** The cog's chip editor: four typed values, one focused, saved on Done. */
	private boolean editingChips;
	private int focusedChip;
	private final String[] chipDrafts = new String[CfChips.COUNT];

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
		if (editingChips) {
			return onChipKey(keyCode);
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
		} else if (keyCode == InputConstants.KEY_BACKSPACE) {
			digits = allIn || digits.isEmpty() ? "" : digits.substring(0, digits.length() - 1);
			allIn = false;
			confirmUntilMs = 0;
		} else if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
			submit();
		} else if (keyCode == InputConstants.KEY_ESCAPE) {
			closeForm();
		}
		return true;
	}

	/** Typing in the chip editor: into the focused chip; Enter saves, Escape discards. */
	private boolean onChipKey(int keyCode) {
		int digit = digitOf(keyCode);
		String draft = chipDrafts[focusedChip];
		if (digit >= 0) {
			if (draft.length() < MAX_DIGITS && !(draft.isEmpty() && digit == 0)) {
				chipDrafts[focusedChip] = draft + digit;
			}
		} else if (keyCode == InputConstants.KEY_BACKSPACE) {
			chipDrafts[focusedChip] = draft.isEmpty() ? "" : draft.substring(0, draft.length() - 1);
		} else if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
			saveChips();
		} else if (keyCode == InputConstants.KEY_ESCAPE) {
			editingChips = false;
		}
		return true;
	}

	/**
	 * Digit keys by name, not by range: 26.3's SDL scancodes put 0 after 9 (1 = 30 ... 9 = 38,
	 * 0 = 39), and the keypad likewise, so "KEY_0 to KEY_9" matched nothing there.
	 */
	private static final int[] DIGIT_KEYS = {
			InputConstants.KEY_0, InputConstants.KEY_1, InputConstants.KEY_2, InputConstants.KEY_3,
			InputConstants.KEY_4, InputConstants.KEY_5, InputConstants.KEY_6, InputConstants.KEY_7,
			InputConstants.KEY_8, InputConstants.KEY_9};
	private static final int[] NUMPAD_KEYS = {
			InputConstants.KEY_NUMPAD0, InputConstants.KEY_NUMPAD1, InputConstants.KEY_NUMPAD2,
			InputConstants.KEY_NUMPAD3, InputConstants.KEY_NUMPAD4, InputConstants.KEY_NUMPAD5,
			InputConstants.KEY_NUMPAD6, InputConstants.KEY_NUMPAD7, InputConstants.KEY_NUMPAD8,
			InputConstants.KEY_NUMPAD9};

	private static int digitOf(int keyCode) {
		for (int digit = 0; digit < 10; digit++) {
			if (keyCode == DIGIT_KEYS[digit] || keyCode == NUMPAD_KEYS[digit]) {
				return digit;
			}
		}
		return -1;
	}

	@Override
	protected void draw(GuiGraphicsExtractor context, Font font, List<SlotView> slots,
			String title, float deviceScale) {
		long now = Util.getMillis();
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

	// --- create form ---

	private void openForm() {
		creating = true;
		editingChips = false;
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
		if (wager < CfChips.MIN) {
			return "min " + Money.format(Money.fromDollars(CfChips.MIN));
		}
		return wager > CfChips.MAX ? "max " + Money.format(Money.fromDollars(CfChips.MAX)) : null;
	}

	/** First press asks, a second within {@link #CONFIRM_MS} sends. */
	private void submit() {
		if (editingChips || problem() != null) {
			return;
		}
		long now = Util.getMillis();
		if (now >= confirmUntilMs) {
			confirmUntilMs = now + CONFIRM_MS;
			return;
		}
		ClientPacketListener network = Minecraft.getInstance().getConnection();
		if (network == null) {
			return;
		}
		network.sendCommand("cf create " + (allIn ? "balance" : String.valueOf(wager())) + " "
				+ (heads() ? "heads" : "tails"));
		closeForm();
		refreshAtMs = now + REFRESH_AFTER_MS;
	}

	private static List<Long> chips() {
		return LabsAddonsConfig.get().coinflipChips;
	}

	private void editChips() {
		List<Long> chips = chips();
		for (int i = 0; i < CfChips.COUNT; i++) {
			chipDrafts[i] = String.valueOf(chips.get(i));
		}
		focusedChip = 0;
		editingChips = true;
	}

	/** Keeps the editor open if any value is out of range, so nothing half-valid is saved. */
	private void saveChips() {
		List<Long> values = new ArrayList<>();
		for (String draft : chipDrafts) {
			long value = draft.isEmpty() ? 0 : Long.parseLong(draft);
			if (!CfChips.valid(value)) {
				return;
			}
			values.add(value);
		}
		LabsAddonsConfig.get().coinflipChips = values;
		LabsAddonsConfig.get().save();
		editingChips = false;
	}

	private boolean draftsValid() {
		for (String draft : chipDrafts) {
			if (draft.isEmpty() || !CfChips.valid(Long.parseLong(draft))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * The rail as a form: the amount, chips that add to it, the side, and a create button
	 * that asks once before it spends anything. The cog swaps the chips for the editor that
	 * sets their values.
	 */
	private void createForm(GuiGraphicsExtractor context, Font font, long now) {
		int x = RAIL_X;
		int w = PANEL_W - PAD - RAIL_X;
		int y = CONTENT_Y;
		caption(context, font, x, y, "NEW FLIP");
		localButton(context, font, x + w - 13, y - 2, 13, 11, "⚙", true, editingChips,
				editingChips ? () -> editingChips = false : this::editChips);
		y += ROW_GAP;

		// The amount box. Focused unless the chip editor is open, so its caret blinks.
		context.fill(x, y, x + w, y + 14, ROW_HOVER);
		EditorPainter.outline(context, x, y, w, 14, editingChips ? BUTTON_BORDER : accent());
		String shown = allIn ? "Balance" : digits.isEmpty() ? "" : Money.format(Money.fromDollars(wager()));
		if (shown.isEmpty()) {
			context.text(font, "type an amount", x + 4, y + 3, TEXT_FAINT, false);
		} else {
			context.text(font, shown, x + 4, y + 3, TEXT, false);
		}
		if (!editingChips && !allIn && (now / 500) % 2 == 0) {
			int caretX = x + 4 + (shown.isEmpty() ? 0 : font.width(shown) + 1);
			context.fill(caretX, y + 3, caretX + 1, y + 11, TEXT);
		}
		y += 18;

		int chipW = (w - 2) / 2;
		List<Long> chips = chips();
		for (int i = 0; i < CfChips.COUNT; i++) {
			int cx = x + (i % 2) * (chipW + 2);
			int cy = y + (i / 2) * 14;
			if (editingChips) {
				chipBox(context, font, cx, cy, chipW, i, now);
			} else {
				long value = chips.get(i);
				localButton(context, font, cx, cy, chipW, 12, "+" + CfChips.label(value), true, false, () -> {
					digits = String.valueOf(CfChips.add(allIn ? 0 : wager(), value));
					allIn = false;
					confirmUntilMs = 0;
				});
			}
		}
		int rowY = y + 28;
		int halfW = (w - 2) / 2;
		if (editingChips) {
			localButton(context, font, x, rowY, halfW, 12, "Reset", true, false, () -> {
				for (int i = 0; i < CfChips.COUNT; i++) {
					chipDrafts[i] = String.valueOf(CfChips.DEFAULTS.get(i));
				}
			});
			localButton(context, font, x + halfW + 2, rowY, w - halfW - 2, 12, "Done", draftsValid(), false,
					this::saveChips);
		} else {
			// Balance is the all-in, wide enough to read as one; Clear starts the sum over.
			int clearW = 34;
			localButton(context, font, x, rowY, w - clearW - 2, 12, "Balance", true, allIn, () -> {
				allIn = true;
				confirmUntilMs = 0;
			});
			localButton(context, font, x + w - clearW, rowY, clearW, 12, "Clear", true, false, () -> {
				digits = "";
				allIn = false;
				confirmUntilMs = 0;
			});
		}
		y += 46;

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
		boolean confirming = !editingChips && problem == null && now < confirmUntilMs;
		if (editingChips) {
			context.text(font, draftsValid() ? "type each chip" : "each $200 – $100m",
					x, y, draftsValid() ? TEXT_FAINT : LOSS, false);
		} else if (confirming) {
			// The side is on the button, so the full amount always fits: "$100,000,000?" at most.
			String what = (allIn ? "Whole balance" : Money.format(Money.fromDollars(wager()))) + "?";
			context.text(font, what, x, y, WARN, false);
		} else if (problem != null && !problem.isEmpty()) {
			context.text(font, problem, x, y, LOSS, false);
		} else if (allIn) {
			context.text(font, "your whole balance", x, y, TEXT_FAINT, false);
		}
		y += 12;
		localButton(context, font, x, y, w, 15, confirming ? (heads ? "CONFIRM HEADS" : "CONFIRM TAILS") : "CREATE",
				!editingChips && problem == null, confirming, this::submit);
		y += 20;
		context.text(font, editingChips ? "esc to discard" : "esc to cancel", x, y, TEXT_FAINT, false);
	}

	/** One chip in the editor: a typed box that takes focus on click. */
	private void chipBox(GuiGraphicsExtractor context, Font font, int x, int y, int w, int index, long now) {
		boolean focused = index == focusedChip;
		String draft = chipDrafts[index];
		boolean ok = !draft.isEmpty() && CfChips.valid(Long.parseLong(draft));
		context.fill(x, y, x + w, y + 12, ROW_HOVER);
		EditorPainter.outline(context, x, y, w, 12, focused ? accent() : ok ? BUTTON_BORDER : LOSS);
		String shown = draft.isEmpty() ? "" : CfChips.label(Long.parseLong(draft));
		context.text(font, shown, x + 3, y + 2, ok ? TEXT : LOSS, false);
		if (focused && (now / 500) % 2 == 0) {
			int caretX = x + 3 + (shown.isEmpty() ? 0 : font.width(shown) + 1);
			context.fill(caretX, y + 2, caretX + 1, y + 10, TEXT);
		}
		action(x, y, w, 12, () -> focusedChip = index);
	}

	/**
	 * A control of the board's own. {@code lit} draws it selected (a picked chip, the chosen
	 * side, the confirm step); a dead one is drawn but does nothing.
	 */
	private void localButton(GuiGraphicsExtractor context, Font font, int x, int y, int w, int h,
			String label, boolean live, boolean lit, Runnable run) {
		boolean hot = live && hovered(x, y, w, h);
		context.fill(x, y, x + w, y + h, lit ? shade(accent(), 0x40) : hot ? ROW_HOVER : PANEL);
		EditorPainter.outline(context, x, y, w, h, lit || hot ? accent() : live ? BUTTON_BORDER : DIVIDER);
		String fit = font.plainSubstrByWidth(label, w - 4);
		context.text(font, fit, x + (w - font.width(fit)) / 2, y + (h - font.lineHeight) / 2 + 1,
				lit || hot ? 0xFFFFFFFF : live ? TEXT : TEXT_FAINT, false);
		if (live) {
			action(x, y, w, h, run);
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
