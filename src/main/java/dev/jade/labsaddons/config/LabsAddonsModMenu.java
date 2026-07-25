package dev.jade.labsaddons.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Registers the Cloth Config screen with Mod Menu. Only loaded when Mod Menu is
 * present, so the mod still runs standalone without it. Cloth Config is optional
 * too — without it Mod Menu simply shows no settings button, and the in-game HUD
 * editor remains the way to configure everything.
 */
public class LabsAddonsModMenu implements ModMenuApi {
	private static final String CLOTH_CONFIG_MOD_ID = "cloth-config";

	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		// Bail before the method reference: resolving it class-loads
		// LabsAddonsConfigScreenFactory, whose Cloth imports would then throw
		// NoClassDefFoundError when Cloth Config isn't installed.
		if (!FabricLoader.getInstance().isModLoaded(CLOTH_CONFIG_MOD_ID)) {
			return screen -> null;
		}
		return LabsAddonsConfigScreenFactory::create;
	}
}
