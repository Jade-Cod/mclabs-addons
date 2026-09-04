package dev.jade.labsaddons.event;

import dev.jade.labsaddons.hud.HudObjectSettings;
import dev.jade.labsaddons.hud.LabeledTimerHudObject;
import dev.jade.labsaddons.hud.TimeFormat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** "The Pit" open-window countdown with a netherite sword icon. */
public class PitHudObject extends LabeledTimerHudObject {
	public static final String ID = "pit_timer";
	private static final int DEFAULT_TEXT_COLOR = 0xFFFF8060;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public HudObjectSettings defaultSettings() {
		HudObjectSettings defaults = new HudObjectSettings();
		defaults.x = 0.985f;
		defaults.y = 0.33f;
		defaults.textColor = DEFAULT_TEXT_COLOR;
		return defaults;
	}

	@Override
	public boolean shouldRender() {
		return PitTracker.isActive();
	}

	@Override
	public EditorAction editorAction() {
		return new EditorAction(Component.translatable("labsaddons.hud.pit.clear"), PitTracker::clear);
	}

	@Override
	@Nullable
	protected Component header(boolean preview) {
		return Component.translatable("labsaddons.hud.pit.name");
	}

	@Override
	@Nullable
	protected ItemStack icon(boolean preview) {
		return new ItemStack(Items.NETHERITE_SWORD);
	}

	@Override
	protected String timeText(boolean preview) {
		return PitTracker.isActive() ? TimeFormat.hms(PitTracker.remainingMs()) : "30:00";
	}
}
