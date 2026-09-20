package dev.jade.labsaddons.blackjack;

import dev.jade.labsaddons.casino.Glyphs;
import dev.jade.labsaddons.hud.HudObject;
import dev.jade.labsaddons.hud.editor.EditorPainter;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Draws playing cards.
 *
 * <p>The server keeps a hand as lore on a brewing stand, so there is no texture to reuse
 * and the mod owns every card face: a pale rounded face, the rank in the corner, and the
 * suit as a bitmap pip from {@link Glyphs} rather than a character the font may not have.
 */
public final class CardPainter {
	public static final int CARD_W = 22;
	public static final int CARD_H = 30;
	/** How far each card sits from the last: cards in a hand overlap, as they would. */
	public static final int STEP = 15;

	private static final int SHADOW = 0x44000000;
	private static final int FACE = 0xFFF7F4EC;
	private static final int FACE_EDGE = 0xFFC2BCAE;
	private static final int FACE_SHINE = 0x30FFFFFF;
	private static final int BACK = 0xFF182232;
	private static final int BACK_EDGE = 0xFF2F425E;
	private static final int BACK_PATTERN = 0xFF233247;
	private static final int RED = 0xFFD32F2F;
	private static final int BLACK = 0xFF14181E;
	private static final int PIP_PIXEL = 2;

	private CardPainter() {
	}

	public static void draw(DrawContext context, TextRenderer font, int x, int y, Card card) {
		draw(context, font, x, y, card, 0);
	}

	public static void draw(DrawContext context, TextRenderer font, int x, int y, Card card, int glowColor) {
		// Ambient drop shadow beneath card
		context.fill(x + 1, y + 1, x + CARD_W + 1, y + CARD_H + 1, SHADOW);

		if (card == null || card.isHidden()) {
			back(context, x, y, glowColor);
			return;
		}

		int ink = card.suit().isRed() ? RED : BLACK;
		HudObject.drawRoundedRect(context, x, y, CARD_W, CARD_H, FACE);

		// Top-edge subtle specular highlight
		context.fill(x + 2, y + 1, x + CARD_W - 2, y + 2, FACE_SHINE);

		// Border outline or glow
		int border = glowColor != 0 ? glowColor : FACE_EDGE;
		EditorPainter.outline(context, x, y, CARD_W, CARD_H, border);
		if (glowColor != 0) {
			EditorPainter.outline(context, x - 1, y - 1, CARD_W + 2, CARD_H + 2, (glowColor & 0x00FFFFFF) | 0x60000000);
		}

		// The rank sits in the corner where a real card carries it
		String rank = card.rankText();
		context.drawText(font, rank, x + 2, y + 2, ink, false);

		// Single clean suit pip centred in the lower area of the card
		Glyphs.centred(context, card.suit().pip(), x, y + 11, CARD_W, CARD_H - 13,
				PIP_PIXEL, ink);
	}

	/** A face-down card: the hole card the server states only as "??". */
	private static void back(DrawContext context, int x, int y, int glowColor) {
		HudObject.drawRoundedRect(context, x, y, CARD_W, CARD_H, BACK);
		int border = glowColor != 0 ? glowColor : BACK_EDGE;
		EditorPainter.outline(context, x, y, CARD_W, CARD_H, border);
		EditorPainter.outline(context, x + 2, y + 2, CARD_W - 4, CARD_H - 4, BACK_PATTERN);

		// Ornate diamond/lattice pattern on card back
		for (int row = y + 5; row < y + CARD_H - 5; row += 4) {
			for (int column = x + 5; column < x + CARD_W - 5; column += 4) {
				int offset = ((row - y) / 4) % 2 == 0 ? 0 : 2;
				if (column + offset + 2 <= x + CARD_W - 5) {
					context.fill(column + offset, row, column + offset + 2, row + 2, BACK_PATTERN);
				}
			}
		}
	}
}
