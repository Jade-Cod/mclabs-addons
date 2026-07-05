package dev.jade.fishbite.mcmmo;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.item.ItemStack;

/**
 * Recognises the server's Smelling Salts item by its custom_model_data string
 * (analogous to {@link dev.jade.fishbite.chum.ChumDetector}). Using it resets
 * every mcMMO ability cooldown; the server also broadcasts "ABILITIES
 * REFRESHED!" (handled by {@link McmmoCooldownTracker}), but clearing on use
 * as well means the HUD updates the instant the item is right-clicked.
 */
public final class SmellingSaltsDetector {
	private static final String MODEL_ID = "smellingsalts";

	private SmellingSaltsDetector() {
	}

	public static boolean isSmellingSalts(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		CustomModelDataComponent modelData = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
		return modelData != null && modelData.strings().contains(MODEL_ID);
	}
}
