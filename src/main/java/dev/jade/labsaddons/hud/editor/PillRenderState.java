package dev.jade.labsaddons.hud.editor;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

/**
 * One pill (a bar with round ends), as one GUI element.
 *
 * <p>The ends used to be a {@code fill()} per pixel with the edge supersampled on the CPU,
 * the same shape of cost the cooldown ring had: every pixel its own render-state object, each
 * checked against those already queued. A widget with ten bars spent over 100 us a frame and
 * 70 KB of garbage on them. Here the body is one quad and each end a fan of slices with a
 * half-pixel feather in the vertex alpha, which the GPU interpolates, as {@code RingRenderState}
 * does.
 */
final class PillRenderState implements SimpleGuiElementRenderState {
	/** Slices per round end. A pill's radius is a few pixels, so this is well under a pixel each. */
	private static final int SLICES = 12;
	/** Softening either side of the curved edge, in pixels. */
	private static final float FEATHER = 0.5f;

	private final Matrix3x2fc pose;
	private final float left;
	private final float right;
	private final float top;
	private final float bottom;
	private final float r;
	private final int colour;
	private final ScreenRect bounds;

	PillRenderState(Matrix3x2fc pose, int x, int y, int w, int h, int colour) {
		this.pose = new Matrix3x2f(pose);
		this.r = h / 2f;
		this.left = x + r;
		this.right = Math.max(left, x + w - r);
		this.top = y;
		this.bottom = y + h;
		this.colour = colour;
		this.bounds = new ScreenRect(x - 1, y - 1, w + 2, h + 2).transformEachVertex(this.pose);
	}

	@Override
	public void setupVertices(VertexConsumer consumer) {
		if (right > left) {
			quad(consumer, left, top, left, bottom, right, bottom, right, top, colour, colour);
		}
		float cy = (top + bottom) / 2f;
		end(consumer, left, cy, -1f);
		end(consumer, right, cy, 1f);
	}

	/** A half disc bulging toward {@code side} (-1 left, 1 right), top to bottom. */
	private void end(VertexConsumer consumer, float cx, float cy, float side) {
		int clear = colour & 0x00FFFFFF;
		float solidR = Math.max(0f, r - FEATHER);
		float outerR = r + FEATHER;
		for (int i = 0; i < SLICES; i++) {
			double a0 = Math.PI * i / SLICES;
			double a1 = Math.PI * (i + 1) / SLICES;
			float dx0 = side * (float) Math.sin(a0);
			float dy0 = (float) -Math.cos(a0);
			float dx1 = side * (float) Math.sin(a1);
			float dy1 = (float) -Math.cos(a1);
			// Solid wedge from the centre, then the feather band out to the edge.
			quad(consumer, cx, cy, cx, cy, cx + dx1 * solidR, cy + dy1 * solidR, cx + dx0 * solidR, cy + dy0 * solidR,
					colour, colour);
			band(consumer, cx, cy, solidR, outerR, dx0, dy0, dx1, dy1, clear);
		}
	}

	private void band(VertexConsumer consumer, float cx, float cy, float inner, float outer,
			float dx0, float dy0, float dx1, float dy1, int clear) {
		consumer.vertex(pose, cx + dx0 * outer, cy + dy0 * outer).color(clear);
		consumer.vertex(pose, cx + dx0 * inner, cy + dy0 * inner).color(colour);
		consumer.vertex(pose, cx + dx1 * inner, cy + dy1 * inner).color(colour);
		consumer.vertex(pose, cx + dx1 * outer, cy + dy1 * outer).color(clear);
	}

	private void quad(VertexConsumer consumer, float x0, float y0, float x1, float y1, float x2, float y2,
			float x3, float y3, int c0, int c1) {
		consumer.vertex(pose, x0, y0).color(c0);
		consumer.vertex(pose, x1, y1).color(c0);
		consumer.vertex(pose, x2, y2).color(c1);
		consumer.vertex(pose, x3, y3).color(c1);
	}

	@Override
	public RenderPipeline pipeline() {
		return RenderPipelines.GUI;
	}

	@Override
	public TextureSetup textureSetup() {
		return TextureSetup.empty();
	}

	@Override
	public ScreenRect scissorArea() {
		return null;
	}

	@Override
	public ScreenRect bounds() {
		return bounds;
	}
}
