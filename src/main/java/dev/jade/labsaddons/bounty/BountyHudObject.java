package dev.jade.labsaddons.bounty;

import dev.jade.labsaddons.hud.HudObject;
import dev.jade.labsaddons.hud.HudObjectSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Spawn hunts: the Bounty Hunt's chemical and chests remaining (chest icon), and the
 * Fishing Weekend's Sunken Treasure barrels remaining (barrel icon). Either can run
 * without the other, so the rows come and go independently under one caption.
 */
public class BountyHudObject extends HudObject {
	public static final String ID = "bounty";
	private static final int DEFAULT_TEXT_COLOR = 0xFFFFC56B;
	private static final int ICON_SIZE = 16;
	private static final int ICON_GAP = 4;
	private static final int LINE_GAP = 2;

	private ItemStack chestIcon;
	private ItemStack barrelIcon;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public HudObjectSettings defaultSettings() {
		HudObjectSettings defaults = new HudObjectSettings();
		defaults.x = 0.985f;
		defaults.y = 0.51f;
		defaults.textColor = DEFAULT_TEXT_COLOR;
		return defaults;
	}

	@Override
	public boolean shouldRender() {
		return BountyTracker.isActive() || SunkenTreasureTracker.isActive();
	}

	@Override
	public EditorAction editorAction() {
		return new EditorAction(Text.translatable("labsaddons.hud.bounty.clear"), () -> {
			BountyTracker.clear();
			SunkenTreasureTracker.clear();
		});
	}

	/** A caption line (null icon) or an icon + text line. */
	private record Row(@Nullable ItemStack icon, Text text) {
	}

	private List<Row> rows(boolean preview) {
		List<Row> rows = new ArrayList<>();
		rows.add(new Row(null, Text.translatable("labsaddons.hud.bounty.name")));
		if (BountyTracker.isActive()) {
			rows.add(new Row(chest(), Text.literal(
					BountyTracker.chem() + " · " + BountyTracker.remaining() + " left")));
		}
		if (SunkenTreasureTracker.isActive()) {
			rows.add(new Row(barrel(), Text.literal(
					"Sunken Treasure · " + SunkenTreasureTracker.remaining() + " left")));
		}
		if (rows.size() == 1 && preview) {
			rows.add(new Row(chest(), Text.literal("Copaprinide · 6 left")));
			rows.add(new Row(barrel(), Text.literal("Sunken Treasure · 4 left")));
		}
		return rows;
	}

	private ItemStack chest() {
		if (chestIcon == null) {
			chestIcon = new ItemStack(Items.CHEST);
		}
		return chestIcon;
	}

	private ItemStack barrel() {
		if (barrelIcon == null) {
			barrelIcon = new ItemStack(Items.BARREL);
		}
		return barrelIcon;
	}

	private static TextRenderer font() {
		return MinecraftClient.getInstance().textRenderer;
	}

	private static int heightOf(Row row) {
		return row.icon() == null ? font().fontHeight : Math.max(ICON_SIZE, font().fontHeight);
	}

	@Override
	public int contentWidth(boolean preview) {
		TextRenderer font = font();
		return rows(preview).stream()
				.mapToInt(row -> (row.icon() == null ? 0 : ICON_SIZE + ICON_GAP) + font.getWidth(row.text()))
				.max().orElse(0);
	}

	@Override
	public int contentHeight(boolean preview) {
		List<Row> rows = rows(preview);
		return rows.stream().mapToInt(BountyHudObject::heightOf).sum()
				+ Math.max(0, rows.size() - 1) * LINE_GAP;
	}

	@Override
	protected void renderContent(DrawContext context, boolean preview) {
		TextRenderer font = font();
		int color = settings().textColor | 0xFF000000;
		int y = 0;
		for (Row row : rows(preview)) {
			int height = heightOf(row);
			int textX = 0;
			if (row.icon() != null) {
				context.drawItem(row.icon(), 0, y + (height - ICON_SIZE) / 2);
				textX = ICON_SIZE + ICON_GAP;
			}
			context.drawText(font, row.text(), textX, y + (height - font.fontHeight) / 2 + 1, color, true);
			y += height + LINE_GAP;
		}
	}
}
