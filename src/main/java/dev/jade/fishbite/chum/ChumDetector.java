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
	/** Hotbar slot of the last counted activation; -1 = none yet. */
	private static int lastSlot = -1;

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
	 * Records a Chum Bucket activation from the given hotbar slot. The debounce
	 * suppresses a single physical click double-firing on the same slot, but a
	 * different slot bypasses it — so holding right-click while scrolling between
	 * two buckets counts both. Returns {@code true} if it was counted.
	 */
	public static boolean tryActivate(int selectedSlot) {
		long now = System.currentTimeMillis();
		boolean sameSlot = selectedSlot == lastSlot;
		if (sameSlot && now - lastActivationMs < DEBOUNCE_MS) {
			return false;
		}
		lastActivationMs = now;
		lastSlot = selectedSlot;
		ChumTimer.addChum();
		return true;
	}
}
