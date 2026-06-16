package dev.jade.fishbite.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Registers the Cloth Config screen with Mod Menu. Only loaded when Mod Menu is
 * present, so the mod still runs standalone without it.
 */
public class FishBiteModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return FishBiteConfigScreenFactory::create;
	}
}
