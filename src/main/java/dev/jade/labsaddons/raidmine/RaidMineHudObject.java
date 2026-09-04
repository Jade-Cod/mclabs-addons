package dev.jade.labsaddons.raidmine;

import dev.jade.labsaddons.hud.HudObjectSettings;
import dev.jade.labsaddons.hud.LabeledTimerHudObject;
import dev.jade.labsaddons.hud.TimeFormat;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/** Raid Mine widget: the double-drops countdown. */
public class RaidMineHudObject extends LabeledTimerHudObject {
	public static final String ID = "raid_mine";
	private static final int DEFAULT_TEXT_COLOR = 0xFFFFCC55;
	/** Shown in the editor when the buff isn't running — the shorter of the two rolls. */
	private static final String PREVIEW_TIME = "15.0";

	@Override
	public String id() {
		return ID;
	}

	@Override
	public HudObjectSettings defaultSettings() {
		HudObjectSettings defaults = new HudObjectSettings();
		defaults.x = 0.012f;
		defaults.y = 0.12f;
		defaults.textColor = DEFAULT_TEXT_COLOR;
		return defaults;
	}

	@Override
	public boolean shouldRender() {
		return RaidMineTracker.isActive();
	}

	@Override
	public EditorAction editorAction() {
		return new EditorAction(Text.translatable("labsaddons.hud.raid_mine.clear"), RaidMineTracker::clear);
	}

	@Override
	@Nullable
	protected Text header(boolean preview) {
		return Text.translatable("labsaddons.hud.raid_mine.double_drops");
	}

	@Override
	@Nullable
	protected ItemStack icon(boolean preview) {
		return new ItemStack(Items.DIAMOND_PICKAXE);
	}

	@Override
	protected String timeText(boolean preview) {
		// precise(): one decimal below a minute, so a 15s buff visibly ticks.
		return RaidMineTracker.isActive()
				? TimeFormat.precise(RaidMineTracker.remainingMs())
				: PREVIEW_TIME;
	}
}
