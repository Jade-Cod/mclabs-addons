package dev.jade.fishbite.daily;

import dev.jade.fishbite.hud.HudObject;
import dev.jade.fishbite.hud.HudObjectSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Daily claim reminders that reset at 9 PM Pacific: the daily spin and /sm claim. */
public class DailyReminderHudObject extends HudObject {
	public static final String ID = "dailies";
	private static final int DEFAULT_TEXT_COLOR = 0xFFFFE079;
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
		defaults.x = 0.012f;
		defaults.y = 0.30f;
		defaults.textColor = DEFAULT_TEXT_COLOR;
		return defaults;
	}

	@Override
	public boolean shouldRender() {
		return DailyTracker.dailyPending() || DailyTracker.smPending();
	}

	private record Row(ItemStack icon, String text) {
	}

	private List<Row> rows(boolean preview) {
		List<Row> rows = new ArrayList<>();
		if (preview || DailyTracker.dailyPending()) {
			rows.add(new Row(new ItemStack(Items.CLOCK), "Daily Spin: /daily"));
		}
		if (preview || DailyTracker.smPending()) {
			rows.add(new Row(new ItemStack(Items.EMERALD), "Investor: /sm claim"));
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
