package dev.jade.fishbite.chum;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.item.ItemStack;

/** Recognises the server's Chum Bucket item by its custom_model_data string. */
public final class ChumDetector {
	private static final String CHUM_MODEL_ID = "chumbucket";
	/** Ignore repeat activations within this window (held-click de-dupe). */
	private static final long DEBOUNCE_MS = 300L;

	private static long lastActivationMs;

	private ChumDetector() {
	}

	public static boolean isChumBucket(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		CustomModelDataComponent modelData = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
		return modelData != null && modelData.strings().contains(CHUM_MODEL_ID);
	}

	/**
	 * Records a Chum Bucket activation, debounced so a single physical click
	 * cannot double-count. Returns {@code true} if it was counted.
	 */
	public static boolean tryActivate() {
		long now = System.currentTimeMillis();
		if (now - lastActivationMs < DEBOUNCE_MS) {
			return false;
		}
		lastActivationMs = now;
		ChumTimer.addChum();
		return true;
	}
}
