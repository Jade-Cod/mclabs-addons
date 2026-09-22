package dev.jade.labsaddons.coinflip;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * Draws a struck coin with a player's face on it.
 *
 * <p>A flat square of skin on a flat disc read as a sticker rather than a coin, so this
 * gives it the things that make metal look like metal: a rim two shades darker than the
 * body, a dark bezel around the portrait so the square edge is deliberate, a highlight up
 * and to the left, and — the one that does most of the work — a visible <b>thickness</b>
 * on the rim as it turns edge-on, lit along the top and shadowed underneath.
 *
 * <p>Only the turn is a matrix trick, the same one the Mines tiles use. Everything else is
 * fills, so the coin costs no texture and no shader.
 */
public final class CoinPainter {
	private static final int RIM_DARK = 0xFF6E4A12;
	private static final int RIM = 0xFFA87826;
	private static final int BODY_EDGE = 0xFFC9913A;
	private static final int BODY = 0xFFE8B04B;
	private static final int SHEEN = 0xFFF7D98A;
	private static final int BEZEL = 0xFF5A3C0E;
	private static final int BLANK_FACE = 0xFFC89A48;
	private static final int SHADOW = 0x59000000;

	/** Below this the portrait is too distorted to be worth drawing. */
	private static final float FACE_VISIBLE = 0.34f;
	/** Past this the coin is edge-on enough to be worth lighting its rim. */
	private static final float EDGE_LIT = 0.55f;
	/** The coin is never thinner than this many pixels: a vanished coin reads as a bug. */
	private static final int MIN_THICKNESS = 2;

	/**
	 * How much of the coin the portrait takes.
	 *
	 * <p>The face used to share the disc with "HEADS"/"TAILS" struck under it, and had to be
	 * small enough to leave room. The word said nothing the seats below the coin do not
	 * already say, so it is gone and the face has the disc to itself. Its corners reach a
	 * little into the rim band, which is what a real coin's portrait does.
	 */
	private static final float FACE_SIZE = 0.62f;

	private CoinPainter() {
	}

	/**
	 * @param squeeze 1 face-on, 0 edge-on, and above 1 for the squash as it lands
	 * @param skin    the face to strike into it, or null while a name is still unknown
	 * @param flash   0 to 1, a white bloom over the whole coin at the moment it settles
	 */
	public static void draw(GuiGraphicsExtractor context, int cx, int cy, int d,
			float squeeze, PlayerSkin skin, float flash) {
		int radius = d / 2;
		float flat = Math.clamp(squeeze, MIN_THICKNESS / (float) radius, 1.45f);
		int rim = Math.max(3, d / 12);

		context.pose().pushMatrix();
		context.pose().translate(0f, cy);
		context.pose().scale(1f, flat);
		context.pose().translate(0f, -cy);

		disc(context, cx, cy, radius, RIM_DARK);
		disc(context, cx, cy, radius - 1, RIM);
		disc(context, cx, cy, radius - rim, BODY_EDGE);
		disc(context, cx, cy, radius - rim - 2, BODY);
		// A small highlight, not a wash: a big pale blob over half the coin read as a
		// gradient mistake rather than light on metal.
		disc(context, cx - radius / 3, cy - radius / 3, Math.max(2, radius / 5), SHEEN);

		if (flat >= FACE_VISIBLE) {
			portrait(context, cx, cy, d, skin);
		}
		thickness(context, cx, cy, radius, flat);
		if (flash > 0f) {
			disc(context, cx, cy, radius, alpha(0xFFFFFF, Math.round(200 * flash)));
		}
		context.pose().popMatrix();
	}

	/**
	 * The rim seen nearly edge-on: lit along the top, shadowed underneath, fading in as the
	 * coin turns away.
	 *
	 * <p>Drawn row by row inside the squeeze rather than as two straight lines across the
	 * top and bottom. An ellipse is barely any width at its extremes, so a full-width line
	 * there hangs off both sides and reads as a stray highlight rather than a rim.
	 */
	private static void thickness(GuiGraphicsExtractor context, int cx, int cy, int radius,
			float flat) {
		int strength = Math.round(220 * (1f - flat / EDGE_LIT));
		if (strength <= 0) {
			return;
		}
		int band = Math.max(1, radius / 3);
		for (int dy = -radius; dy <= radius; dy++) {
			if (dy >= -band && dy <= band) {
				continue;
			}
			int dx = (int) Math.round(Math.sqrt((double) radius * radius - (double) dy * dy));
			context.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1,
					alpha(dy < 0 ? SHEEN : RIM_DARK, strength));
		}
	}

	/** The player's face, bezelled so its square edge reads as a strike and not a sticker. */
	private static void portrait(GuiGraphicsExtractor context, int cx, int cy, int d,
			PlayerSkin skin) {
		int size = Math.max(8, Math.round(d * FACE_SIZE));
		int x = cx - size / 2;
		int y = cy - size / 2;
		context.fill(x - 1, y - 1, x + size + 1, y + size + 1, BEZEL);
		if (skin != null) {
			PlayerFaceExtractor.extractRenderState(context, skin, x, y, size);
		} else {
			// A name we have not seen yet. A blank strike is honest; a stranger's face
			// would not be.
			context.fill(x, y, x + size, y + size, BLANK_FACE);
		}
	}

	/** A faint copy of the coin a fraction of a turn behind, so a fast spin smears. */
	public static void ghost(GuiGraphicsExtractor context, int cx, int cy, int d, float squeeze,
			int opacity) {
		int radius = d / 2;
		float flat = Math.clamp(squeeze, MIN_THICKNESS / (float) radius, 1f);
		context.pose().pushMatrix();
		context.pose().translate(0f, cy);
		context.pose().scale(1f, flat);
		context.pose().translate(0f, -cy);
		disc(context, cx, cy, radius, alpha(RIM & 0xFFFFFF, opacity));
		disc(context, cx, cy, radius - Math.max(2, d / 16), alpha(BODY & 0xFFFFFF, opacity));
		context.pose().popMatrix();
	}

	/**
	 * The coin's shadow on the table, flattened and faded by how high it is.
	 *
	 * @param lift 0 on the table, 1 at the top of the toss
	 */
	public static void shadow(GuiGraphicsExtractor context, int cx, int y, int d, float lift) {
		float high = Math.clamp(lift, 0f, 1f);
		int radius = Math.max(2, Math.round(d * (1f - 0.45f * high) / 2f));
		int fade = Math.round(((SHADOW >>> 24) & 0xFF) * (1f - 0.55f * high));
		context.pose().pushMatrix();
		context.pose().translate(0f, y);
		context.pose().scale(1f, 0.20f);
		context.pose().translate(0f, -y);
		disc(context, cx, y, radius, alpha(SHADOW & 0xFFFFFF, fade));
		context.pose().popMatrix();
	}

	/** An expanding ring, for the moment the coin settles. */
	public static void ring(GuiGraphicsExtractor context, int cx, int cy, int radius, int color) {
		if (radius <= 0) {
			return;
		}
		for (int dy = -radius; dy <= radius; dy++) {
			int dx = (int) Math.round(Math.sqrt((double) radius * radius - (double) dy * dy));
			context.fill(cx - dx, cy + dy, cx - dx + 1, cy + dy + 1, color);
			context.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
		}
	}

	private static int alpha(int rgb, int opacity) {
		return (Math.clamp(opacity, 0, 255) << 24) | (rgb & 0xFFFFFF);
	}

	private static void disc(GuiGraphicsExtractor context, int cx, int cy, int radius, int color) {
		if (radius <= 0) {
			return;
		}
		for (int dy = -radius; dy <= radius; dy++) {
			int dx = (int) Math.round(Math.sqrt((double) radius * radius - (double) dy * dy));
			context.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
		}
	}
}
