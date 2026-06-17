package dev.jade.fishbite.chum;

import dev.jade.fishbite.hud.HudObject;
import dev.jade.fishbite.hud.HudObjectSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

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
		return new EditorAction(Text.translatable("fishbite.hud.chum.reset"), ChumTimer::reset);
	}

	private String timeText(boolean preview) {
		return preview && !ChumTimer.isActive() ? PREVIEW_TIME : ChumTimer.formatRemaining();
	}

	@Override
	public int contentWidth(boolean preview) {
		return ICON_SIZE + ICON_TEXT_GAP
				+ MinecraftClient.getInstance().textRenderer.getWidth(timeText(preview));
	}

	@Override
	public int contentHeight(boolean preview) {
		return ICON_SIZE;
	}

	@Override
	protected void renderContent(DrawContext context, boolean preview) {
		MinecraftClient client = MinecraftClient.getInstance();
		context.drawItem(icon(), 0, 0);
		int textY = (ICON_SIZE - client.textRenderer.fontHeight) / 2 + 1;
		context.drawText(client.textRenderer, Text.literal(timeText(preview)),
				ICON_SIZE + ICON_TEXT_GAP, textY, settings().textColor | 0xFF000000, true);
	}

	/** Salmon bucket carrying the chum model data (server pack shows real art). */
	private ItemStack icon() {
		if (chumIcon == null) {
			ItemStack stack = new ItemStack(Items.SALMON_BUCKET);
			stack.set(DataComponentTypes.CUSTOM_MODEL_DATA,
					new CustomModelDataComponent(List.of(), List.of(), List.of("chumbucket"), List.of()));
			chumIcon = stack;
		}
		return chumIcon;
	}
}
