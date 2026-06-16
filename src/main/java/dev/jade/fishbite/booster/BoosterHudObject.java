package dev.jade.fishbite.booster;

import dev.jade.fishbite.hud.HudObject;
import dev.jade.fishbite.hud.HudObjectSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.List;

/** Server booster countdowns: "<item> <mult>x <time>" per active booster. */
public class BoosterHudObject extends HudObject {
	public static final String ID = "booster_timer";
	private static final int DEFAULT_TEXT_COLOR = 0xFFFFD75F;
	private static final int LINE_GAP = 2;
	private static final String PREVIEW_ROW = "All 1.5x 30:00";

	@Override
	public String id() {
		return ID;
	}

	@Override
	public HudObjectSettings defaultSettings() {
		HudObjectSettings defaults = new HudObjectSettings();
		defaults.x = 0.012f;
		defaults.y = 0.52f;
		defaults.textColor = DEFAULT_TEXT_COLOR;
		return defaults;
	}

	@Override
	public boolean shouldRender() {
		return !BoosterTracker.active().isEmpty();
	}

	@Override
	public EditorAction editorAction() {
		return new EditorAction(Text.translatable("fishbite.hud.booster.clear"), BoosterTracker::clear);
	}

	private List<String> rows(boolean preview) {
		List<String> rows = BoosterTracker.active().stream()
				.map(booster -> booster.item + " "
						+ BoosterTracker.formatMultiplier(booster.multiplier) + " "
						+ BoosterTracker.formatRemaining(booster))
				.toList();
		if (rows.isEmpty() && preview) {
			return List.of(PREVIEW_ROW);
		}
		return rows;
	}

	@Override
	public int contentWidth(boolean preview) {
		TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
		return rows(preview).stream().mapToInt(textRenderer::getWidth).max().orElse(0);
	}

	@Override
	public int contentHeight(boolean preview) {
		TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
		int rowCount = Math.max(1, rows(preview).size());
		return rowCount * (textRenderer.fontHeight + LINE_GAP) - LINE_GAP;
	}

	@Override
	protected void renderContent(DrawContext context, boolean preview) {
		TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
		int color = settings().textColor | 0xFF000000;
		int y = 0;
		for (String row : rows(preview)) {
			context.drawText(textRenderer, Text.literal(row), 0, y, color, true);
			y += textRenderer.fontHeight + LINE_GAP;
		}
	}
}
