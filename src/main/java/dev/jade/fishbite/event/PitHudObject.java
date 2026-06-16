package dev.jade.fishbite.event;

import dev.jade.fishbite.hud.HudObjectSettings;
import dev.jade.fishbite.hud.LabeledTimerHudObject;
import dev.jade.fishbite.hud.TimeFormat;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
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
		defaults.x = 0.012f;
		defaults.y = 0.72f;
		defaults.textColor = DEFAULT_TEXT_COLOR;
		return defaults;
	}

	@Override
	public boolean shouldRender() {
		return PitTracker.isActive();
	}

	@Override
	public EditorAction editorAction() {
		return new EditorAction(Text.translatable("fishbite.hud.pit.clear"), PitTracker::clear);
	}

	@Override
	@Nullable
	protected Text header(boolean preview) {
		return Text.translatable("fishbite.hud.pit.name");
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
