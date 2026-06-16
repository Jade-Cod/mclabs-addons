package dev.jade.fishbite.hud;

import dev.jade.fishbite.BiteMarkerHud;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

/**
 * Draws every fishbite HUD element from a single hook at the tail of the vanilla
 * HUD render (see {@code InGameHudMixin}).
 *
 * <p>Historically these elements were registered through Fabric's
 * {@code HudElementRegistry.addLast}, but client overlays such as Feather replace
 * the vanilla per-element render anchors that Fabric's layer dispatch hangs off
 * of, swallowing every {@code addLast} element. Injecting at the return of the
 * vanilla HUD render is independent of that layer system, so it survives the
 * overlay while remaining correct in vanilla.
 */
public final class HudRenderDispatcher {
	private HudRenderDispatcher() {
	}

	/** Draws the bite marker and all widgets, honouring F1 and the editor screen. */
	public static void renderAll(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.options.hudHidden) {
			return;
		}
		// The editor draws its own preview widgets; skip the live pass while it is
		// open so active widgets aren't drawn twice underneath the previews.
		if (client.currentScreen instanceof HudEditScreen) {
			return;
		}

		BiteMarkerHud.render(context, tickCounter);
		for (HudObject object : HudObjects.all()) {
			object.render(context, false);
		}
	}
}
