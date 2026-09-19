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

	private static final int FACE = 0xFFF5F2EA;
	private static final int FACE_EDGE = 0xFFC9C3B4;
	private static final int BACK = 0xFF232B36;
	private static final int BACK_EDGE = 0xFF3E4B5C;
	private static final int BACK_PATTERN = 0xFF2F3A48;
	private static final int RED = 0xFFC1362F;
	private static final int BLACK = 0xFF1B1E24;
	private static final int PIP_PIXEL = 2;

	private CardPainter() {
	}

	public static void draw(DrawContext context, TextRenderer font, int x, int y, Card card) {
		if (card == null || card.isHidden()) {
			back(context, x, y);
			return;
		}
		int ink = card.suit().isRed() ? RED : BLACK;
		HudObject.drawRoundedRect(context, x, y, CARD_W, CARD_H, FACE);
		EditorPainter.outline(context, x, y, CARD_W, CARD_H, FACE_EDGE);

		// The rank sits in the corner where a real card carries it, so a fanned hand still
		// reads: the overlap hides the pip of every card but the last.
		String rank = card.rankText();
		context.drawText(font, rank, x + 2, y + 2, ink, false);
		Glyphs.centred(context, card.suit().pip(), x, y + 11, CARD_W, CARD_H - 13,
				PIP_PIXEL, ink);
	}

	/** A face-down card: the hole card the server states only as "??". */
	private static void back(DrawContext context, int x, int y) {
		HudObject.drawRoundedRect(context, x, y, CARD_W, CARD_H, BACK);
		EditorPainter.outline(context, x, y, CARD_W, CARD_H, BACK_EDGE);
		// A lattice rather than a glyph: it reads as a card back at any size, and cannot
		// be mistaken for a rank.
		for (int row = y + 4; row < y + CARD_H - 4; row += 4) {
			for (int column = x + 4; column < x + CARD_W - 4; column += 4) {
				int offset = ((row - y) / 4) % 2 == 0 ? 0 : 2;
				if (column + offset + 2 <= x + CARD_W - 4) {
					context.fill(column + offset, row, column + offset + 2, row + 2,
							BACK_PATTERN);
				}
			}
		}
	}
}
