package dev.jade.labsaddons.server;

import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.Text;

import java.util.Locale;

/**
 * Tracks whether the client is currently on the MCLabs network, detected from the
 * persistent sidebar scoreboard ("MCLabs ..." header) rather than chat join banners.
 * Banners vary per-world (some omit "MCLabs" entirely, e.g. The Pit) and the network's
 * Lobby sends no join message at all, so no chat-based marker can cover every subserver.
 * The sidebar is shown network-wide, including in the Lobby, making it a single
 * reliable signal. Polled once per client tick; resets on connect/disconnect so a
 * stale "yes" can't leak into a different server (or singleplayer).
 */
public final class McLabsSession {
	private static final String SIDEBAR_MARKER = "mclabs";

	private static volatile boolean onMcLabs = false;
	// Identity-cached: the server only swaps in a new Text when the sidebar title
	// actually changes, so re-deriving the lowercase match on every tick (20/sec,
	// for as long as any sidebar objective is shown) is wasted work.
	private static Text lastSidebarTitle;
	private static boolean lastSidebarMatched;

	private McLabsSession() {
	}

	/**
	 * Re-evaluates the sidebar scoreboard for the current world.
	 * @return true the moment this call is the one that activates the session.
	 */
	public static boolean tick(MinecraftClient client) {
		boolean wasActive = onMcLabs;
		onMcLabs = isOnMcLabsNetwork(client);
		return onMcLabs && !wasActive;
	}

	private static boolean isOnMcLabsNetwork(MinecraftClient client) {
		if (client.world == null) {
			lastSidebarTitle = null;
			return false;
		}
		ScoreboardObjective sidebar = client.world.getScoreboard().getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
		if (sidebar == null) {
			lastSidebarTitle = null;
			return false;
		}
		Text title = sidebar.getDisplayName();
		if (title != lastSidebarTitle) {
			lastSidebarTitle = title;
			lastSidebarMatched = title.getString().toLowerCase(Locale.ROOT).contains(SIDEBAR_MARKER);
		}
		return lastSidebarMatched;
	}

	/** Call on every fresh connection so a stale session can't leak forward. */
	public static void reset() {
		onMcLabs = false;
		lastSidebarTitle = null;
	}

	public static boolean isActive() {
		return onMcLabs;
	}
}
