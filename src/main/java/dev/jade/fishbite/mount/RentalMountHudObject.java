package dev.jade.fishbite.mount;

import dev.jade.fishbite.hud.HudObjectSettings;
import dev.jade.fishbite.hud.LabeledTimerHudObject;
import dev.jade.fishbite.hud.TimeFormat;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/** Rental mount countdown (saddle icon). */
public class RentalMountHudObject extends LabeledTimerHudObject {
	public static final String ID = "rental_mount";
	private static final int DEFAULT_TEXT_COLOR = 0xFFFFCC66;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public HudObjectSettings defaultSettings() {
		HudObjectSettings defaults = new HudObjectSettings();
		defaults.x = 0.985f;
		defaults.y = 0.69f;
		defaults.textColor = DEFAULT_TEXT_COLOR;
		return defaults;
	}

	@Override
	public boolean shouldRender() {
		return RentalMountTimer.isActive();
	}

	@Override
	public EditorAction editorAction() {
		return new EditorAction(Text.translatable("fishbite.hud.rental_mount.clear"), RentalMountTimer::clear);
	}

	@Override
	@Nullable
	protected Text header(boolean preview) {
		return Text.translatable("fishbite.hud.rental_mount.name");
	}

	@Override
	@Nullable
	protected ItemStack icon(boolean preview) {
		return new ItemStack(Items.SADDLE);
	}

	@Override
	protected String timeText(boolean preview) {
		return RentalMountTimer.isActive() ? TimeFormat.hms(RentalMountTimer.remainingMs()) : "60:00";
	}
}
