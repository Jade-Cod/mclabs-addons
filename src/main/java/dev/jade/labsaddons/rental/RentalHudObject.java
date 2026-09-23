package dev.jade.labsaddons.rental;

import dev.jade.labsaddons.hud.FrameValue;
import dev.jade.labsaddons.hud.HudObject;
import dev.jade.labsaddons.hud.HudObjectSettings;
import dev.jade.labsaddons.hud.HudObjects;
import dev.jade.labsaddons.hud.TimeFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.util.List;

/** One row per {@code /rent} item: its icon, its name and the time until it has to go back. */
public class RentalHudObject extends HudObject {
	public static final String ID = "rentals";
	private static final int DEFAULT_TEXT_COLOR = 0xFF66D9FF;
	private static final int OVERDUE_COLOR = 0xFFFF5555;
	private static final int ICON_SIZE = 16;
	private static final int ICON_GAP = 4;
	private static final int LINE_GAP = 3;

	private record Row(ItemStack icon, Text text, boolean overdue) {
	}

	private final FrameValue<List<Row>> rowCache = new FrameValue<>();

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Text group() {
		return HudObjects.BOOSTS;
	}

	@Override
	public HudObjectSettings defaultSettings() {
		HudObjectSettings defaults = new HudObjectSettings();
		defaults.x = 0.985f;
		defaults.y = 0.78f;
		defaults.textColor = DEFAULT_TEXT_COLOR;
		return defaults;
	}

	@Override
	public boolean shouldRender() {
		return RentalTracker.any();
	}

	@Override
	public EditorAction editorAction() {
		return new EditorAction(Text.translatable("labsaddons.hud.rentals.clear"), RentalTracker::clear);
	}

	private List<Row> rows(boolean preview) {
		return rowCache.get(Util.getMeasuringTimeMs(), preview, () -> buildRows(preview));
	}

	private static List<Row> buildRows(boolean preview) {
		if (preview && !RentalTracker.any()) {
			return List.of(new Row(new ItemStack(Items.ACACIA_BOAT), Text.literal("Portable Raft 1:42:10"), false));
		}
		long nowMs = System.currentTimeMillis();
		return RentalTracker.entries().stream().map(e -> {
			long leftMs = e.endMs - nowMs;
			Text time = leftMs > 0 ? Text.literal(TimeFormat.hms(leftMs))
					: Text.translatable("labsaddons.hud.rentals.overdue");
			return new Row(icon(e.itemId), Text.literal(e.name + " ").append(time), leftMs <= 0);
		}).toList();
	}

	private static ItemStack icon(String itemId) {
		Identifier id = Identifier.tryParse(itemId);
		return id != null && Registries.ITEM.containsId(id)
				? new ItemStack(Registries.ITEM.get(id)) : new ItemStack(Items.PAPER);
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
			context.drawText(font, row.text(), ICON_SIZE + ICON_GAP,
					y + (rowHeight() - font.fontHeight) / 2 + 1, row.overdue() ? OVERDUE_COLOR : color, true);
			y += rowHeight() + LINE_GAP;
		}
	}
}
