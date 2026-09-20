package dev.jade.labsaddons.double2;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

final class D2WheelRenderState implements SimpleGuiElementRenderState {
	private static final int SUBDIVISIONS = 4;
	private static final int DISC_SLICES = 32;

	private final Matrix3x2fc pose;
	private final float cx;
	private final float cy;
	private final float outerR;
	private final float innerR;
	private final Lab[] segments;
	private final float offset;
	private final int accent;
	private final Lab settledLab;
	private final int winningIndex;
	private final float settledPulse;
	private final float pointerAngle;
	private final ScreenRect bounds;

	D2WheelRenderState(Matrix3x2fc pose, float cx, float cy, float outerR, float innerR,
			Lab[] segments, float offset, int accent, Lab settledLab, int winningIndex,
			float settledPulse, float pointerAngle) {
		this.pose = new Matrix3x2f(pose);
		this.cx = cx;
		this.cy = cy;
		this.outerR = outerR;
		this.innerR = innerR;
		this.segments = segments != null ? segments : new Lab[D2Ring.SIZE];
		this.offset = offset;
		this.accent = accent;
		this.settledLab = settledLab;
		this.winningIndex = winningIndex;
		this.settledPulse = settledPulse;
		this.pointerAngle = pointerAngle;

		int minX = (int) Math.floor(cx - outerR - 4);
		int minY = (int) Math.floor(cy - outerR - 10);
		int sizeX = (int) Math.ceil((outerR + 4) * 2);
		int sizeY = (int) Math.ceil(outerR * 2 + 14);
		this.bounds = new ScreenRect(minX, minY, sizeX, sizeY).transformEachVertex(this.pose);
	}

	@Override
	public void setupVertices(VertexConsumer consumer) {
		double pointerIndex = offset + D2Ring.POINTER;
		double segRad = Math.PI * 2.0 / D2Ring.SIZE;
		double step = segRad / SUBDIVISIONS;

		// 1. Draw 25 annular segments
		for (int i = 0; i < D2Ring.SIZE; i++) {
			Lab lab = segments[i];
			int color;
			if (lab == null) {
				color = D2Wheel.UNSEEN;
			} else if (settledLab != null) {
				if (i == winningIndex) {
					color = blendColor(lab.color(), lab.lift(), settledPulse * 0.7f);
				} else {
					color = dimColor(lab.color(), 0.35f);
				}
			} else {
				color = lab.color();
			}

			double segCenter = (i - pointerIndex) * segRad - Math.PI / 2.0;
			double startAngle = segCenter - segRad / 2.0;

			for (int s = 0; s < SUBDIVISIONS; s++) {
				double a1 = startAngle + s * step;
				double a2 = startAngle + (s + 1) * step;

				float cos1 = (float) Math.cos(a1);
				float sin1 = (float) Math.sin(a1);
				float cos2 = (float) Math.cos(a2);
				float sin2 = (float) Math.sin(a2);

				float xOut1 = cx + outerR * cos1;
				float yOut1 = cy + outerR * sin1;
				float xIn1 = cx + innerR * cos1;
				float yIn1 = cy + innerR * sin1;
				float xIn2 = cx + innerR * cos2;
				float yIn2 = cy + innerR * sin2;
				float xOut2 = cx + outerR * cos2;
				float yOut2 = cy + outerR * sin2;

				consumer.vertex(pose, xOut1, yOut1).color(color);
				consumer.vertex(pose, xIn1, yIn1).color(color);
				consumer.vertex(pose, xIn2, yIn2).color(color);
				consumer.vertex(pose, xOut2, yOut2).color(color);
			}
		}

		// 1b. Metallic perimeter pegs between segments
		float pegRIn = outerR - 1.2f;
		float pegROut = outerR + 1.2f;
		float pegHalfW = 0.6f;
		int pegColor = 0xFF8A94A6;

		for (int i = 0; i < D2Ring.SIZE; i++) {
			double segCenter = (i - pointerIndex) * segRad - Math.PI / 2.0;
			double startAngle = segCenter - segRad / 2.0;
			float cos = (float) Math.cos(startAngle);
			float sin = (float) Math.sin(startAngle);
			float nx = -sin * pegHalfW;
			float ny = cos * pegHalfW;

			float xIn1 = cx + pegRIn * cos - nx;
			float yIn1 = cy + pegRIn * sin - ny;
			float xIn2 = cx + pegRIn * cos + nx;
			float yIn2 = cy + pegRIn * sin + ny;
			float xOut1 = cx + pegROut * cos - nx;
			float yOut1 = cy + pegROut * sin - ny;
			float xOut2 = cx + pegROut * cos + nx;
			float yOut2 = cy + pegROut * sin + ny;

			consumer.vertex(pose, xOut1, yOut1).color(pegColor);
			consumer.vertex(pose, xIn1, yIn1).color(pegColor);
			consumer.vertex(pose, xIn2, yIn2).color(pegColor);
			consumer.vertex(pose, xOut2, yOut2).color(pegColor);
		}

		// 2. Inner accent disc (rim)
		int rimColor = accent;
		if (settledLab != null) {
			rimColor = blendColor(accent, settledLab.lift(), settledPulse * 0.7f);
		}
		drawDisc(consumer, innerR, rimColor, DISC_SLICES);

		// 3. Hub background disc
		drawDisc(consumer, Math.max(0f, innerR - 1f), D2Wheel.HUB_BG, DISC_SLICES);

		// 4. Pointer needle & metallic pivot pin
		float pivotX = cx;
		float pivotY = cy - outerR - 6.5f;
		float length = 9.5f;
		float wb = 3.0f;
		float wt = 0.6f;

		float sinT = (float) Math.sin(pointerAngle);
		float cosT = (float) Math.cos(pointerAngle);

		// Direction along needle towards tip
		float ux = -sinT;
		float uy = cosT;
		// Normal to the right of needle
		float nx = cosT;
		float ny = sinT;

		float blX = pivotX - wb * nx;
		float blY = pivotY - wb * ny;
		float brX = pivotX + wb * nx;
		float brY = pivotY + wb * ny;
		float tlX = pivotX + length * ux - wt * nx;
		float tlY = pivotY + length * uy - wt * ny;
		float trX = pivotX + length * ux + wt * nx;
		float trY = pivotY + length * uy + wt * ny;

		// Subtle dark drop shadow behind needle
		int shadowColor = 0xAA080A0E;
		consumer.vertex(pose, blX + 0.5f, blY + 0.8f).color(shadowColor);
		consumer.vertex(pose, tlX + 0.5f, tlY + 0.8f).color(shadowColor);
		consumer.vertex(pose, trX + 0.5f, trY + 0.8f).color(shadowColor);
		consumer.vertex(pose, brX + 0.5f, brY + 0.8f).color(shadowColor);

		// Pointer needle body
		consumer.vertex(pose, blX, blY).color(accent);
		consumer.vertex(pose, tlX, tlY).color(accent);
		consumer.vertex(pose, trX, trY).color(accent);
		consumer.vertex(pose, brX, brY).color(accent);

		// Metallic pivot pin cap (diamond)
		float pinR = 1.8f;
		int pinColor = 0xFFD8E2EC;
		consumer.vertex(pose, pivotX - pinR, pivotY).color(pinColor);
		consumer.vertex(pose, pivotX, pivotY + pinR).color(pinColor);
		consumer.vertex(pose, pivotX + pinR, pivotY).color(pinColor);
		consumer.vertex(pose, pivotX, pivotY - pinR).color(pinColor);
	}

	private static int dimColor(int argb, float factor) {
		int a = (argb >>> 24) & 0xFF;
		int r = Math.round(((argb >>> 16) & 0xFF) * factor);
		int g = Math.round(((argb >>> 8) & 0xFF) * factor);
		int b = Math.round((argb & 0xFF) * factor);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	private static int blendColor(int c1, int c2, float t) {
		float inv = 1f - t;
		int a = Math.round(((c1 >>> 24) & 0xFF) * inv + ((c2 >>> 24) & 0xFF) * t);
		int r = Math.round(((c1 >>> 16) & 0xFF) * inv + ((c2 >>> 16) & 0xFF) * t);
		int g = Math.round(((c1 >>> 8) & 0xFF) * inv + ((c2 >>> 8) & 0xFF) * t);
		int b = Math.round((c1 & 0xFF) * inv + (c2 & 0xFF) * t);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	private void drawDisc(VertexConsumer consumer, float r, int color, int slices) {
		if (r <= 0f) {
			return;
		}
		double step = Math.PI * 2.0 / slices;
		for (int i = 0; i < slices; i += 2) {
			double a0 = i * step;
			double a1 = (i + 1) * step;
			double a2 = (i + 2) * step;

			float x0 = cx + r * (float) Math.cos(a0);
			float y0 = cy + r * (float) Math.sin(a0);
			float x1 = cx + r * (float) Math.cos(a1);
			float y1 = cy + r * (float) Math.sin(a1);
			float x2 = cx + r * (float) Math.cos(a2);
			float y2 = cy + r * (float) Math.sin(a2);

			consumer.vertex(pose, cx, cy).color(color);
			consumer.vertex(pose, x0, y0).color(color);
			consumer.vertex(pose, x1, y1).color(color);
			consumer.vertex(pose, x2, y2).color(color);
		}
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
