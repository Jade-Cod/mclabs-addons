package dev.jade.labsaddons.server;

import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * The one place the sidebar scoreboard is read, and everything the mod asks of it.
 *
 * <p>MCLabs keeps a sidebar up network-wide, including in the Lobby, and its title names
 * both the network and the subserver you are on: {@code MCLabs Spawn},
 * {@code MCLabs Underworld-A}, {@code MCLabs Raid}. So two questions the mod asks
 * constantly have the same answer sitting in the same string, and used to be asked
 * separately — this class read it once a tick to decide whether the HUD may draw at all,
 * and the Raid Mine widget read the very same objective again on every frame to decide
 * whether it was in a raid. Same object, two different clocks, two different caches, and
 * each logging its own account of what it found.
 *
 * <p>The title is now read once per client tick and both answers derived from it, so there
 * is one read, one cache and one log line. Neither answer changed: the match words are the
 * ones that were there before, and each has been checked against every sidebar title the
 * server is known to show — see {@code McLabsSessionTest}.
 *
 * <p>Detected from the sidebar rather than from chat join banners, because the banners vary
 * per world (The Pit's omits "MCLabs" entirely) and the Lobby sends no join message at all,
 * so no chat marker could cover every subserver. {@link McLabsWorld} answers the separate
 * question of <em>which</em> world, which a profile keys off, and stays on the banner: the
 * Underworld alone appears in the sidebar as {@code Underworld-A}, {@code Underworld-B},
 * {@code UW-A} and {@code UW-B}, and the banner names it once.
 *
 * <p>Resets on connect and disconnect so a stale "yes" cannot leak into a different server
 * (or singleplayer).
 */
public final class McLabsSession {
	private static final String SIDEBAR_MARKER = "mclabs";
	private static final String RAID_MARKER = "raid";

	private static final Logger LOGGER = LoggerFactory.getLogger("labsaddons");

	private static volatile boolean onMcLabs;
	private static volatile boolean inRaid;
	/**
	 * Identity-cached: the server only swaps in a new {@link Text} when the title actually
	 * changes, so re-deriving the matches every tick (20/sec, for as long as any sidebar is
	 * shown) is wasted work.
	 */
	private static Text lastSidebarTitle;
	/** Last title reported, so the sidebar is logged when it changes and not every tick. */
	/** The last pair of answers logged, so a live sidebar heading cannot flood the log. */
	private static String lastLoggedAnswers;

	private McLabsSession() {
	}

	/**
	 * Re-reads the sidebar for the current world.
	 *
	 * @return true the moment this call is the one that activates the session.
	 */
	public static boolean tick(MinecraftClient client) {
		boolean wasActive = onMcLabs;
		read(client);
		return onMcLabs && !wasActive;
	}

	private static void read(MinecraftClient client) {
		Text title = sidebarTitle(client);
		if (title == lastSidebarTitle) {
			return;
		}
		lastSidebarTitle = title;
		String text = title == null ? "" : title.getString();
		onMcLabs = matchesMcLabs(text);
		inRaid = matchesRaid(text);
		log(text);
	}

	private static Text sidebarTitle(MinecraftClient client) {
		if (client.world == null) {
			return null;
		}
		ScoreboardObjective sidebar = client.world.getScoreboard()
				.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
		return sidebar == null ? null : sidebar.getDisplayName();
	}

	/**
	 * One line per title, naming what the sidebar said and what was made of it.
	 *
	 * <p>Both words are matched rather than compared against a list, so a subserver the mod
	 * has never seen still works — but a sidebar MCLabs renames would fail silently, and this
	 * is what makes that readable from a log instead of another round of testing. A menu that
	 * hides the sidebar is not worth a line.
	 */
	/**
	 * One line per change of answer, not per change of title.
	 *
	 * <p>This is read every tick, and it keyed off the title text — so a sidebar with anything
	 * live in its heading (a countdown, a player count, a cycling banner) is a different string
	 * every tick, and that was a log line every tick: twenty a second of synchronous file and
	 * console I/O on the client thread, for a string whose only job is to answer two booleans.
	 * Precautionary rather than observed — MCLabs' headings look static — but the line is about
	 * the answers and nothing is lost by saying so only when one of them moves.
	 */
	private static void log(String title) {
		if (title.isEmpty()) {
			return;
		}
		String answers = onMcLabs + "/" + inRaid;
		if (answers.equals(lastLoggedAnswers)) {
			return;
		}
		lastLoggedAnswers = answers;
		LOGGER.info("[labsaddons] Sidebar: \"{}\" (mclabs={} raid={})", title, onMcLabs, inRaid);
	}

	/** Minecraft-free seam so the title match is testable. */
	static boolean matchesMcLabs(String title) {
		return contains(title, SIDEBAR_MARKER);
	}

	/** Minecraft-free seam so the title match is testable. */
	static boolean matchesRaid(String title) {
		return contains(title, RAID_MARKER);
	}

	private static boolean contains(String title, String marker) {
		return title != null && title.toLowerCase(Locale.ROOT).contains(marker);
	}

	/** Call on every fresh connection so a stale session cannot leak forward. */
	public static void reset() {
		onMcLabs = false;
		inRaid = false;
		lastSidebarTitle = null;
		lastLoggedAnswers = null;
	}

	public static boolean isActive() {
		return onMcLabs;
	}

	/**
	 * Whether the sidebar says we are in a raid.
	 *
	 * <p>The mine has no chat announcement to key off and no fixed coordinates, but the
	 * server swaps the sidebar to {@code MCLabs Raid} while you are in one.
	 */
	public static boolean isInRaid() {
		return inRaid;
	}
}
