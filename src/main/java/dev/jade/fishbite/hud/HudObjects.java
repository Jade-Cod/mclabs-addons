package dev.jade.fishbite.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Registry of all HUD objects. Register new widgets here and they
 * automatically gain dragging, snapping, resizing, background, and config
 * scaffolding.
 */
public final class HudObjects {
	private static final List<HudObject> OBJECTS = new ArrayList<>();

	private HudObjects() {
	}

	public static void register(HudObject object) {
		OBJECTS.add(object);
		HudElementRegistry.addLast(Identifier.of("fishbite", object.id()),
				(context, tickCounter) -> {
					if (!MinecraftClient.getInstance().options.hudHidden) {
						object.render(context, false);
					}
				});
	}

	public static List<HudObject> all() {
		return Collections.unmodifiableList(OBJECTS);
	}
}
