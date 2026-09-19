package dev.jade.labsaddons.double2;

import dev.jade.labsaddons.casino.CasinoPanel;
import dev.jade.labsaddons.casino.SlotView;
import dev.jade.labsaddons.config.LabsAddonsConfig;
import dev.jade.labsaddons.hud.editor.EditorPainter;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.Util;

import java.util.List;
import java.util.Locale;

/**
 * The Double² board: drawn in place of the server's chest menu, clicking through to it.
 *
 * <p>{@link CasinoPanel} owns the parts every casino board shares — standing in for the
 * menu, surviving its re-sends, and forwarding clicks. What is left here is the wheel and
 * the lab panel.
 *
 * <p>Everything on screen is read from the container every frame, plus the payout line
 * from chat. The wheel is the server's own position, tweened only between its updates.
 */
public final class D2Screen extends CasinoPanel {
	public static final D2Screen INSTANCE = new D2Screen();

	private static final int BAR_Y = 16;
	/** The wheel and the lab panel start below the countdown bar, not at the base's y. */
	private static final int WHEEL_TOP = 28;
	private static final int OUTER_R = 64;
	private static final int INNER_R = 34;
	private static final int LEFT_W = 136;
	/** Tween time constant: short enough to keep up with a one-tick step, long enough to smooth it. */
	private static final double TWEEN_TAU_MS = 40.0;
	/** Keep the monotonic wheel position small enough that float precision never bites. */
	private static final double WRAP_AT = 100_000.0;
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

	private double turned;
	private int lastOffset = -1;
	private float drawOffset;
	private long lastFrameMs;
	private int lastGoodOffset = -1;
	private int lastSeconds = -1;
	private long secondsChangedMs;

	private D2Screen() {
	}

	@Override
	protected boolean enabled() {
		return LabsAddonsConfig.get().double2Overlay;
	}

	/** The five labs in slots 2-6, read straight off the handler. */
	@Override
	protected boolean looksLike(ScreenHandler handler) {
		for (Lab lab : Lab.values()) {
			ItemStack stack = handler.slots.get(lab.pickSlot()).getStack();
			if (stack.isEmpty() || Lab.fromText(stack.getName().getString()) != lab) {
				return false;
			}
		}
		return true;
	}

	@Override
	protected boolean parses(List<SlotView> slots) {
		return D2Reader.isDouble2(slots);
	}

	/**
	 * A different container: forget where the wheel was, so reopening the menu snaps to
	 * the live position instead of sweeping in from the last one.
	 */
	@Override
	protected void onContainerChange() {
		lastOffset = -1;
		lastFrameMs = 0L;
		lastGoodOffset = -1;
		lastSeconds = -1;
		D2Ring.reset();
	}

	@Override
	protected void draw(DrawContext context, TextRenderer font, List<SlotView> slots,
			String title, float deviceScale) {
		D2State state = D2Reader.read(slots);
		int accent = accent();

		topBar(context, font, state, accent);

		// The wheel places itself against what it has learned; a frame it cannot place
		// holds the last position rather than blanking.
		int placed = D2Ring.observe(state.window());
		int offset = placed >= 0 ? placed : lastGoodOffset;
		if (placed >= 0) {
			lastGoodOffset = placed;
		}
		int wheelCx = PAD + LEFT_W / 2;
		int wheelCy = WHEEL_TOP + OUTER_R;
		if (offset >= 0) {
			D2Wheel.draw(context, font, wheelCx, wheelCy, OUTER_R, INNER_R,
					D2Ring.segments(), tween(offset), accent, hubText(state, font),
					deviceScale);
			if (!D2Ring.isComplete()) {
				// Say why part of it is grey, so it does not read as a fault.
				String note = "learning the wheel";
				context.drawText(font, note, wheelCx - font.getWidth(note) / 2,
						wheelCy + OUTER_R + 5, TEXT_DIM, false);
			}
		}

		panel(context, font, state, PAD + LEFT_W + 6, WHEEL_TOP,
				PANEL_W - (PAD + LEFT_W + 6) - PAD, accent);
	}

	/** Title, phase and the closing countdown, across the top of the panel. */
	private void topBar(DrawContext context, TextRenderer font, D2State state, int accent) {
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
	private float smoothSeconds(int seconds) {
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
	private float tween(int offset) {
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

	private static String[] hubText(D2State state, TextRenderer font) {
		Lab pointer = state.pointerLab();
		if (pointer == null) {
			return null;
		}
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

	private void panel(DrawContext context, TextRenderer font, D2State state,
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
					context.fill(x + 1, y + 1, x + 1 + barW, y + ROW_H - 2,
							shade(lab.color(), 0x3C));
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
			clickable(x, y, width, ROW_H - 1, lab.pickSlot());
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
				button(context, font, chipX, y, chipW, ROW_H, row[i], chip++, accent);
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

		context.fill(x, y, x + width, y + 1, DIVIDER);
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

	private static String money(long amount) {
		return "$" + String.format(Locale.ROOT, "%,d", amount);
	}

	private static int blend(int accent) {
		return (0x2E << 24) | (accent & 0x00FFFFFF);
	}
}
