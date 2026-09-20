package dev.jade.labsaddons.casino;

import net.minecraft.client.gui.DrawContext;

/**
 * Small pictograms drawn as bitmaps rather than text.
 *
 * <p>The obvious way to put a star or a card suit on a board is to draw the character —
 * but which of those Minecraft's font actually has a glyph for is not something this mod
 * can promise, and a missing one renders as a box in the middle of a card. Seven-by-seven
 * bitmaps are a dozen bytes each and always look the same.
 */
public final class Glyphs {
	private static final int SIZE = 7;

	/**
	 * A five-pointed star: a revealed safe tile. The feet are two pixels wide — a single
	 * one read as a dot floating below the leg rather than the end of it.
	 */
	public static final int[] STAR = {0x08, 0x1C, 0x7F, 0x3E, 0x1C, 0x36, 0x63};
	/** A bomb with a fuse: a revealed mine. */
	public static final int[] MINE = {0x0A, 0x1C, 0x3E, 0x7F, 0x7F, 0x3E, 0x1C};
	public static final int[] DIAMOND = {0x08, 0x1C, 0x3E, 0x7F, 0x3E, 0x1C, 0x08};
	public static final int[] HEART = {0x36, 0x7F, 0x7F, 0x7F, 0x3E, 0x1C, 0x08};
	public static final int[] SPADE = {0x08, 0x1C, 0x3E, 0x7F, 0x7F, 0x08, 0x1C};
	/**
	 * Three lobes and a stem. Seven pixels will not hold three separated circles, and
	 * punching holes between them reads as eyes at this size — so the lobes live in the
	 * silhouette instead: a round crown where the spade has a point, and a notch under it
	 * where the spade is flat.
	 */
	public static final int[] CLUB = {0x1C, 0x3E, 0x7F, 0x7F, 0x36, 0x08, 0x1C};

	private Glyphs() {
	}

	/** The drawn size of a glyph at {@code pixel} scale, for centring it. */
	public static int size(int pixel) {
		return SIZE * pixel;
	}

	/**
	 * Draws {@code glyph} with its top-left corner at {@code x, y}, each bit becoming a
	 * {@code pixel}-square block. Runs of set bits are emitted as one fill.
	 */
	public static void draw(DrawContext context, int[] glyph, int x, int y, int pixel,
			int color) {
		for (int row = 0; row < SIZE; row++) {
			int bits = glyph[row];
			int column = 0;
			while (column < SIZE) {
				if ((bits & (1 << (SIZE - 1 - column))) == 0) {
					column++;
					continue;
				}
				int run = 0;
				while (column + run < SIZE
						&& (bits & (1 << (SIZE - 1 - column - run))) != 0) {
					run++;
				}
				int left = x + column * pixel;
				int top = y + row * pixel;
				context.fill(left, top, left + run * pixel, top + pixel, color);
				column += run;
			}
		}
	}

	/** Draws {@code glyph} centred in the given rectangle. */
	public static void centred(DrawContext context, int[] glyph, int x, int y, int w, int h,
			int pixel, int color) {
		int drawn = size(pixel);
		draw(context, glyph, x + (w - drawn) / 2, y + (h - drawn) / 2, pixel, color);
	}
}
