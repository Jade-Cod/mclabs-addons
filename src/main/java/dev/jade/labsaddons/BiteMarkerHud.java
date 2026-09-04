package dev.jade.labsaddons;

import dev.jade.labsaddons.config.LabsAddonsConfig;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
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
	private static Vec3 cameraPos = Vec3.ZERO;
	private static float tickProgress;
	private static boolean frameValid;

	private BiteMarkerHud() {
	}

	/** LevelExtractionEvents.END_EXTRACTION: capture this frame's camera matrices. */
	public static void onEndExtraction(LevelExtractionContext context) {
		CameraRenderState camera = context.levelState().cameraRenderState;
		VIEW.set(camera.viewRotationMatrix);
		PROJECTION.set(camera.projectionMatrix);
		cameraPos = camera.pos;
		tickProgress = context.deltaTracker().getGameTimeDeltaPartialTick(false);
		frameValid = true;
	}

	/** HudElementRegistry element: project and draw the marker. */
	public static void render(GuiGraphicsExtractor drawContext, DeltaTracker tickCounter) {
		if (!frameValid) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.gui.hud.isHidden()) {
			return;
		}

		FishingHook bobber = client.player.fishing;
		if (bobber == null) {
			return;
		}

		Component marker = BiteMarker.markerFor(bobber);
		if (marker == null) {
			return;
		}

		Vec3 labelOffset = BiteMarker.labelPosFor(bobber, tickProgress);
		Vec3 world = bobber.getPosition(tickProgress).add(labelOffset);

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

		float screenX = (clip.x / clip.w * 0.5f + 0.5f) * drawContext.guiWidth();
		float screenY = (1.0f - (clip.y / clip.w * 0.5f + 0.5f)) * drawContext.guiHeight();

		Font font = client.font;
		float scale = BASE_SCALE * LabsAddonsConfig.get().markerScale;
		float halfWidth = font.width(marker) / 2.0f;

		drawContext.pose().pushMatrix();
		drawContext.pose().translate(screenX, screenY);
		drawContext.pose().scale(scale, scale);
		drawContext.text(font, marker,
				Math.round(-halfWidth), -font.lineHeight, 0xFFFFFFFF);
		drawContext.pose().popMatrix();
	}
}
