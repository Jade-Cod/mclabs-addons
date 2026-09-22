package dev.jade.labsaddons.server;

import dev.jade.labsaddons.config.LabsAddonsConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The MCLabs worlds a HUD profile can be bound to, matched from the join banner the
 * server sends on arrival:
 *
 * <pre>
 * Welcome to MCLabs Spawn, Ophiliah!
 * Welcome to the MCLabs Overworld, Ophiliah!
 * Welcome to the MCLabs Underworld, Ophiliah!
 * Welcome to MCLabs Events, Ophiliah!
 * Welcome to The Pit, Ophiliah!
 * </pre>
 *
 * <p>A fixed table rather than whatever the mod happens to observe: these are the
 * worlds worth binding a layout to, the list is short and stable, and it means the
 * profile screen shows all five from a clean install instead of filling in as you
 * wander. The banners do not share one shape either — The Pit's omits "MCLabs"
 * entirely — so each world carries its own literal.
 *
 * <p>{@link McLabsSession} answers "are we on MCLabs" from the sidebar; this answers
 * "which world", which is what a profile keys off.
 */
public enum McLabsWorld {
	SPAWN("spawn", "Welcome to MCLabs Spawn"),
	OVERWORLD("overworld", "Welcome to the MCLabs Overworld"),
	UNDERWORLD("underworld", "Welcome to the MCLabs Underworld"),
	EVENTS("events", "Welcome to MCLabs Events"),
	PIT("pit", "Welcome to The Pit");

	private static final Logger LOGGER = LoggerFactory.getLogger("labsaddons");

	private static volatile McLabsWorld current;

	/** Stable key used in the config and as the lang-key suffix. */
	private final String id;
	/** The banner text up to (but not including) the ", <player>!" tail. */
	private final String banner;

	McLabsWorld(String id, String banner) {
		this.id = id;
		this.banner = banner;
	}

	public String id() {
		return id;
	}

	public Text displayName() {
		return Text.translatable("labsaddons.world." + id);
	}

	public static McLabsWorld byId(String id) {
		for (McLabsWorld world : values()) {
			if (world.id.equals(id)) {
				return world;
			}
		}
		return null;
	}

	/** The world we believe we are in, or null before any banner is seen. */
	public static McLabsWorld current() {
		return current;
	}

	/** Call on disconnect: the world we remember belongs to the server we just left. */
	public static void reset() {
		current = null;
	}

	public static void onMessage(String text) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) {
			return;
		}
		McLabsWorld world = from(text, client.player.getName().getString());
		if (world != null) {
			enter(world);
		}
	}

	/**
	 * Minecraft-free seam so the banner match is unit-testable.
	 *
	 * <p>Two guards, because a player can type anything into chat and real logs contain
	 * lines like {@code Monster: Welcome to MCLabs}. The banner must be followed by the
	 * local player's own name, and nothing lettered may precede it — a chat message
	 * always carries the sender's name and rank in front, whereas the server's line
	 * starts clean. Some chat mods prefix a timestamp and a {@code [!]} marker, which is
	 * why the check is "no letters" rather than "nothing at all".
	 *
	 * @return the world entered, or null when this is not our own join banner
	 */
	public static McLabsWorld from(String text, String localPlayerName) {
		if (text == null || localPlayerName == null || localPlayerName.isEmpty()) {
			return null;
		}
		String tail = ", " + localPlayerName + "!";
		for (McLabsWorld world : values()) {
			int at = text.indexOf(world.banner);
			if (at < 0 || !text.startsWith(tail, at + world.banner.length())) {
				continue;
			}
			if (hasLetterBefore(text, at)) {
				continue;
			}
			return world;
		}
		return null;
	}

	/** Letters ahead of the banner mean a chat sender's name or rank tag. */
	private static boolean hasLetterBefore(String text, int index) {
		return text.substring(0, index).chars().anyMatch(Character::isLetter);
	}

	/** Records the world and swaps to whichever profile is bound to it. */
	private static void enter(McLabsWorld world) {
		if (world == current) {
			return;
		}
		current = world;
		LOGGER.info("[labsaddons] Entered MCLabs world: {}", world.id);

		String bound = LabsAddonsConfig.get().worldProfiles.get(world.id);
		if (bound != null) {
			LabsAddonsConfig.activateProfile(bound);
		}
	}
}
