package dev.jade.fishbite.daily;

import dev.jade.fishbite.hud.HudObjectSettings;
import dev.jade.fishbite.hud.LabeledTimerHudObject;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/** Reminder of how many of the 7 daily votes are done; resets at 9 PM Pacific. */
public class VoteReminderHudObject extends LabeledTimerHudObject {
	public static final String ID = "votes";
	private static final int DEFAULT_TEXT_COLOR = 0xFFFFF066;
	private static final int PREVIEW_DONE = 3;

	private ItemStack voteIcon;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public HudObjectSettings defaultSettings() {
		HudObjectSettings defaults = new HudObjectSettings();
		defaults.x = 0.985f;
		defaults.y = 0.87f;
		defaults.textColor = DEFAULT_TEXT_COLOR;
		return defaults;
	}

	@Override
	public boolean shouldRender() {
		return VoteTracker.pending();
	}

	@Override
	@Nullable
	protected Text header(boolean preview) {
		return Text.translatable("fishbite.hud.votes.name");
	}

	@Override
	@Nullable
	protected ItemStack icon(boolean preview) {
		if (voteIcon == null) {
			voteIcon = new ItemStack(Items.SUNFLOWER);
		}
		return voteIcon;
	}

	@Override
	protected String timeText(boolean preview) {
		int done = preview ? PREVIEW_DONE : VoteTracker.votesDone();
		return done + "/" + VoteTracker.VOTE_GOAL;
	}

	// Cross-machine sync is intentionally out of scope; this is local-only.
	@Override
	public EditorAction editorAction() {
		return new EditorAction(Text.translatable("fishbite.hud.votes.mark_voted"), VoteTracker::markAllDone);
	}
}
