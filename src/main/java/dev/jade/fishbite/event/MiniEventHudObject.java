package dev.jade.fishbite.event;

import dev.jade.fishbite.hud.HudObjectSettings;
import dev.jade.fishbite.hud.LabeledTimerHudObject;
import dev.jade.fishbite.hud.TimeFormat;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** Mini-event widget: upcoming countdown, then active type + time + reward. */
public class MiniEventHudObject extends LabeledTimerHudObject {
	public static final String ID = "mini_event";
	private static final int DEFAULT_TEXT_COLOR = 0xFF7FE0FF;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public HudObjectSettings defaultSettings() {
		HudObjectSettings defaults = new HudObjectSettings();
		defaults.x = 0.012f;
		defaults.y = 0.62f;
		defaults.textColor = DEFAULT_TEXT_COLOR;
		return defaults;
	}

	@Override
	public boolean shouldRender() {
		return MiniEventTracker.isActive() || MiniEventTracker.isUpcoming();
	}

	@Override
	public EditorAction editorAction() {
		return new EditorAction(Text.translatable("fishbite.hud.mini_event.clear"), MiniEventTracker::clear);
	}

	@Override
	@Nullable
	protected Text header(boolean preview) {
		if (MiniEventTracker.isActive()) {
			return Text.literal(MiniEventTracker.type() + " Event");
		}
		if (MiniEventTracker.isUpcoming()) {
			return Text.translatable("fishbite.hud.mini_event.upcoming");
		}
		return Text.literal("Fishing Event");
	}

	@Override
	@Nullable
	protected ItemStack icon(boolean preview) {
		if (MiniEventTracker.isActive()) {
			return new ItemStack(iconForType(MiniEventTracker.type()));
		}
		if (MiniEventTracker.isUpcoming()) {
			return new ItemStack(Items.CLOCK);
		}
		return new ItemStack(Items.FISHING_ROD);
	}

	@Override
	protected String timeText(boolean preview) {
		if (MiniEventTracker.isActive()) {
			return TimeFormat.hms(MiniEventTracker.activeRemainingMs());
		}
		if (MiniEventTracker.isUpcoming()) {
			return TimeFormat.hms(MiniEventTracker.upcomingRemainingMs());
		}
		return "30:00";
	}

	private static net.minecraft.item.Item iconForType(String type) {
		String lower = type.toLowerCase(Locale.ROOT);
		if (lower.contains("cache")) {
			return Items.PLAYER_HEAD;
		}
		if (lower.contains("selling") || lower.contains("sell")) {
			return Items.LEATHER_HORSE_ARMOR;
		}
		if (lower.contains("fishing")) {
			return Items.FISHING_ROD;
		}
		if (lower.contains("pit") || lower.contains("hill") || lower.contains("koth")) {
			return Items.NETHERITE_SWORD;
		}
		return Items.CLOCK;
	}
}
