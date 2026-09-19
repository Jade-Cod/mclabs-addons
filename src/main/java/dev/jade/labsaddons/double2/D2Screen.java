package dev.jade.labsaddons.double2;

import dev.jade.labsaddons.config.LabsAddonsConfig;
import dev.jade.labsaddons.hud.HudObject;
import dev.jade.labsaddons.hud.editor.EditorPainter;
import dev.jade.labsaddons.hud.editor.EditorTheme;
import dev.jade.labsaddons.server.McLabsSession;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Double² board: drawn in place of the server's chest menu, clicking through to it.
 *
 * <p>The screen is never replaced. The board cancels the menu's own render and paints a
 * fixed panel on the ordinary Minecraft screen dim, and our controls call
 * {@code clickSlot} with the slot a player would have clicked — so the server sees an
 * ordinary click and the mod never has to know what a bet means.
 *
 * <p>Everything on screen is read from the container every frame, plus the payout line
 * from chat. The wheel is the server's own position, tweened only between its updates.
 */
public final class D2Screen {
	/** Base panel size in GUI pixels. Fixed, like the chest menu it stands in for. */
	private static final int PANEL_W = 288;
	private static final int PANEL_H = 176;
	private static final int PAD = 6;
	private static final int BAR_Y = 16;
	private static final int CONTENT_Y = 28;
	private static final int OUTER_R = 64;
	private static final int INNER_R = 34;
	private static final int LEFT_W = 136;
	private static final int ROW_H = 12;
	private static final int PANEL = 0xFF1A1D24;
	private static final int PANEL_BORDER = 0xFF23262E;
	private static final int BUTTON_BORDER = 0xFF454C5A;
	private static final int ROW_HOVER = 0xFF262A33;
	private static final int TRACK = 0xFF23262E;
	private static final int TEXT = 0xFFEAEEF3;
	private static final int TEXT_DIM = 0xFF9AA3AD;
	private static final int WIN = 0xFF96E03F;
	private static final int LOSS = 0xFFFF8080;
	/** Tween time constant: short enough to keep up with a one-tick step, long enough to smooth it. */
	private static final double TWEEN_TAU_MS = 40.0;
	/** Keep the monotonic wheel position small enough that float precision never bites. */
	private static final double WRAP_AT = 100_000.0;
	/** How long a click's local prediction may mask the slot it emptied. */
	private static final long PREDICTION_MS = 600L;
	/** The full betting window, for scaling the countdown bar. */
	private static final float ROUND_SECONDS = 60f;

	/**
	 * The chips, labelled as the server labels them. Three rows because "+$100,000" is
	 * fifty pixels of font on its own and will not share a row with four others.
	 */
	private static final String[][] CHIP_ROWS = {
			{"+$100", "+$1,000"},
			{"+$10,000", "+$100,000"},
			{"MAX"}
	};

	private record Hit(int x, int y, int w, int h, int slot) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private static final List<Hit> HITS = new ArrayList<>();
	/** The syncId the board is up for, or -1. Not a bare flag: it has to stop applying
	 *  the moment a different container opens, or that menu's first click is eaten. */
	private static int activeSyncId = -1;
	private static double turned;
	private static int lastOffset = -1;
	private static float drawOffset;
	private static long lastFrameMs;
	private static int lastGoodOffset = -1;
	private static List<D2Reader.SlotView> lastSlots;
	private static int predictedSlot = -1;
	private static D2Reader.SlotView predictedBefore;
	private static long predictedAtMs;
	private static int lastSeconds = -1;
	private static long secondsChangedMs;
	private static float hoverX;
	private static float hoverY;
	private static int panelX;
	private static int panelY;
	private static float panelScale = 1f;

	private D2Screen() {
	}

	/**
	 * Whether the board is standing in for this menu — asked by the hooks that run before
	 * it draws, so they can suppress the chest texture and the slot tooltips.
	 */
	public static boolean recognises(HandledScreen<?> screen) {
		int syncId = screen.getScreenHandler().syncId;
		if (activeSyncId >= 0 && activeSyncId == syncId) {
			return true;
		}
		// First frame for this container — and every frame of every other container in
		// the game, so it stays cheap: five slot names, not a full read of fifty-four.
		return LabsAddonsConfig.get().double2Overlay && McLabsSession.isActive()
				&& looksLikeDouble2(screen.getScreenHandler());
	}

	/** The five labs in slots 2-6, read straight off the handler. */
	private static boolean looksLikeDouble2(ScreenHandler handler) {
		if (handler.slots.size() < D2Reader.CONTAINER_SLOTS) {
			return false;
		}
		for (Lab lab : Lab.values()) {
			ItemStack stack = handler.slots.get(lab.pickSlot()).getStack();
			if (stack.isEmpty() || Lab.fromText(stack.getName().getString()) != lab) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Draws the board in place of the menu's own render.
	 *
	 * @return true when the board took over, so the caller cancels the vanilla render
	 */
	public static boolean render(HandledScreen<?> screen, DrawContext context,
			int mouseX, int mouseY) {
		HITS.clear();
		int syncId = screen.getScreenHandler().syncId;
		if (activeSyncId != syncId) {
			// A different container: forget where the wheel was, so reopening the menu
			// snaps to the live position instead of sweeping in from the last one.
			activeSyncId = -1;
			lastOffset = -1;
			lastFrameMs = 0L;
			lastGoodOffset = -1;
			lastSeconds = -1;
		}
		if (!LabsAddonsConfig.get().double2Overlay || !McLabsSession.isActive()) {
			return false;
		}
		List<D2Reader.SlotView> slots = slotViews(screen.getScreenHandler());
		if (!D2Reader.isDouble2(slots)) {
			return false;
		}
		lastSlots = slots;
		activeSyncId = syncId;
		D2State state = D2Reader.read(slots);
		MinecraftClient client = MinecraftClient.getInstance();
		TextRenderer font = client.textRenderer;
		int w = context.getScaledWindowWidth();
		int h = context.getScaledWindowHeight();
		int accent = EditorTheme.ACCENT;

		// No dim of our own: Minecraft has already drawn its own behind this, and a
		// second one on top of it reads as a doubled tint.
		panelScale = Math.min(1f, Math.min((w - 16f) / PANEL_W, (h - 16f) / PANEL_H));
		panelX = Math.round((w - PANEL_W * panelScale) / 2f);
		panelY = Math.round((h - PANEL_H * panelScale) / 2f);

		context.getMatrices().pushMatrix();
		context.getMatrices().translate(panelX, panelY);
		context.getMatrices().scale(panelScale, panelScale);
		hoverX = (mouseX - panelX) / panelScale;
		hoverY = (mouseY - panelY) / panelScale;

		HudObject.drawRoundedRect(context, 0, 0, PANEL_W, PANEL_H, EditorTheme.PANEL_BG);
		EditorPainter.outline(context, 0, 0, PANEL_W, PANEL_H, EditorTheme.PANEL_BORDER);

		header(context, font, state, accent);

		// Hold the last known position for a frame that cannot be placed, so a momentary
		// misread stalls the wheel instead of blanking it.
		int offset = state.ringOffset() >= 0 ? state.ringOffset() : lastGoodOffset;
		if (state.ringOffset() >= 0) {
			lastGoodOffset = state.ringOffset();
		}
		int wheelCx = PAD + LEFT_W / 2;
		int wheelCy = CONTENT_Y + OUTER_R;
		if (offset >= 0) {
			D2Wheel.draw(context, font, wheelCx, wheelCy, OUTER_R, INNER_R,
					tween(offset), accent, hubText(state, offset, font),
					client.getWindow().getScaleFactor() * panelScale);
		} else {
			// Every pane is a lab or the board would not be up, so this means the ring
			// itself has changed under us. Drawing no wheel beats drawing a wrong one.
			String note = "wheel changed";
			context.drawText(font, note, wheelCx - font.getWidth(note) / 2, wheelCy,
					TEXT_DIM, false);
		}

		panel(context, font, state, PAD + LEFT_W + 6, CONTENT_Y,
				PANEL_W - (PAD + LEFT_W + 6) - PAD, accent);

		context.getMatrices().popMatrix();
		return true;
	}

	/** Title, phase and the closing countdown, across the top of the panel. */
	private static void header(DrawContext context, TextRenderer font, D2State state, int accent) {
		context.drawText(font, "DOUBLE²", PAD, 5, 0xFFFFFFFF, false);
		String phase = switch (state.phase()) {
			case BETTING -> "INVESTING OPEN";
			case SPINNING -> "MARKET CLOSED";
			case SETTLED -> "ROUND SETTLED";
		};
		context.drawText(font, phase, PANEL_W - PAD - font.getWidth(phase), 5,
				state.phase() == D2State.Phase.BETTING ? WIN : accent, false);

		int barW = PANEL_W - PAD * 2;
		float remaining = state.phase() == D2State.Phase.BETTING
				? smoothSeconds(state.secondsLeft())
				: ROUND_SECONDS;
		int filled = Math.clamp(Math.round(remaining / ROUND_SECONDS * barW), 0, barW);
		context.fill(PAD, BAR_Y, PAD + barW, BAR_Y + 2, TRACK);
		context.fill(PAD, BAR_Y, PAD + filled, BAR_Y + 2, accent);
	}

	/**
	 * The countdown, drained smoothly between the server's whole-second updates. The
	 * server only ever states an integer, so the bar would otherwise step once a second.
	 */
	private static float smoothSeconds(int seconds) {
		if (seconds < 0) {
			return ROUND_SECONDS;
		}
		long now = Util.getMeasuringTimeMs();
		if (seconds != lastSeconds) {
			lastSeconds = seconds;
			secondsChangedMs = now;
		}
		float since = Math.min(1f, (now - secondsChangedMs) / 1000f);
		return Math.max(0f, seconds - since);
	}

	/**
	 * The ring position to draw. The server steps one segment per update; this eases
	 * towards it so the wheel reads smoothly however fast the client is drawing, without
	 * inventing any motion the server did not send.
	 */
	private static float tween(int offset) {
		if (lastOffset < 0) {
			turned = offset;
			drawOffset = offset;
		} else {
			turned += Math.floorMod(offset - lastOffset, D2Ring.SIZE);
		}
		lastOffset = offset;

		long now = Util.getMeasuringTimeMs();
		double dt = lastFrameMs == 0L ? 16.0 : Math.min(160.0, now - lastFrameMs);
		lastFrameMs = now;
		drawOffset += (float) ((turned - drawOffset) * (1.0 - Math.exp(-dt / TWEEN_TAU_MS)));

		if (turned > WRAP_AT) {
			double laps = Math.floor(turned / D2Ring.SIZE) * D2Ring.SIZE;
			turned -= laps;
			drawOffset -= (float) laps;
		}
		return drawOffset;
	}

	private static String[] hubText(D2State state, int offset, TextRenderer font) {
		Lab pointer = D2Ring.pointerLab(offset);
		String note = switch (state.phase()) {
			case BETTING -> state.secondsLeft() >= 0 ? state.secondsLeft() + "s left" : null;
			case SPINNING -> "simulating";
			case SETTLED -> "profiting lab";
		};
		// A small hub has room for the code alone; drop the rest rather than overflow it.
		if (INNER_R < font.fontHeight * 3) {
			return new String[]{pointer.name()};
		}
		return new String[]{pointer.name(), pointer.multiplierText(), note};
	}

	private static void panel(DrawContext context, TextRenderer font, D2State state,
			int x, int y, int width, int accent) {
		int line = font.fontHeight;
		long potTotal = state.potTotal();

		for (Lab lab : Lab.values()) {
			boolean picked = state.selected() == lab;
			boolean hot = hovered(x, y, width, ROW_H - 1);
			context.fill(x, y, x + width, y + ROW_H - 1,
					picked ? blend(accent) : hot ? ROW_HOVER : PANEL);

			// That lab's share of the pot, as a bar behind the row rather than a figure
			// in it. A figure here reads as your own money when it is in fact everyone's,
			// and the exact split changes nothing — the odds are fixed. Your own stake is
			// stated once, below, where it cannot be mistaken for this.
			long staked = state.pot().getOrDefault(lab, 0L);
			if (staked > 0 && potTotal > 0) {
				int barW = (int) Math.round((double) staked / potTotal * (width - 2));
				if (barW > 0) {
					context.fill(x + 1, y + 1, x + 1 + barW, y + ROW_H - 2, shade(lab.color()));
				}
			}

			EditorPainter.outline(context, x, y, width, ROW_H - 1,
					picked || hot ? accent : BUTTON_BORDER);
			context.fill(x + 1, y + 1, x + 3, y + ROW_H - 2, lab.color());
			context.drawText(font, lab.glyph(), x + 5, y + 2, lab.lift(), false);
			// The multiplier is the one permanent fact about a lab, so it always shows.
			String mult = lab.multiplierText();
			String name = font.trimToWidth(lab.shortName(), width - 21 - font.getWidth(mult));
			context.drawText(font, name, x + 15, y + 2, picked || hot ? 0xFFFFFFFF : TEXT, false);
			context.drawText(font, mult, x + width - font.getWidth(mult) - 3, y + 2,
					picked || hot ? TEXT : TEXT_DIM, false);
			HITS.add(new Hit(x, y, width, ROW_H - 1, lab.pickSlot()));
			y += ROW_H;
		}
		y += 3;

		// Each chip invests its amount the moment it is clicked — the server has no
		// confirm step, so there is no button here for one.
		int chip = D2Reader.CHIP_FIRST;
		for (String[] row : CHIP_ROWS) {
			int gap = 2;
			int chipW = (width - gap * (row.length - 1)) / row.length;
			for (int i = 0; i < row.length; i++) {
				int chipX = x + i * (chipW + gap);
				boolean hot = hovered(chipX, y, chipW, ROW_H);
				context.fill(chipX, y, chipX + chipW, y + ROW_H, hot ? ROW_HOVER : PANEL);
				EditorPainter.outline(context, chipX, y, chipW, ROW_H,
						hot ? accent : BUTTON_BORDER);
				String label = font.trimToWidth(row[i], chipW - 4);
				context.drawText(font, label, chipX + (chipW - font.getWidth(label)) / 2,
						y + 2, hot ? 0xFFFFFFFF : TEXT, false);
				HITS.add(new Hit(chipX, y, chipW, ROW_H, chip++));
			}
			y += ROW_H + 2;
		}
		y += 2;

		context.drawText(font, "INVESTED", x, y, TEXT_DIM, false);
		String staked = state.hasBet()
				? money(state.betAmount()) + " " + state.betLab().name()
				: money(state.stake());
		context.drawText(font, staked, x + width - font.getWidth(staked), y,
				state.stake() > 0 ? 0xFFFFFFFF : TEXT_DIM, false);
		y += line + 4;

		context.fill(x, y, x + width, y + 1, PANEL_BORDER);
		y += 4;
		String pot = "POT " + money(potTotal);
		context.drawText(font, pot, x, y, TEXT, false);
		// Spell it out when there is room; a very large pot takes the room back.
		String who = state.investors() + " investing";
		if (font.getWidth(pot) + font.getWidth(who) + 6 > width) {
			who = state.investors() + " in";
		}
		context.drawText(font, who, x + width - font.getWidth(who), y, TEXT_DIM, false);
		y += line + 2;

		// One line, shared: the drought while betting, the result once it is known.
		if (state.phase() == D2State.Phase.SETTLED && state.settledLab() != null) {
			String result = state.settledLab().name() + " profited";
			context.drawText(font, result, x, y, state.settledLab().lift(), false);
			D2Chat.Outcome outcome = D2Chat.lastOutcome();
			if (outcome != null) {
				// The server's own figure: the credit is not the face multiplier and we
				// do not yet know the exact cut.
				String settle = (outcome.won() ? "+" : "−") + money(outcome.amount());
				context.drawText(font, settle, x + width - font.getWidth(settle), y,
						outcome.won() ? WIN : LOSS, false);
			}
		} else if (state.eolDrought() >= 0) {
			context.drawText(font, "EOL dry", x, y, TEXT_DIM, false);
			String rounds = state.eolDrought() + " rounds";
			context.drawText(font, rounds, x + width - font.getWidth(rounds), y, TEXT_DIM, false);
		}
	}

	/** Whether the cursor is over this rectangle, in panel coordinates. */
	private static boolean hovered(int x, int y, int w, int h) {
		return hoverX >= x && hoverX < x + w && hoverY >= y && hoverY < y + h;
	}

	/** True when the click was over our board, so the hidden vanilla slots stay untouched. */
	public static boolean mouseClicked(HandledScreen<?> screen, double mouseX, double mouseY) {
		if (activeSyncId < 0 || activeSyncId != screen.getScreenHandler().syncId) {
			return false;
		}
		double localX = (mouseX - panelX) / panelScale;
		double localY = (mouseY - panelY) / panelScale;
		for (Hit hit : HITS) {
			if (hit.contains(localX, localY)) {
				sendClick(screen, hit.slot());
				return true;
			}
		}
		// Swallowed even with nothing under the cursor: the real slots are still live
		// behind the board, and a stray click on an invisible one would place a bet.
		return true;
	}

	private static void sendClick(HandledScreen<?> screen, int slot) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.interactionManager == null || client.player == null) {
			return;
		}
		// The client predicts the click by emptying the slot into the cursor. On a lab or
		// chip that reads as "not the Double² menu" for the frame or two before the server
		// corrects it — which showed up as the whole board flashing on every click. Keep
		// what the slot held and put it back until the real contents return.
		if (lastSlots != null && slot < lastSlots.size()) {
			predictedSlot = slot;
			predictedBefore = lastSlots.get(slot);
			predictedAtMs = Util.getMeasuringTimeMs();
		}
		client.interactionManager.clickSlot(screen.getScreenHandler().syncId, slot, 0,
				SlotActionType.PICKUP, client.player);
	}

	private static List<D2Reader.SlotView> slotViews(ScreenHandler handler) {
		int count = Math.min(D2Reader.CONTAINER_SLOTS, handler.slots.size());
		List<D2Reader.SlotView> out = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			ItemStack stack = handler.slots.get(i).getStack();
			List<String> lore = List.of();
			String name = "";
			if (!stack.isEmpty()) {
				name = stack.getName().getString();
				LoreComponent component = stack.get(DataComponentTypes.LORE);
				if (component != null) {
					lore = component.lines().stream().map(Text::getString).toList();
				}
			}
			out.add(new D2Reader.SlotView(i, name, lore, stack.getCount()));
		}
		return unpredict(out);
	}

	/**
	 * Undoes the local pickup prediction on the slot we last clicked, until the server's
	 * own contents come back or the window lapses. Only ever substitutes for a slot that
	 * reads empty, so a genuine change is never masked.
	 */
	private static List<D2Reader.SlotView> unpredict(List<D2Reader.SlotView> slots) {
		if (predictedSlot < 0 || predictedSlot >= slots.size()) {
			return slots;
		}
		if (Util.getMeasuringTimeMs() - predictedAtMs > PREDICTION_MS
				|| !slots.get(predictedSlot).name().isEmpty()) {
			predictedSlot = -1;
			predictedBefore = null;
			return slots;
		}
		if (predictedBefore != null) {
			slots.set(predictedSlot, predictedBefore);
		}
		return slots;
	}

	private static String money(long amount) {
		return "$" + String.format(Locale.ROOT, "%,d", amount);
	}

	/** A lab colour at the weight a background bar wants. */
	private static int shade(int argb) {
		return (0x3C << 24) | (argb & 0x00FFFFFF);
	}

	private static int blend(int accent) {
		return (0x2E << 24) | (accent & 0x00FFFFFF);
	}
}
