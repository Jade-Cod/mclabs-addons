package dev.jade.fishbite.bounty;

import dev.jade.fishbite.hud.HudObjectSettings;
import dev.jade.fishbite.hud.LabeledTimerHudObject;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/** Spawn Bounty Hunt: a chest icon with the bounty chemical and chests remaining. */
public class BountyHudObject extends LabeledTimerHudObject {
	public static final String ID = "bounty";
	private static final int DEFAULT_TEXT_COLOR = 0xFFFFC56B;
	private static final String PREVIEW = "Copaprinide · 6 left";

	private ItemStack chestIcon;

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
		return BountyTracker.isActive();
	}

	@Override
	public EditorAction editorAction() {
		return new EditorAction(Text.translatable("fishbite.hud.bounty.clear"), BountyTracker::clear);
	}

	@Override
	@Nullable
	protected Text header(boolean preview) {
		return Text.translatable("fishbite.hud.bounty.name");
	}

	@Override
	@Nullable
	protected ItemStack icon(boolean preview) {
		if (chestIcon == null) {
			chestIcon = new ItemStack(Items.CHEST);
		}
		return chestIcon;
	}

	@Override
	protected String timeText(boolean preview) {
		if (!BountyTracker.isActive()) {
			return preview ? PREVIEW : "";
		}
		return BountyTracker.chem() + " · " + BountyTracker.remaining() + " left";
	}
}
