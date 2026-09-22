package dev.jade.labsaddons.blackjack;

import dev.jade.labsaddons.casino.CasinoPanel;
import dev.jade.labsaddons.casino.Money;
import dev.jade.labsaddons.casino.SlotView;
import dev.jade.labsaddons.config.LabsAddonsConfig;
import dev.jade.labsaddons.hud.editor.EditorPainter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;

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
			long clock = Util.getMillis();
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
	protected boolean looksLike(AbstractContainerMenu handler) {
		return BjReader.looksLikeBlackjack(nameOf(handler, BjReader.LAB_SEAT_SLOT),
				nameOf(handler, BjReader.YOUR_SEAT_SLOT));
	}

	private static String nameOf(AbstractContainerMenu handler, int slot) {
		ItemStack stack = handler.slots.get(slot).getItem();
		return stack.isEmpty() ? "" : stack.getHoverName().getString();
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
	protected void draw(GuiGraphicsExtractor context, Font font, List<SlotView> slots,
			String title, float deviceScale) {
		BjState state = BjReader.read(slots, title);
		int accent = accent();
		int width = PANEL_W - PAD * 2;
		you.track(state.yourHand());
		lab.track(state.labHand());

		header(context, font, "BONDJOULES", stakeLine(state), TEXT_DIM);

		boolean natural21 = state.yourHand().size() == 2 && state.yourTotal() == 21;
		int playerGlow = natural21 ? 0xFFE6A817
				: state.phase() == BjState.Phase.WON ? WIN
				: state.yourTotal() > 21 ? LOSS
				: 0;
		int labGlow = state.phase() == BjState.Phase.LOST ? WIN : 0;

		caption(context, font, PAD, LAB_CAPTION_Y, "COMPETING LAB");
		hand(context, font, PAD, LAB_CARDS_Y, lab, labGlow);
		total(context, font, state.labTotal(), LAB_CARDS_Y + 6, TEXT,
				state.labHand().size() > 1 && state.labHand().get(1).isHidden()
						? "showing" : null, false);

		context.fill(PAD, DIVIDER_Y, PANEL_W - PAD, DIVIDER_Y + 1, DIVIDER);

		caption(context, font, PAD, YOUR_CAPTION_Y, "YOU");
		hand(context, font, PAD, YOUR_CARDS_Y, you, playerGlow);

		String yourNote = state.yourTotal() > 21 ? "BUST"
				: natural21 ? "★ BLACKJACK"
				: state.soft() ? "soft (" + (state.yourTotal() - 10) + "/" + state.yourTotal() + ")"
				: "hard";
		int yourTotalColor = natural21 ? 0xFFFFD700
				: state.yourTotal() > 21 ? LOSS
				: accent;
		total(context, font, state.yourTotal(), YOUR_CARDS_Y + 6, yourTotalColor, yourNote, natural21);

		odds(context, font, state);

		if (state.settled()) {
			banner(context, font, state, width, natural21);
		} else {
			controls(context, font, state, width, accent);
		}
		result(context, font, state, width);
	}

	/**
	 * Draws a seat's cards, each overlapping the last.
	 *
	 * <p>A newly dealt card glides in from the dealer shoe at the top-right with an ease-out
	 * curve and a slight tilt, snapping square into place. A hole card turning over flips in
	 * 3D with a specular shine glint.
	 */
	private void hand(GuiGraphicsExtractor context, Font font, int x, int y, Seat seat, int glowColor) {
		long now = Util.getMillis();
		for (int i = 0; i < seat.cards.size(); i++) {
			Card card = seat.cards.get(i);
			int home = x + i * CardPainter.STEP;
			long arrived = seat.arrivedAt(i);
			float t = arrived == 0L ? 1f
					: Math.clamp((now - arrived) / (float) DEAL_MS, 0f, 1f);
			if (t >= 1f) {
				CardPainter.draw(context, font, home, y, card, glowColor);
			} else if (seat.turnedOver(i)) {
				turnOver(context, font, home, y, card, t, glowColor);
			} else {
				int fromX = PANEL_W - PAD - CardPainter.CARD_W;
				int fromY = LAB_CARDS_Y - 12;
				float ease = 1f - (1f - t) * (1f - t) * (1f - t);
				int currX = Math.round(fromX + (home - fromX) * ease);
				int currY = Math.round(fromY + (y - fromY) * ease);

				context.pose().pushMatrix();
				context.pose().translate(currX + CardPainter.CARD_W / 2f, currY + CardPainter.CARD_H / 2f);
				context.pose().rotate((1f - ease) * 0.08f);
				context.pose().translate(-(currX + CardPainter.CARD_W / 2f), -(currY + CardPainter.CARD_H / 2f));
				CardPainter.draw(context, font, currX, currY, card, glowColor);
				context.pose().popMatrix();
			}
		}
	}

	private void turnOver(GuiGraphicsExtractor context, Font font, int x, int y, Card card,
			float t, int glowColor) {
		float squeeze = Math.max(0.02f, Math.abs(t * 2f - 1f));
		context.pose().pushMatrix();
		context.pose().translate(x + CardPainter.CARD_W / 2f, 0f);
		context.pose().scale(squeeze, 1f);
		context.pose().translate(-(x + CardPainter.CARD_W / 2f), 0f);
		CardPainter.draw(context, font, x, y, t >= 0.5f ? card : Card.HIDDEN, glowColor);
		if (t >= 0.40f && t <= 0.65f) {
			int shineAlpha = (int) (0x90 * (1f - Math.abs(t - 0.52f) / 0.13f));
			context.fill(x, y, x + CardPainter.CARD_W, y + CardPainter.CARD_H, (shineAlpha << 24) | 0x00FFFFFF);
		}
		context.pose().popMatrix();
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
	private void total(GuiGraphicsExtractor context, Font font, int value, int y, int color,
			String note, boolean gold) {
		if (value <= 0) {
			return;
		}
		String text = String.valueOf(value);
		int drawnW = font.width(text) * TOTAL_SCALE;
		int x = PANEL_W - PAD - drawnW;
		context.pose().pushMatrix();
		context.pose().translate(x, y);
		context.pose().scale(TOTAL_SCALE, TOTAL_SCALE);
		context.text(font, text, 0, 0, color, false);
		context.pose().popMatrix();
		if (note != null) {
			int noteColor = gold ? 0xFFFFD700 : note.startsWith("soft") ? 0xFF00E5FF : TEXT_FAINT;
			context.text(font, note, PANEL_W - PAD - font.width(note),
					y + font.lineHeight * TOTAL_SCALE, noteColor, false);
		}
	}

	/** The one computed figure on this board, and the dealer rule behind it. */
	private void odds(GuiGraphicsExtractor context, Font font, BjState state) {
		if (state.settled() || state.yourTotal() <= 0) {
			return;
		}
		if (state.phase() == BjState.Phase.LAB_TURN) {
			if (state.labTotal() > 21) {
				context.text(font, "LAB BUSTED (" + state.labTotal() + ")", PAD, ODDS_Y, WIN, false);
			} else if (state.labTotal() >= 17) {
				context.text(font, "LAB STANDS ON " + state.labTotal(), PAD, ODDS_Y, TEXT_DIM, false);
			} else {
				context.text(font, "LAB EXPERIMENTING...", PAD, ODDS_Y, accent(), false);
			}
			return;
		}
		if (state.yourTotal() > 21) {
			context.text(font, "bust", PAD, ODDS_Y, LOSS, false);
			return;
		}
		context.text(font, "draw busts", PAD, ODDS_Y, TEXT_DIM, false);
		int x = PAD + font.width("draw busts ");
		String chance = state.bustText();
		context.text(font, chance, x, ODDS_Y, state.soft() ? WIN : LOSS, false);
		String tail = " · lab draws to 17";
		context.text(font, tail, x + font.width(chance), ODDS_Y, TEXT_DIM, false);
	}

	private void controls(GuiGraphicsExtractor context, Font font, BjState state, int width,
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

	/**
	 * The server's own settled words, centred where the buttons were.
	 *
	 * <p>The 3:2 treatment needs the hand to have <em>won</em>, not merely to be a dealt
	 * 21. A dealt 21 against the lab's own dealt 21 is a push: the stake comes back and
	 * nothing is paid, and {@link BjState.Phase#PUSH} settles like any other end of hand.
	 * Keyed on the two cards alone, this band told you it had paid you half your stake
	 * again on a hand that paid you nothing.
	 */
	private void banner(GuiGraphicsExtractor context, Font font, BjState state, int width, boolean natural21) {
		boolean naturalWin = natural21 && state.phase() == BjState.Phase.WON;
		int bg = naturalWin ? 0x30E6A817 : PANEL;
		int color = naturalWin ? 0xFFFFD700 : switch (state.phase()) {
			case WON -> WIN;
			case LOST -> LOSS;
			default -> TEXT_DIM;
		};
		context.fill(PAD, BUTTONS_Y, PANEL_W - PAD, BUTTONS_Y + BUTTON_H, bg);
		if (naturalWin) {
			EditorPainter.outline(context, PAD, BUTTONS_Y, PANEL_W - PAD * 2, BUTTON_H, 0xFFE6A817);
		}
		String text = naturalWin ? "★ PERFECT REACTION ★ 3:2 WIN!"
				: font.plainSubstrByWidth(state.banner(), width - 6);
		context.text(font, text, PAD + (width - font.width(text)) / 2,
				BUTTONS_Y + (BUTTON_H - font.lineHeight) / 2 + 1, color, false);
	}

	/**
	 * What the hand paid. The band names a figure only on a win, so this comes from chat,
	 * which states all three outcomes.
	 */
	private void result(GuiGraphicsExtractor context, Font font, BjState state, int width) {
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
