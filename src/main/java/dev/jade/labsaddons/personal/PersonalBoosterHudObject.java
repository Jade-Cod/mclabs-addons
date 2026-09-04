package dev.jade.labsaddons.personal;

import dev.jade.labsaddons.hud.HudObject;
import dev.jade.labsaddons.hud.HudObjectSettings;
import dev.jade.labsaddons.hud.TimeFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Personal boosts: chem price (gold ingot) and prestige (xp bottle) rows. */
public class PersonalBoosterHudObject extends HudObject {
	public static final String ID = "personal_boosters";
	private static final int DEFAULT_TEXT_COLOR = 0xFF7FFF8F;
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
		defaults.y = 0.60f;
		defaults.textColor = DEFAULT_TEXT_COLOR;
		return defaults;
	}

	@Override
	public boolean shouldRender() {
		return PersonalBoosters.anyActive();
	}

	@Override
	public EditorAction editorAction() {
		return new EditorAction(Component.translatable("labsaddons.hud.personal_boosters.clear"), PersonalBoosters::clear);
	}

	private record Row(ItemStack icon, String text) {
	}

	private List<Row> rows(boolean preview) {
		List<Row> rows = new ArrayList<>();
		if (PersonalBoosters.chemRemainingMs() > 0) {
			rows.add(new Row(new ItemStack(Items.GOLD_INGOT),
					"Chem Price 10% " + TimeFormat.hms(PersonalBoosters.chemRemainingMs())));
		}
		if (PersonalBoosters.prestigeRemainingMs() > 0) {
			rows.add(new Row(new ItemStack(Items.EXPERIENCE_BOTTLE),
					"Prestige 10% " + TimeFormat.hms(PersonalBoosters.prestigeRemainingMs())));
		}
		if (rows.isEmpty() && preview) {
			rows.add(new Row(new ItemStack(Items.GOLD_INGOT), "Chem Price 10% 29:34"));
			rows.add(new Row(new ItemStack(Items.EXPERIENCE_BOTTLE), "Prestige 10% 1d 8h"));
		}
		return rows;
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
			context.text(font, Component.literal(row.text()),
					ICON_SIZE + ICON_GAP, y + (rowHeight() - font.lineHeight) / 2 + 1, color, true);
			y += rowHeight() + LINE_GAP;
		}
	}
}
