package dev.jade.labsaddons.mount;

import dev.jade.labsaddons.config.LabsAddonsConfig;
import dev.jade.labsaddons.hud.Durations;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Rental mount access timer: a "/buy" 60-minute purchase or a redeemed coupon, stacking additively. */
public final class RentalMountTimer {
	private static final long PURCHASE_MS = 60L * 60_000L;
	private static final String COUPON_MODEL = "mount-rental";
	private static final Pattern PURCHASE =
			Pattern.compile("purchased temporary access to the rental mount", Pattern.CASE_INSENSITIVE);
	private static final Pattern DURATION = Pattern.compile("Duration:\\s*(.+)", Pattern.CASE_INSENSITIVE);

	private RentalMountTimer() {
	}

	/** Chat: the instant 60-minute purchase. */
	public static void onMessage(String text) {
		if (PURCHASE.matcher(text).find()) {
			addDuration(PURCHASE_MS);
		}
	}

	/** Right-click on a Mount Rental Coupon: reads its "Duration:" lore. @return true if it was a coupon. */
	public static boolean tryCoupon(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		CustomModelData model = stack.get(DataComponents.CUSTOM_MODEL_DATA);
		if (model == null || !model.strings().contains(COUPON_MODEL)) {
			return false;
		}
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore != null) {
			for (Component line : lore.lines()) {
				Matcher m = DURATION.matcher(line.getString());
				if (m.find()) {
					long ms = Durations.parseMs(m.group(1));
					if (ms > 0) {
						addDuration(ms);
					}
					break;
				}
			}
		}
		return true;
	}

	public static void addDuration(long durationMs) {
		LabsAddonsConfig config = LabsAddonsConfig.get();
		long base = Math.max(System.currentTimeMillis(), config.rentalMountExpiryEpochMs);
		config.rentalMountExpiryEpochMs = base + durationMs;
		config.save();
	}

	public static long remainingMs() {
		return Math.max(0L, LabsAddonsConfig.get().rentalMountExpiryEpochMs - System.currentTimeMillis());
	}

	public static boolean isActive() {
		return remainingMs() > 0L;
	}

	public static void clear() {
		LabsAddonsConfig.get().rentalMountExpiryEpochMs = 0L;
		LabsAddonsConfig.get().save();
	}
}
