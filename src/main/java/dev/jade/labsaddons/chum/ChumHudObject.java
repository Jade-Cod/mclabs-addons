package dev.jade.labsaddons.chum;

import dev.jade.labsaddons.hud.HudObject;
import dev.jade.labsaddons.hud.HudObjectSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Chum Bucket double-fish countdown: bucket icon + remaining time. */
public class ChumHudObject extends HudObject {
	public static final String ID = "chum_timer";
	public static final int DEFAULT_TEXT_COLOR = 0xFF55FFFF;
	private static final int ICON_SIZE = 16;
	private static final int ICON_TEXT_GAP = 4;
	private static final String PREVIEW_TIME = "20:00";

	private ItemStack chumIcon;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public HudObjectSettings defaultSettings() {
		HudObjectSettings defaults = new HudObjectSettings();
		defaults.x = 0.985f;
		defaults.y = 0.06f;
		defaults.textColor = DEFAULT_TEXT_COLOR;
		return defaults;
	}

	@Override
	public boolean shouldRender() {
		return ChumTimer.isActive();
	}

	@Override
	public EditorAction editorAction() {
		return new EditorAction(Component.translatable("labsaddons.hud.chum.reset"), ChumTimer::reset);
	}

	private String timeText(boolean preview) {
		return preview && !ChumTimer.isActive() ? PREVIEW_TIME : ChumTimer.formatRemaining();
	}

	@Override
	public int contentWidth(boolean preview) {
		return ICON_SIZE + ICON_TEXT_GAP
				+ Minecraft.getInstance().font.width(timeText(preview));
	}

	@Override
	public int contentHeight(boolean preview) {
		return ICON_SIZE;
	}

	@Override
	protected void renderContent(GuiGraphicsExtractor context, boolean preview) {
		Minecraft client = Minecraft.getInstance();
		context.item(icon(), 0, 0);
		int textY = (ICON_SIZE - client.font.lineHeight) / 2 + 1;
		context.text(client.font, Component.literal(timeText(preview)),
				ICON_SIZE + ICON_TEXT_GAP, textY, settings().textColor | 0xFF000000, true);
	}

	/** Salmon bucket carrying the chum model data (server pack shows real art). */
	private ItemStack icon() {
		if (chumIcon == null) {
			ItemStack stack = new ItemStack(Items.SALMON_BUCKET);
			stack.set(DataComponents.CUSTOM_MODEL_DATA,
					new CustomModelData(List.of(), List.of(), List.of("chumbucket"), List.of()));
			chumIcon = stack;
		}
		return chumIcon;
	}
}
