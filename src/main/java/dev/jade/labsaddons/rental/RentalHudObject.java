package dev.jade.labsaddons.rental;

import dev.jade.labsaddons.hud.FrameValue;
import dev.jade.labsaddons.hud.HudObject;
import dev.jade.labsaddons.hud.HudObjectSettings;
import dev.jade.labsaddons.hud.HudObjects;
import dev.jade.labsaddons.hud.TimeFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** One row per {@code /rent} item: its icon, its name and the time until it has to go back. */
public class RentalHudObject extends HudObject {
	public static final String ID = "rentals";
	private static final int DEFAULT_TEXT_COLOR = 0xFF66D9FF;
	private static final int OVERDUE_COLOR = 0xFFFF5555;
	private static final int ICON_SIZE = 16;
	private static final int ICON_GAP = 4;
	private static final int LINE_GAP = 3;

	private record Row(ItemStack icon, Component text, boolean overdue) {
	}

	private final FrameValue<List<Row>> rowCache = new FrameValue<>();

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Component group() {
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
		return new EditorAction(Component.translatable("labsaddons.hud.rentals.clear"), RentalTracker::clear);
	}

	private List<Row> rows(boolean preview) {
		return rowCache.get(Util.getMillis(), preview, () -> buildRows(preview));
	}

	private static List<Row> buildRows(boolean preview) {
		if (preview && !RentalTracker.any()) {
			return List.of(new Row(new ItemStack(Items.ACACIA_BOAT), Component.literal("Portable Raft 1:42:10"), false));
		}
		long nowMs = System.currentTimeMillis();
		return RentalTracker.entries().stream().map(e -> {
			long leftMs = e.endMs - nowMs;
			Component time = leftMs > 0 ? Component.literal(TimeFormat.hms(leftMs))
					: Component.translatable("labsaddons.hud.rentals.overdue");
			return new Row(icon(e.itemId), Component.literal(e.name + " ").append(time), leftMs <= 0);
		}).toList();
	}

	private static ItemStack icon(String itemId) {
		Identifier id = Identifier.tryParse(itemId);
		return id == null ? new ItemStack(Items.PAPER)
				: BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElseGet(() -> new ItemStack(Items.PAPER));
	}

	private int rowHeight() {
		return Math.max(ICON_SIZE, Minecraft.getInstance().font.lineHeight);
	}

	@Override
	public int contentWidth(boolean preview) {
		Font font = Minecraft.getInstance().font;
		return rows(preview).stream()
				.mapToInt(row -> ICON_SIZE + ICON_GAP + font.width(row.text())).max().orElse(0);
	}

	@Override
	public int contentHeight(boolean preview) {
		int rowCount = Math.max(1, rows(preview).size());
		return rowCount * rowHeight() + (rowCount - 1) * LINE_GAP;
	}

	@Override
	protected void renderContent(GuiGraphicsExtractor context, boolean preview) {
		Font font = Minecraft.getInstance().font;
		int color = settings().textColor | 0xFF000000;
		int y = 0;
		for (Row row : rows(preview)) {
			context.item(row.icon(), 0, y + (rowHeight() - ICON_SIZE) / 2);
			context.text(font, row.text(), ICON_SIZE + ICON_GAP,
					y + (rowHeight() - font.lineHeight) / 2 + 1, row.overdue() ? OVERDUE_COLOR : color, true);
			y += rowHeight() + LINE_GAP;
		}
	}
}
