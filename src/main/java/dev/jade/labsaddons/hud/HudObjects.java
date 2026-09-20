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
	 * The editor-rail group the casino widgets share, so the pair costs the rail one row
	 * instead of two. See {@link HudObject#group()}.
	 */
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
