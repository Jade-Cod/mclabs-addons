package dev.jade.fishbite.booster;

import dev.jade.fishbite.chem.ChemIcons;
import dev.jade.fishbite.hud.HudObject;
import dev.jade.fishbite.hud.HudObjectSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Server booster countdowns: a chem icon with "<mult>x <time>" per active booster. */
public class BoosterHudObject extends HudObject {
	public static final String ID = "booster_timer";
	private static final int DEFAULT_TEXT_COLOR = 0xFFFFD75F;
	private static final int ICON_SIZE = 16;
	private static final int ICON_GAP = 4;
	private static final int LINE_GAP = 3;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public HudObjectSettings defaultSettings() {
		HudObjectSettings defaults = new HudObjectSettings();
		defaults.x = 0.985f;
		defaults.y = 0.15f;
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

	private record Row(ItemStack icon, String text) {
	}

	private List<Row> rows(boolean preview) {
		List<Row> rows = new ArrayList<>();
		for (var booster : BoosterTracker.active()) {
			rows.add(new Row(ChemIcons.iconFor(booster.item),
					BoosterTracker.formatMultiplier(booster.multiplier) + " "
							+ BoosterTracker.formatRemaining(booster)));
		}
		if (rows.isEmpty() && preview) {
			rows.add(new Row(ChemIcons.iconFor("all"), "1.5x 30:00"));
		}
		return rows;
	}

	private int rowHeight() {
		return Math.max(ICON_SIZE, MinecraftClient.getInstance().textRenderer.fontHeight);
	}

	@Override
	public int contentWidth(boolean preview) {
		TextRenderer font = MinecraftClient.getInstance().textRenderer;
		return rows(preview).stream()
				.mapToInt(row -> ICON_SIZE + ICON_GAP + font.getWidth(row.text())).max().orElse(0);
	}

	@Override
	public int contentHeight(boolean preview) {
		int rowCount = Math.max(1, rows(preview).size());
		return rowCount * rowHeight() + (rowCount - 1) * LINE_GAP;
	}

	@Override
	protected void renderContent(DrawContext context, boolean preview) {
		TextRenderer font = MinecraftClient.getInstance().textRenderer;
		int color = settings().textColor | 0xFF000000;
		int y = 0;
		for (Row row : rows(preview)) {
			context.drawItem(row.icon(), 0, y + (rowHeight() - ICON_SIZE) / 2);
			context.drawText(font, Text.literal(row.text()),
					ICON_SIZE + ICON_GAP, y + (rowHeight() - font.fontHeight) / 2 + 1, color, true);
			y += rowHeight() + LINE_GAP;
		}
	}
}
