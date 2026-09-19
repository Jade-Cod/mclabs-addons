package dev.jade.labsaddons.blackjack;

import dev.jade.labsaddons.casino.CasinoPanel;
import dev.jade.labsaddons.casino.Money;
import dev.jade.labsaddons.casino.SlotView;
import dev.jade.labsaddons.config.LabsAddonsConfig;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;

import net.minecraft.util.Util;

import java.util.Arrays;
import java.util.List;

/**
 * The BondJoules board: two hands of real playing cards, the totals large enough to read
 * at a glance, and the exact chance one more card busts you.
 *
 * <p>The buttons keep the server's own words — Energize, Finalize, Double Down — because
 * those are what the chat messages and the tooltips call them. They are drawn dead rather
 * than hidden while the deal is running, so the panel does not reflow under the cursor.
 */
public final class BjBoard extends CasinoPanel {
	public static final BjBoard INSTANCE = new BjBoard();

	private static final int LAB_CAPTION_Y = 21;
	private static final int LAB_CARDS_Y = 32;
	private static final int DIVIDER_Y = 67;
	private static final int YOUR_CAPTION_Y = 72;
	private static final int YOUR_CARDS_Y = 83;
	private static final int ODDS_Y = 118;
	private static final int BUTTONS_Y = 132;
	private static final int BUTTON_H = 16;
	private static final int BUTTON_GAP = 4;
	private static final int RESULT_Y = 154;
	private static final int TOTAL_SCALE = 2;
	/** How long a card takes to reach its place, or to turn over. */
	private static final long DEAL_MS = 220L;
	/** Nobody reaches 21 in more than ten cards, and the array costs nothing. */
	private static final int MAX_CARDS = 12;

	private final Seat you = new Seat();
	private final Seat lab = new Seat();

	/**
	 * One seat's cards and when each of them arrived.
	 *
	 * <p>Keyed to the cards themselves rather than to the container, because BondJoules
	 * reopens the chest under a new id for every single action — four times over just to
	 * deal a hand. Anything tied to the container would restart the deal each time.
	 */
	private static final class Seat {
		private List<Card> cards = List.of();
		private final long[] arrivedAt = new long[MAX_CARDS];
		/** True for a card that turned over where it lay, rather than being dealt. */
		private final boolean[] turnedOver = new boolean[MAX_CARDS];

		void track(List<Card> now) {
			long clock = Util.getMeasuringTimeMs();
			if (now.size() < cards.size()) {
				// A shorter hand than last time can only be a new one.
				Arrays.fill(arrivedAt, 0L);
				Arrays.fill(turnedOver, false);
			} else {
				for (int i = cards.size(); i < Math.min(now.size(), MAX_CARDS); i++) {
					arrivedAt[i] = clock;
					turnedOver[i] = false;
				}
				// The hole card coming up is a reveal, not a deal, so it turns in place.
				int shared = Math.min(Math.min(cards.size(), now.size()), MAX_CARDS);
				for (int i = 0; i < shared; i++) {
					if (cards.get(i).isHidden() && !now.get(i).isHidden()) {
						arrivedAt[i] = clock;
						turnedOver[i] = true;
					}
				}
			}
			cards = now;
		}

		long arrivedAt(int index) {
			return index < MAX_CARDS ? arrivedAt[index] : 0L;
		}

		boolean turnedOver(int index) {
			return index < MAX_CARDS && turnedOver[index];
		}
	}

	private BjBoard() {
	}

	@Override
	protected boolean enabled() {
		return LabsAddonsConfig.get().blackjackOverlay;
	}

	@Override
	protected boolean looksLike(ScreenHandler handler) {
		return BjReader.looksLikeBlackjack(nameOf(handler, BjReader.LAB_SEAT_SLOT),
				nameOf(handler, BjReader.YOUR_SEAT_SLOT));
	}

	private static String nameOf(ScreenHandler handler, int slot) {
		ItemStack stack = handler.slots.get(slot).getStack();
		return stack.isEmpty() ? "" : stack.getName().getString();
	}

	@Override
	protected boolean parses(List<SlotView> slots) {
		return BjReader.isBlackjack(slots);
	}

	/**
	 * "BondJoules ($3,100)", without the "[16 - 10]" the server appends — that changes
	 * with every card, and a hand that is still the same hand has to read as one.
	 */
	@Override
	protected String holdKey(String title) {
		return BjReader.menuKey(title);
	}

	@Override
	protected void draw(DrawContext context, TextRenderer font, List<SlotView> slots,
			String title, float deviceScale) {
		BjState state = BjReader.read(slots, title);
		int accent = accent();
		int width = PANEL_W - PAD * 2;
		you.track(state.yourHand());
		lab.track(state.labHand());

		header(context, font, "BONDJOULES", stakeLine(state), TEXT_DIM);

		// The lab's total is only ever what it is showing: its hole card is face down and
		// the title counts only the cards on the table.
		caption(context, font, PAD, LAB_CAPTION_Y, "COMPETING LAB");
		hand(context, font, PAD, LAB_CARDS_Y, lab);
		total(context, font, state.labTotal(), LAB_CARDS_Y + 6, TEXT,
				state.labHand().size() > 1 && state.labHand().get(1).isHidden()
						? "showing" : null);

		context.fill(PAD, DIVIDER_Y, PANEL_W - PAD, DIVIDER_Y + 1, DIVIDER);

		caption(context, font, PAD, YOUR_CAPTION_Y, "YOU");
		hand(context, font, PAD, YOUR_CARDS_Y, you);
		total(context, font, state.yourTotal(), YOUR_CARDS_Y + 6,
				state.yourTotal() > 21 ? LOSS : accent, state.soft() ? "soft" : "hard");

		odds(context, font, state);

		if (state.settled()) {
			banner(context, font, state, width);
		} else {
			controls(context, font, state, width, accent);
		}
		result(context, font, state, width);
	}

	/**
	 * Draws a seat's cards, each overlapping the last.
	 *
	 * <p>A newly dealt card slides in from the far side of the panel; a hole card turning
	 * over squeezes edge-on and comes back as itself. Neither invents anything — the cards
	 * are the server's, only the moment they appear to move is ours.
	 */
	private void hand(DrawContext context, TextRenderer font, int x, int y, Seat seat) {
		long now = Util.getMeasuringTimeMs();
		for (int i = 0; i < seat.cards.size(); i++) {
			Card card = seat.cards.get(i);
			int home = x + i * CardPainter.STEP;
			long arrived = seat.arrivedAt(i);
			float t = arrived == 0L ? 1f
					: Math.clamp((now - arrived) / (float) DEAL_MS, 0f, 1f);
			if (t >= 1f) {
				CardPainter.draw(context, font, home, y, card);
			} else if (seat.turnedOver(i)) {
				turnOver(context, font, home, y, card, t);
			} else {
				// Never starts outside the panel: there is no clip here, and a card drawn
				// past the edge would be drawn over the world.
				int from = PANEL_W - PAD - CardPainter.CARD_W;
				float ease = 1f - (1f - t) * (1f - t) * (1f - t);
				CardPainter.draw(context, font,
						Math.round(from + (home - from) * ease), y, card);
			}
		}
	}

	private void turnOver(DrawContext context, TextRenderer font, int x, int y, Card card,
			float t) {
		float squeeze = Math.max(0.02f, Math.abs(t * 2f - 1f));
		context.getMatrices().pushMatrix();
		context.getMatrices().translate(x + CardPainter.CARD_W / 2f, 0f);
		context.getMatrices().scale(squeeze, 1f);
		context.getMatrices().translate(-(x + CardPainter.CARD_W / 2f), 0f);
		CardPainter.draw(context, font, x, y, t >= 0.5f ? card : Card.HIDDEN);
		context.getMatrices().popMatrix();
	}

	/** The stake, or the raised one once a double down has been confirmed in chat. */
	private static String stakeLine(BjState state) {
		long doubled = BjChat.doubledStakeCents();
		if (doubled > state.stakeCents()) {
			// The title keeps stating the original figure after a double, so this is the
			// only place the raised stake is known.
			return Money.format(doubled) + " doubled";
		}
		return state.stakeText();
	}

	/**
	 * A hand total, right-aligned and at twice the font size — it is the number the whole
	 * board exists to show, and at one scale it disappears next to the cards.
	 */
	private void total(DrawContext context, TextRenderer font, int value, int y, int color,
			String note) {
		if (value <= 0) {
			return;
		}
		String text = String.valueOf(value);
		int drawnW = font.getWidth(text) * TOTAL_SCALE;
		int x = PANEL_W - PAD - drawnW;
		context.getMatrices().pushMatrix();
		context.getMatrices().translate(x, y);
		context.getMatrices().scale(TOTAL_SCALE, TOTAL_SCALE);
		context.drawText(font, text, 0, 0, color, false);
		context.getMatrices().popMatrix();
		if (note != null) {
			context.drawText(font, note, PANEL_W - PAD - font.getWidth(note),
					y + font.fontHeight * TOTAL_SCALE, TEXT_FAINT, false);
		}
	}

	/** The one computed figure on this board, and the dealer rule behind it. */
	private void odds(DrawContext context, TextRenderer font, BjState state) {
		if (state.settled() || state.yourTotal() <= 0) {
			return;
		}
		if (state.yourTotal() > 21) {
			context.drawText(font, "bust", PAD, ODDS_Y, LOSS, false);
			return;
		}
		context.drawText(font, "draw busts", PAD, ODDS_Y, TEXT_DIM, false);
		int x = PAD + font.getWidth("draw busts ");
		String chance = state.bustText();
		context.drawText(font, chance, x, ODDS_Y, state.soft() ? WIN : LOSS, false);
		String tail = " · lab draws to 17";
		context.drawText(font, tail, x + font.getWidth(chance), ODDS_Y, TEXT_DIM, false);
	}

	private void controls(DrawContext context, TextRenderer font, BjState state, int width,
			int accent) {
		int buttonW = (width - BUTTON_GAP * 2) / 3;
		button(context, font, PAD, BUTTONS_Y, buttonW, BUTTON_H, "ENERGIZE",
				state.canHit() ? BjReader.HIT_SLOT : -1, accent);
		button(context, font, PAD + buttonW + BUTTON_GAP, BUTTONS_Y, buttonW, BUTTON_H,
				"FINALIZE", state.canStand() ? BjReader.STAND_SLOT : -1, accent);
		button(context, font, PAD + (buttonW + BUTTON_GAP) * 2, BUTTONS_Y,
				width - (buttonW + BUTTON_GAP) * 2, BUTTON_H, "DOUBLE",
				state.canDouble() ? BjReader.DOUBLE_SLOT : -1, accent);
	}

	/** The server's own settled words, centred where the buttons were. */
	private void banner(DrawContext context, TextRenderer font, BjState state, int width) {
		int color = switch (state.phase()) {
			case WON -> WIN;
			case LOST -> LOSS;
			default -> TEXT_DIM;
		};
		context.fill(PAD, BUTTONS_Y, PANEL_W - PAD, BUTTONS_Y + BUTTON_H, PANEL);
		String text = font.trimToWidth(state.banner(), width - 6);
		context.drawText(font, text, PAD + (width - font.getWidth(text)) / 2,
				BUTTONS_Y + (BUTTON_H - font.fontHeight) / 2 + 1, color, false);
	}

	/**
	 * What the hand paid. The band names a figure only on a win, so this comes from chat,
	 * which states all three outcomes.
	 */
	private void result(DrawContext context, TextRenderer font, BjState state, int width) {
		if (!state.settled()) {
			return;
		}
		BjChat.Outcome outcome = BjChat.lastOutcome();
		if (outcome == null) {
			return;
		}
		String label;
		String value;
		int color;
		if (outcome.push()) {
			label = "neutralized";
			value = "deposit back";
			color = TEXT_DIM;
		} else {
			label = outcome.won() ? "returned" : "lost";
			value = (outcome.won() ? "+" : "−") + Money.format(outcome.amountCents());
			color = outcome.won() ? WIN : LOSS;
		}
		row(context, font, PAD, RESULT_Y, width, label, value, TEXT_DIM, color);
	}
}
