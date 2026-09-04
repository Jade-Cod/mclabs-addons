package dev.jade.labsaddons.raidmine;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import dev.jade.labsaddons.mixin.TextDisplayInvoker;
import net.minecraft.world.entity.Display;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the Raid Mine's resource holograms off the world. Breaking a block drops
 * no items — the server spawns a short-lived text display saying what you
 * generated ("+3ℯ" over "+2𝕊"), and that popup is the only record of the gain.
 *
 * <p>The popups are private: a test with a second player confirmed you never see
 * anyone else's, and the log backs it up — popups spawning in the same second were
 * never more than 4.4 blocks apart, one player's reach. So every one we see is
 * ours and no attribution is needed.
 *
 * <p>A display spawns with <b>empty</b> text and is filled by a later metadata
 * packet, so reading at spawn finds nothing. Each new display is instead read a
 * few ticks later, once, keyed by entity id.
 */
public final class RaidMineHologramReader {
	/** Ticks to wait after a display appears before reading it, for its text to arrive. */
	private static final int READ_DELAY_TICKS = 3;

	// entity id -> tick it should be read on. One read each, then forgotten.
	private static final Map<Integer, Integer> pending = new HashMap<>();
	private static int tickCounter;

	private RaidMineHologramReader() {
	}

	/** ClientEntityEvents.ENTITY_LOAD: queue any new text display for a delayed read. */
	public static void onEntityLoad(Entity entity, ClientLevel world) {
		if (entity instanceof Display.TextDisplay) {
			pending.put(entity.getId(), tickCounter + READ_DELAY_TICKS);
		}
	}

	public static void tick(Minecraft client) {
		tickCounter++;
		if (pending.isEmpty() || client.level == null) {
			return;
		}
		List<RaidMineGains.Gain> gains = new ArrayList<>();
		pending.entrySet().removeIf(entry -> {
			if (tickCounter < entry.getValue()) {
				return false;
			}
			Entity entity = client.level.getEntity(entry.getKey());
			if (entity instanceof Display.TextDisplay display) {
				gains.addAll(RaidMineGains.parse(flatten(((TextDisplayInvoker) display).labsaddons$getText())));
			}
			return true;
		});
		RaidMineSession.record(gains);
	}

	/**
	 * Flattens a component tree into coloured runs. The amount and its resource code
	 * are separate siblings, so the text has to be walked rather than read whole, and
	 * the colour has to come along: it is what separates the tiers sharing a letter.
	 */
	static List<RaidMineGains.Segment> flatten(Component text) {
		List<RaidMineGains.Segment> segments = new ArrayList<>();
		collect(text, 0xFFFFFF, segments);
		return segments;
	}

	private static void collect(Component text, int inherited, List<RaidMineGains.Segment> out) {
		int color = colorOf(text.getStyle(), inherited);
		// Only literal content carries the digits and codes; the structural wrappers
		// contribute nothing but their colour, which children inherit.
		String direct = directLiteral(text);
		if (!direct.isEmpty()) {
			out.add(new RaidMineGains.Segment(direct, color));
		}
		for (Component sibling : text.getSiblings()) {
			collect(sibling, color, out);
		}
	}

	/** This component's own literal text, excluding its siblings. */
	private static String directLiteral(Component text) {
		StringBuilder builder = new StringBuilder();
		text.getContents().visit(literal -> {
			builder.append(literal);
			return java.util.Optional.empty();
		});
		return builder.toString();
	}

	private static int colorOf(Style style, int inherited) {
		TextColor color = style == null ? null : style.getColor();
		return color == null ? inherited : color.getValue();
	}

	/** Entity ids are per-world; drop anything queued when leaving a server. */
	public static void reset() {
		pending.clear();
	}
}
