package dev.jade.labsaddons.raidmine;

import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * Whether the player is in a raid, read off the sidebar scoreboard's title. The
 * mine has no chat announcement to key off and no fixed coordinates, but the
 * server swaps the sidebar to a raid board while you are in one — which is both
 * cheaper and more reliable than guessing from position.
 */
public final class RaidMineScoreboard {
	private static final String RAID = "raid";

	private static final Logger LOGGER = LoggerFactory.getLogger("labsaddons");
	/** Last title logged, so the sidebar is reported when it changes and not every tick. */
	private static String lastLoggedTitle;

	private RaidMineScoreboard() {
	}

	public static boolean isInRaid() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return false;
		}
		Objective sidebar =
				client.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
		String title = sidebar == null ? "" : sidebar.getDisplayName().getString();
		logTitleChange(title);
		return matchesRaid(title);
	}

	/**
	 * The word we match on is a guess until seen against the real server. Logging
	 * each new sidebar title means a wrong guess can be corrected from a log rather
	 * than another round of testing.
	 */
	private static void logTitleChange(String title) {
		if (!title.equals(lastLoggedTitle)) {
			lastLoggedTitle = title;
			LOGGER.info("[labsaddons] Sidebar objective is now: \"{}\" (raid match: {})",
					title, matchesRaid(title));
		}
	}

	/** Minecraft-free seam so the title match is testable. */
	static boolean matchesRaid(String title) {
		return title != null && title.toLowerCase(Locale.ROOT).contains(RAID);
	}
}
