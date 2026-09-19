package dev.jade.labsaddons.double2;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Paints the 25-segment ring, pointer at the top.
 *
 * <p>Two things keep it smooth. First it is rasterised at <b>device</b> resolution
 * rather than GUI units: the matrix is counter-scaled and every radius multiplied up,
 * so one fill is one screen pixel instead of a GUI-scale-sized block. Drawing at GUI
 * scale and letting Minecraft magnify it was what made the edges look doubled.
 *
 * <p>Second, edges carry coverage. A pixel near the rim fades by how much of it the
 * ring covers, and a pixel near a segment boundary is mixed between the two segments,
 * so the spokes and the rim antialias themselves. That needs per-pixel work, but runs
 * of identical colour are emitted as a single fill, so the interior of a segment costs
 * one rectangle per scanline rather than one per pixel.
 */
final class D2Wheel {
	private static final double SEG_RAD = Math.PI * 2 / D2Ring.SIZE;
	private static final int HUB_BG = 0xFF0A0C10;
	/** A segment of the wheel that has not come past the strip yet. */
	private static final int UNSEEN = 0xFF2A2F3A;
	/** Below this ring thickness the glyphs are wider than the band, so they are dropped. */
	private static final int GLYPH_MIN_THICKNESS = 14;

	private D2Wheel() {
	}

	/**
	 * @param segments      the wheel as far as it is known; a null has not been seen yet
	 * @param offset        the tweened ring position of the window's first segment
	 * @param unitsToPixels screen pixels per GUI unit — the GUI scale times the panel's
	 *                      own scale, which is the resolution actually available
	 * @param hubText       lines for the middle, any of which may be null
	 */
	static void draw(DrawContext context, TextRenderer font, int cx, int cy,
			int outerR, int innerR, Lab[] segments, float offset, int accent,
			String[] hubText, float unitsToPixels) {
		float scale = Math.max(1f, unitsToPixels);
		int outerPx = Math.round(outerR * scale);
		int innerPx = Math.round(innerR * scale);
		int rimPx = Math.max(1, Math.round(scale));

		context.getMatrices().pushMatrix();
		context.getMatrices().translate(cx, cy);
		context.getMatrices().scale(1f / scale, 1f / scale);
		ring(context, segments, outerPx, innerPx, offset);
		disc(context, innerPx, accent);
		disc(context, innerPx - rimPx, HUB_BG);
		context.getMatrices().popMatrix();

		if (outerR - innerR >= GLYPH_MIN_THICKNESS) {
			glyphs(context, font, segments, cx, cy, (innerR + outerR) / 2, offset);
		}
		hub(context, font, cx, cy, innerR, hubText);
		pointer(context, cx, cy, outerR, accent);
	}

	/** The ring itself, one scanline at a time, merging equal-coloured runs. */
	private static void ring(DrawContext context, Lab[] segments,
			int outerR, int innerR, float offset) {
		double pointerIndex = offset + D2Ring.POINTER;
		for (int py = -outerR; py < outerR; py++) {
			double y = py + 0.5;
			if (Math.abs(y) >= outerR) {
				continue;
			}
			int xo = (int) Math.ceil(Math.sqrt((double) outerR * outerR - y * y));
			// Skip the hollow middle rather than testing every pixel of it.
			int xi = Math.abs(y) < innerR
					? (int) Math.floor(Math.sqrt((double) innerR * innerR - y * y))
					: 0;
			if (xi > 0) {
				scan(context, segments, py, -xo, -xi, y, outerR, innerR, pointerIndex);
				scan(context, segments, py, xi, xo, y, outerR, innerR, pointerIndex);
			} else {
				scan(context, segments, py, -xo, xo, y, outerR, innerR, pointerIndex);
			}
		}
	}

	private static void scan(DrawContext context, Lab[] segments, int py, int from, int to,
			double y, int outerR, int innerR, double pointerIndex) {
		int runColor = 0;
		int runStart = from;
		for (int px = from; px <= to; px++) {
			int color = pixel(segments, px + 0.5, y, outerR, innerR, pointerIndex);
			if (color != runColor) {
				if ((runColor >>> 24) != 0) {
					context.fill(runStart, py, px, py + 1, runColor);
				}
				runColor = color;
				runStart = px;
			}
		}
		if ((runColor >>> 24) != 0) {
			context.fill(runStart, py, to + 1, py + 1, runColor);
		}
	}

	/** One pixel of the ring: its segment's colour, faded at the rim, mixed at a spoke. */
	private static int pixel(Lab[] segments, double x, double y,
			int outerR, int innerR, double pointerIndex) {
		double r = Math.sqrt(x * x + y * y);
		double coverage = Math.min(outerR - r, r - innerR) + 0.5;
		if (coverage <= 0.0) {
			return 0;
		}
		coverage = Math.min(1.0, coverage);

		// Segment 0 sits at twelve o'clock when the offset is zero.
		double segment = (Math.atan2(y, x) + Math.PI / 2) / SEG_RAD + pointerIndex;
		int index = (int) Math.floor(segment + 0.5);
		double within = segment + 0.5 - Math.floor(segment + 0.5);
		int color = colorOf(segments, index);

		// Within half a pixel of a boundary, mix with the segment on the other side.
		double edgePx = Math.min(within, 1.0 - within) * SEG_RAD * r;
		if (edgePx < 0.5) {
			int other = colorOf(segments, within < 0.5 ? index - 1 : index + 1);
			color = mix(color, other, (float) (0.5 - edgePx));
		}
		return (((int) Math.round(coverage * 255) & 0xFF) << 24) | (color & 0x00FFFFFF);
	}

	/** A segment's colour, or the unseen grey where the wheel is not known yet. */
	private static int colorOf(Lab[] segments, int index) {
		Lab lab = segments[Math.floorMod(index, D2Ring.SIZE)];
		return lab == null ? UNSEEN : lab.color();
	}

	/** A filled circle with a soft edge, drawn in the same device-pixel space. */
	private static void disc(DrawContext context, int r, int color) {
		if (r <= 0) {
			return;
		}
		for (int py = -r; py < r; py++) {
			double y = py + 0.5;
			double span = Math.sqrt(Math.max(0.0, (double) r * r - y * y));
			int solid = (int) Math.floor(span);
			if (solid > 0) {
				context.fill(-solid, py, solid, py + 1, color);
			}
			// The pixel each edge lands inside takes the fraction of it that is covered.
			int alpha = (int) Math.round((span - solid) * 255);
			if (alpha > 0) {
				int faded = (alpha << 24) | (color & 0x00FFFFFF);
				context.fill(solid, py, solid + 1, py + 1, faded);
				context.fill(-solid - 1, py, -solid, py + 1, faded);
			}
		}
	}

	/** Lab glyphs, upright, in GUI units — the font is already sharp at any scale. */
	private static void glyphs(DrawContext context, TextRenderer font, Lab[] segments,
			int cx, int cy, int radius, float offset) {
		double pointerIndex = offset + D2Ring.POINTER;
		for (int i = 0; i < D2Ring.SIZE; i++) {
			double angle = (i - pointerIndex) * SEG_RAD - Math.PI / 2;
			Lab lab = segments[i];
			if (lab == null) {
				continue;
			}
			int gx = cx + (int) Math.round(Math.cos(angle) * radius);
			int gy = cy + (int) Math.round(Math.sin(angle) * radius);
			context.drawText(font, lab.glyph(), gx - font.getWidth(lab.glyph()) / 2,
					gy - font.fontHeight / 2, lab.lift(), false);
		}
	}

	private static void hub(DrawContext context, TextRenderer font,
			int cx, int cy, int innerR, String[] lines) {
		if (lines == null) {
			return;
		}
		int drawn = 0;
		for (String line : lines) {
			if (line != null) {
				drawn++;
			}
		}
		if (drawn == 0 || innerR < font.fontHeight) {
			return;
		}
		int lineH = font.fontHeight + 2;
		int y = cy - drawn * lineH / 2;
		for (int i = 0; i < lines.length; i++) {
			if (lines[i] == null) {
				continue;
			}
			int color = i == 0 ? 0xFFFFFFFF : i == 1 ? 0xFFEAEEF3 : 0xFF9AA3AD;
			context.drawText(font, lines[i], cx - font.getWidth(lines[i]) / 2, y, color, false);
			y += lineH;
		}
	}

	/** The marker at twelve o'clock, narrowing into the ring. */
	private static void pointer(DrawContext context, int cx, int cy, int outerR, int accent) {
		int top = cy - outerR - 7;
		for (int row = 0; row < 7; row++) {
			int half = 5 - (row * 5 / 7);
			if (half > 0) {
				context.fill(cx - half, top + row, cx + half, top + row + 1, accent);
			}
		}
	}

	/** {@code amount} of {@code b} blended into {@code a}; both opaque. */
	private static int mix(int a, int b, float amount) {
		int r = Math.round(((a >> 16) & 0xFF) * (1 - amount) + ((b >> 16) & 0xFF) * amount);
		int g = Math.round(((a >> 8) & 0xFF) * (1 - amount) + ((b >> 8) & 0xFF) * amount);
		int bl = Math.round((a & 0xFF) * (1 - amount) + (b & 0xFF) * amount);
		return 0xFF000000 | (r << 16) | (g << 8) | bl;
	}
}
