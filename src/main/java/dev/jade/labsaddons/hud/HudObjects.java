package dev.jade.labsaddons.hud;

import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Registry of all HUD objects. Register new widgets here and they
 * automatically gain dragging, snapping, resizing, background, and config
 * scaffolding. Live drawing happens in {@link HudRenderDispatcher} (via the
 * {@code InGameHudMixin} tail hook), not Fabric's HudElementRegistry, so the
 * widgets survive client overlays such as Feather.
 */
public final class HudObjects {
	/**
	 * The editor-rail groups, so a set of related widgets costs the rail one row rather
	 * than one each. See {@link HudObject#group()}.
	 *
	 * <p>Rates and rentals running down: the Chum Bucket, server and personal boosters,
	 * Lab Wars revenue, and the rental mount.
	 */
	public static final Text BOOSTS = Text.translatable("labsaddons.hud.group.boosts");
	/** What the server is running right now: mini-events, the Pit, Raid Mine, bounties. */
	public static final Text EVENTS = Text.translatable("labsaddons.hud.group.events");
	/**
	 * What is still owed today. Not "Dailies" — that is the name of one of the two widgets
	 * inside it, and a header repeating its own child reads as a mistake.
	 */
	public static final Text REMINDERS = Text.translatable("labsaddons.hud.group.reminders");
	/** The casino widgets. */
	public static final Text GAMBLING = Text.translatable("labsaddons.hud.group.gambling");

	private static final List<HudObject> OBJECTS = new ArrayList<>();

	private HudObjects() {
	}

	public static void register(HudObject object) {
		OBJECTS.add(object);
	}

	public static List<HudObject> all() {
		return Collections.unmodifiableList(OBJECTS);
	}
}
