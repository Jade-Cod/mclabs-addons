package dev.jade.labsaddons.coinflip;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.entity.player.SkinTextures;

/**
 * Draws a struck coin with a player's face on it.
 *
 * <p>The server's own flip is a player head swapping names in one slot. This is the same
 * information given a body: a gold disc with the player's real skin face struck into it and
 * the side it stands for written underneath, squeezed vertically as it turns so it passes
 * edge-on the way a coin does.
 *
 * <p>Only the squeeze is a matrix trick — the same one the Mines tiles turn on. Everything
 * else is fills, so the coin costs no texture and no shader.
 */
public final class CoinPainter {
	private static final int RIM = 0xFFA87826;
	private static final int BODY = 0xFFE8B04B;
	private static final int SHEEN = 0xFFF7D98A;
	private static final int LEGEND = 0xFF4A320F;
	private static final int BLANK_FACE = 0xFFC89A48;
	private static final int SHADOW = 0x59000000;

	/** Below this the coin reads as edge-on: no face, no legend, just the rim. */
	private static final float FACE_VISIBLE = 0.30f;
	private static final float LEGEND_VISIBLE = 0.62f;
	/** A coin exactly edge-on would vanish, and a vanished coin reads as a bug. */
	private static final float MIN_SQUEEZE = 0.05f;
	/** Face and legend each sit this fraction of the diameter off centre. */
	private static final int LEGEND_INSET = 7;

	private CoinPainter() {
	}

	/**
	 * @param squeeze 1 face-on, 0 edge-on — {@code |cos(pi * halfTurns)|}
	 * @param skin    the face to strike into it, or null while a name is still unknown
	 * @param legend  "HEADS" or "TAILS", drawn only while the coin is flat enough to read
	 */
	public static void draw(DrawContext context, TextRenderer font, int cx, int cy, int d,
			float squeeze, SkinTextures skin, String legend) {
		float flat = Math.clamp(squeeze, MIN_SQUEEZE, 1f);
		int radius = d / 2;

		context.getMatrices().pushMatrix();
		context.getMatrices().translate(0f, cy);
		context.getMatrices().scale(1f, flat);
		context.getMatrices().translate(0f, -cy);

		disc(context, cx, cy, radius, RIM);
		disc(context, cx, cy, radius - Math.max(2, d / 18), BODY);
		// A highlight up and left, so the disc reads as struck metal rather than a circle.
		disc(context, cx - radius / 4, cy - radius / 4, radius / 3, SHEEN);

		if (flat >= FACE_VISIBLE) {
			int size = Math.max(8, d / 2);
			int faceX = cx - size / 2;
			// Lifted by the legend's own height when there is one, so both stay inside the
			// disc: a word centred any lower runs off the bottom of the circle.
			int faceY = cy - size / 2
					- (legend == null || legend.isEmpty() ? 0 : d / LEGEND_INSET);
			if (skin != null) {
				PlayerSkinDrawer.draw(context, skin, faceX, faceY, size);
			} else {
				// A name we have not seen yet. A blank strike is honest; a stranger's face
				// would not be.
				context.fill(faceX, faceY, faceX + size, faceY + size, BLANK_FACE);
			}
		}
		if (flat >= LEGEND_VISIBLE && legend != null && !legend.isEmpty()) {
			int width = font.getWidth(legend);
			context.drawText(font, legend, cx - width / 2, cy + d / LEGEND_INSET, LEGEND,
					false);
		}

		context.getMatrices().popMatrix();
	}

	/**
	 * The coin's shadow on the table, flattened and faded by how high it is.
	 *
	 * @param lift 0 on the table, 1 at the top of the toss
	 */
	public static void shadow(DrawContext context, int cx, int y, int d, float lift) {
		float spread = 1f - 0.35f * Math.clamp(lift, 0f, 1f);
		int radius = Math.max(2, Math.round(d * spread / 2f));
		context.getMatrices().pushMatrix();
		context.getMatrices().translate(0f, y);
		context.getMatrices().scale(1f, 0.22f);
		context.getMatrices().translate(0f, -y);
		disc(context, cx, y, radius, SHADOW);
		context.getMatrices().popMatrix();
	}

	/** An expanding ring, for the moment the coin settles. */
	public static void ring(DrawContext context, int cx, int cy, int radius, int color) {
		for (int dy = -radius; dy <= radius; dy++) {
			int dx = (int) Math.round(Math.sqrt((double) radius * radius - (double) dy * dy));
			context.fill(cx - dx, cy + dy, cx - dx + 1, cy + dy + 1, color);
			context.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
		}
	}

	private static void disc(DrawContext context, int cx, int cy, int radius, int color) {
		if (radius <= 0) {
			return;
		}
		for (int dy = -radius; dy <= radius; dy++) {
			int dx = (int) Math.round(Math.sqrt((double) radius * radius - (double) dy * dy));
			context.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
		}
	}
}
