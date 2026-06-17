package dev.jade.fishbite.labwars;

import dev.jade.fishbite.hud.HudObject;
import dev.jade.fishbite.hud.HudObjectSettings;
import dev.jade.fishbite.hud.TimeFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.List;

/** Lab Wars revenue boosters: one row per active booster (icon + type + multiplier + time / sync hint). */
public class LabWarsHudObject extends HudObject {
	public static final String ID = "lab_wars";
	private static final int DEFAULT_TEXT_COLOR = 0xFFC792EA;
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
		defaults.y = 0.24f;
		defaults.textColor = DEFAULT_TEXT_COLOR;
		return defaults;
	}

	@Override
	public boolean shouldRender() {
		return !LabWarsTracker.active().isEmpty();
	}

	@Override
	public EditorAction editorAction() {
		return new EditorAction(Text.translatable("fishbite.hud.lab_wars.clear"), LabWarsTracker::clear);
	}

	private record Row(ItemStack icon, String text) {
	}

	private List<Row> rows(boolean preview) {
		List<LabWarsBooster> boosters = LabWarsTracker.active();
		if (boosters.isEmpty() && preview) {
			return List.of(
					new Row(new ItemStack(LabWarsType.icon("fishing")), "Fishing 1.5x 59:30"),
					new Row(new ItemStack(LabWarsType.icon("fishing")), "Fishing 1.1x 4:30"),
					new Row(new ItemStack(LabWarsType.icon("arrests")), "Arrests 1.25x ⟳ /lw rates"));
		}
		return boosters.stream().map(b -> {
			String time = b.timerKnown ? TimeFormat.hms(b.remainingMs()) : "⟳ /lw rates";
			String text = LabWarsType.display(b.key) + " " + formatMult(b.multiplier) + "x " + time;
			return new Row(new ItemStack(LabWarsType.icon(b.key)), text);
		}).toList();
	}

	private static String formatMult(double multiplier) {
		return multiplier == Math.floor(multiplier) ? Long.toString((long) multiplier) : Double.toString(multiplier);
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
