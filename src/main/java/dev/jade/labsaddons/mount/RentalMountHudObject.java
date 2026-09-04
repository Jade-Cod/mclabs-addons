package dev.jade.labsaddons.mount;

import dev.jade.labsaddons.hud.HudObjectSettings;
import dev.jade.labsaddons.hud.LabeledTimerHudObject;
import dev.jade.labsaddons.hud.TimeFormat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
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
		return new EditorAction(Component.translatable("labsaddons.hud.rental_mount.clear"), RentalMountTimer::clear);
	}

	@Override
	@Nullable
	protected Component header(boolean preview) {
		return Component.translatable("labsaddons.hud.rental_mount.name");
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
