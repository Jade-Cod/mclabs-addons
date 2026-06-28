package dev.jade.fishbite.hud;

import dev.jade.fishbite.config.FishBiteConfig;
import net.minecraft.client.gui.DrawContext;

/**
 * A draggable, scalable HUD widget with a rounded translucent background.
 * Subclasses provide an id, default settings, content size, and content
 * drawing; this base handles settings lookup, background, scaling, and the
 * screen-space bounds used by the editor for dragging/snapping.
 */
public abstract class HudObject {
	/** Padding between content and background edge, in unscaled px. */
	public static final int PADDING = 3;

	public abstract String id();

	/** Defaults applied the first time this object appears in the config. */
	public abstract HudObjectSettings defaultSettings();

	/** Human-readable widget name, shown in the editor and settings. */
	public net.minecraft.text.Text displayName() {
		return net.minecraft.text.Text.translatable("fishbite.hud." + id() + ".name");
	}

	public abstract int contentWidth(boolean preview);

	public abstract int contentHeight(boolean preview);

	/** Draw content at (0,0) in unscaled coordinates; matrices pre-transformed. */
	protected abstract void renderContent(DrawContext context, boolean preview);

	/** Whether the object should currently draw outside the editor. */
	public abstract boolean shouldRender();

	/** Optional widget-specific action shown in its settings popup. */
	public record EditorAction(net.minecraft.text.Text label, Runnable action) {
	}

	/** @return an extra action button for this widget's settings, or null. */
	public EditorAction editorAction() {
		return null;
	}

	/** A widget-specific on/off setting shown as a toggle in the editor inspector. */
	public record ToggleOption(net.minecraft.text.Text label,
			java.util.function.BooleanSupplier value,
			java.util.function.Consumer<Boolean> onChange) {
	}

	/** @return extra on/off toggles for this widget's settings (empty by default). */
	public java.util.List<ToggleOption> toggleOptions() {
		return java.util.List.of();
	}

	public HudObjectSettings settings() {
		FishBiteConfig config = FishBiteConfig.get();
		HudObjectSettings settings = config.hudObjects.get(id());
		if (settings == null) {
			settings = defaultSettings().copy();
			config.hudObjects.put(id(), settings);
			config.save();
		}
		return settings;
	}

	/**
	 * Content-origin X in screen pixels, auto-anchored: a widget whose anchor
	 * point is on the right half of the screen pins its right edge and grows
	 * leftward, so longer text never runs off the right side.
	 */
	private int contentOriginXpx(int scaledWidth, boolean preview) {
		HudObjectSettings settings = settings();
		int anchorPx = Math.round(settings.x * scaledWidth);
		return settings.x > 0.5f
				? anchorPx - Math.round(contentWidth(preview) * settings.scale)
				: anchorPx;
	}

	/** Renders background + content. {@code preview} forces visibility (editor). */
	public final void render(DrawContext context, boolean preview) {
		HudObjectSettings settings = settings();
		if (!preview && (!settings.enabled || !shouldRender())) {
			return;
		}

		int x = contentOriginXpx(context.getScaledWindowWidth(), preview);
		int y = Math.round(settings.y * context.getScaledWindowHeight());

		context.getMatrices().pushMatrix();
		context.getMatrices().translate(x, y);
		context.getMatrices().scale(settings.scale, settings.scale);
		if (settings.backgroundEnabled) {
			drawRoundedRect(context, -PADDING, -PADDING,
					contentWidth(preview) + PADDING * 2, contentHeight(preview) + PADDING * 2,
					settings.backgroundColor);
		}
		renderContent(context, preview);
		context.getMatrices().popMatrix();
	}

	/**
	 * Commits a desired background-box top-left (screen px) into {@code settings.x/y},
	 * using the same auto-anchor rule as {@link #contentOriginXpx}: a box whose centre
	 * lands on the right half of the screen pins its right edge (so it grows leftward).
	 * Shared by the editor's drag, keyboard-nudge, and anchor-snap paths.
	 */
	public final void setScreenBoxPosition(int boxX, int boxY, int screenWidth, int screenHeight) {
		HudObjectSettings settings = settings();
		float scale = settings.scale;
		int boxWidth = screenBounds(screenWidth, screenHeight, true)[2];
		boolean anchorRight = (boxX + boxWidth / 2) > screenWidth / 2;
		settings.x = anchorRight
				? (boxX + boxWidth - PADDING * scale) / screenWidth
				: (boxX + PADDING * scale) / screenWidth;
		settings.y = (boxY + PADDING * scale) / screenHeight;
	}

	/** Background box in screen pixels: {x, y, w, h}. Used by the editor. */
	public final int[] screenBounds(int screenWidth, int screenHeight, boolean preview) {
		HudObjectSettings settings = settings();
		float scale = settings.scale;
		int x = contentOriginXpx(screenWidth, preview);
		int y = Math.round(settings.y * screenHeight);
		int bgX = Math.round(x - PADDING * scale);
		int bgY = Math.round(y - PADDING * scale);
		int bgW = Math.round((contentWidth(preview) + PADDING * 2) * scale);
		int bgH = Math.round((contentHeight(preview) + PADDING * 2) * scale);
		return new int[]{bgX, bgY, bgW, bgH};
	}

	/** Rounded-corner rect from non-overlapping fills (radius 2). */
	public static void drawRoundedRect(DrawContext context, int x, int y, int w, int h, int color) {
		context.fill(x + 2, y, x + w - 2, y + h, color);
		context.fill(x, y + 2, x + 2, y + h - 2, color);
		context.fill(x + w - 2, y + 2, x + w, y + h - 2, color);
		context.fill(x + 1, y + 1, x + 2, y + 2, color);
		context.fill(x + w - 2, y + 1, x + w - 1, y + 2, color);
		context.fill(x + 1, y + h - 2, x + 2, y + h - 1, color);
		context.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, color);
	}
}
