package dev.jade.labsaddons.hud;

import dev.jade.labsaddons.BiteMarkerHud;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.RenderTickCounter;

/**
 * Draws every labsaddons HUD element from a single hook at the tail of the vanilla
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

	/** Draws the bite marker and all widgets, honouring F1, F3 and any screen on top. */
	public static void renderAll(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.options.hudHidden) {
			return;
		}

		// Above the covered check on purpose. The marker is an alert rather than a panel,
		// and MCLabs is a server: the world keeps running while you are in your inventory
		// or the escape menu, so a bite can land while one is open. Hiding it there would
		// lose the fish, which is a worse trade than the overlap it avoids.
		BiteMarkerHud.render(context, tickCounter);

		if (isCovered(client)) {
			return;
		}
		for (HudObject object : HudObjects.all()) {
			object.render(context, false);
		}
	}

	/**
	 * Whether something is drawn over the HUD that the widgets should get out from under.
	 *
	 * <p>The vanilla HUD renders on every frame whether or not a screen is open — the
	 * screen is drawn afterwards, on top — so without this the widgets sit behind your
	 * inventory, the escape menu and the casino boards, laying themselves out and drawing
	 * every frame to be covered up. This is the one gate for all of them, ahead of each
	 * widget's own per-frame work rather than inside it.
	 *
	 * <p>Two things are deliberate. The debug overlay is not a {@code Screen}, so F3 has to
	 * be asked about separately. And chat is a screen the game stays visible behind, open
	 * for as long as you are typing, so it is the one screen the widgets stay up for.
	 *
	 * <p>This also covers the HUD editor, which needs it: the editor draws its own preview
	 * widgets, and the live pass has to stay out of the way so the two are not drawn on top
	 * of one another.
	 */
	private static boolean isCovered(MinecraftClient client) {
		Screen screen = client.currentScreen;
		return covers(client.getDebugHud().shouldShowDebugHud(),
				screen != null, screen instanceof ChatScreen);
	}

	/**
	 * The rule itself, with no Minecraft in it so it can be checked without a game — the
	 * same trade {@link FrameValue} makes with the clock.
	 */
	static boolean covers(boolean debugOverlayShown, boolean screenOpen, boolean screenIsChat) {
		return debugOverlayShown || (screenOpen && !screenIsChat);
	}
}
