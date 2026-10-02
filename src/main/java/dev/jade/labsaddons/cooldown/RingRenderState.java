package dev.jade.labsaddons.cooldown;

import net.minecraft.client.render.VertexConsumer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

/**
 * One cooldown ring, as one GUI element.
 *
 * <p>It used to be a {@code fill()} per pixel with the edge coverage supersampled on the CPU:
 * about 350 fills a ring, each its own render-state object, and the GUI checks every new one
 * against those already queued. Two rings cost the HUD a millisecond a frame and 128 KB of
 * garbage. Here the ring is an annulus of quads with the antialiasing in the vertex alpha: a
 * half-pixel feather either side of each radius, which is the band the old 4x4 sampling
 * blurred over, and the GPU interpolates across it.
 *
 * <p>The ring is two arcs clockwise from 12 o'clock: {@code before} up to {@code splitDeg},
 * {@code after} from there round. The split lands on a slice edge, so the boundary between
 * the recharged green and the orange still to go stays as sharp as it was.
 */
final class RingRenderState implements SimpleGuiElementRenderState {
	/** Wedges in a full turn. At an 18px radius that's under 2px of arc between vertices. */
	private static final int SLICES = 64;
	/** Softening either side of each radius, in pixels. */
	private static final float FEATHER = 0.5f;

	private final Matrix3x2fc pose;
	private final float cx;
	private final float cy;
	private final float innerR;
	private final float outerR;
	private final float splitDeg;
	private final int before;
	private final int after;
	private final ScreenRect bounds;

	RingRenderState(Matrix3x2fc pose, float cx, float cy, float innerR, float outerR,
			float splitDeg, int before, int after) {
		this.pose = new Matrix3x2f(pose);
		this.cx = cx;
		this.cy = cy;
		this.innerR = innerR;
		this.outerR = outerR;
		this.splitDeg = Math.clamp(splitDeg, 0f, 360f);
		this.before = before;
		this.after = after;

		int min = (int) Math.floor(-outerR - FEATHER) - 1;
		int size = (int) Math.ceil((outerR + FEATHER) * 2) + 3;
		this.bounds = new ScreenRect((int) Math.floor(cx) + min, (int) Math.floor(cy) + min, size, size)
				.transformEachVertex(this.pose);
	}

	/** A ring all one colour. */
	static RingRenderState solid(Matrix3x2fc pose, float cx, float cy, float innerR, float outerR, int colour) {
		return new RingRenderState(pose, cx, cy, innerR, outerR, 360f, colour, colour);
	}

	@Override
	public void setupVertices(VertexConsumer consumer) {
		arc(consumer, 0f, splitDeg, before);
		arc(consumer, splitDeg, 360f, after);
	}

	private void arc(VertexConsumer consumer, float fromDeg, float toDeg, int colour) {
		float span = toDeg - fromDeg;
		if (span <= 0f) {
			return;
		}
		int slices = Math.max(1, (int) Math.ceil(SLICES * span / 360f));
		int solid = colour;
		int clear = colour & 0x00FFFFFF;
		for (int i = 0; i < slices; i++) {
			double a0 = Math.toRadians(fromDeg + span * i / slices);
			double a1 = Math.toRadians(fromDeg + span * (i + 1) / slices);
			// Clockwise from the top: x grows with sin, y shrinks with cos.
			float sin0 = (float) Math.sin(a0);
			float cos0 = (float) -Math.cos(a0);
			float sin1 = (float) Math.sin(a1);
			float cos1 = (float) -Math.cos(a1);
			band(consumer, innerR - FEATHER, clear, innerR + FEATHER, solid, sin0, cos0, sin1, cos1);
			band(consumer, innerR + FEATHER, solid, outerR - FEATHER, solid, sin0, cos0, sin1, cos1);
			band(consumer, outerR - FEATHER, solid, outerR + FEATHER, clear, sin0, cos0, sin1, cos1);
		}
	}

	/** One quad of a slice, between two radii — outer, inner, inner, outer, as the glow winds. */
	private void band(VertexConsumer consumer, float inner, int innerColour, float outer, int outerColour,
			float sin0, float cos0, float sin1, float cos1) {
		consumer.vertex(pose, cx + outer * sin0, cy + outer * cos0).color(outerColour);
		consumer.vertex(pose, cx + inner * sin0, cy + inner * cos0).color(innerColour);
		consumer.vertex(pose, cx + inner * sin1, cy + inner * cos1).color(innerColour);
		consumer.vertex(pose, cx + outer * sin1, cy + outer * cos1).color(outerColour);
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
		return this.bounds;
	}
}
