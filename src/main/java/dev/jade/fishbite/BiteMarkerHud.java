package dev.jade.fishbite;

import dev.jade.fishbite.config.FishBiteConfig;
import dev.jade.fishbite.mixin.CameraAccessor;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Draws the bite marker as a HUD overlay: the bobber's world position is
 * projected to screen coordinates using the view/projection matrices captured
 * from the current frame, and the marker is drawn as GUI text. This bypasses
 * the world-render label pipeline entirely, which client overlays such as
 * Feather replace with their own (swallowing third-party submitLabel calls).
 */
public final class BiteMarkerHud {
	/** Base GUI text scale at 100% marker size (vanilla GUI text is small). */
	private static final float BASE_SCALE = 1.5f;
	/** Clip-space w below this is behind the camera; skip drawing. */
	private static final float MIN_CLIP_W = 0.05f;

	// View/projection of the frame being rendered, captured at extraction end.
	private static final Matrix4f VIEW = new Matrix4f();
	private static final Matrix4f PROJECTION = new Matrix4f();
	private static Vec3d cameraPos = Vec3d.ZERO;
	private static float tickProgress;
	private static boolean frameValid;

	private BiteMarkerHud() {
	}

	/** WorldRenderEvents.END_EXTRACTION: capture this frame's camera matrices. */
	public static void onEndExtraction(WorldExtractionContext context) {
		VIEW.set(context.viewMatrix());
		PROJECTION.set(context.cullProjectionMatrix());
		cameraPos = ((CameraAccessor) context.camera()).getPos();
		tickProgress = context.tickCounter().getTickProgress(false);
		frameValid = true;
	}

	/** HudElementRegistry element: project and draw the marker. */
	public static void render(DrawContext drawContext, RenderTickCounter tickCounter) {
		if (!frameValid) {
			return;
		}

		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.options.hudHidden) {
			return;
		}

		FishingBobberEntity bobber = client.player.fishHook;
		if (bobber == null) {
			return;
		}

		Text marker = BiteMarker.markerFor(bobber);
		if (marker == null) {
			return;
		}

		Vec3d labelOffset = BiteMarker.labelPosFor(bobber, tickProgress);
		Vec3d world = bobber.getLerpedPos(tickProgress).add(labelOffset);

		// Camera-relative position through view + projection into clip space.
		Vector4f clip = new Vector4f(
				(float) (world.x - cameraPos.x),
				(float) (world.y - cameraPos.y),
				(float) (world.z - cameraPos.z),
				1.0f);
		VIEW.transform(clip);
		PROJECTION.transform(clip);
		if (clip.w < MIN_CLIP_W) {
			return;
		}

		float screenX = (clip.x / clip.w * 0.5f + 0.5f) * drawContext.getScaledWindowWidth();
		float screenY = (1.0f - (clip.y / clip.w * 0.5f + 0.5f)) * drawContext.getScaledWindowHeight();

		TextRenderer textRenderer = client.textRenderer;
		float scale = BASE_SCALE * FishBiteConfig.get().markerScale;
		float halfWidth = textRenderer.getWidth(marker) / 2.0f;

		drawContext.getMatrices().pushMatrix();
		drawContext.getMatrices().translate(screenX, screenY);
		drawContext.getMatrices().scale(scale, scale);
		drawContext.drawTextWithShadow(textRenderer, marker,
				Math.round(-halfWidth), -textRenderer.fontHeight, 0xFFFFFFFF);
		drawContext.getMatrices().popMatrix();
	}
}
