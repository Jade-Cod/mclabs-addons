package dev.jade.fishbite.mount;

import dev.jade.fishbite.config.FishBiteConfig;
import dev.jade.fishbite.hud.Durations;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

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
		CustomModelDataComponent model = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
		if (model == null || !model.strings().contains(COUPON_MODEL)) {
			return false;
		}
		LoreComponent lore = stack.get(DataComponentTypes.LORE);
		if (lore != null) {
			for (Text line : lore.lines()) {
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
		FishBiteConfig config = FishBiteConfig.get();
		long base = Math.max(System.currentTimeMillis(), config.rentalMountExpiryEpochMs);
		config.rentalMountExpiryEpochMs = base + durationMs;
		config.save();
	}

	public static long remainingMs() {
		return Math.max(0L, FishBiteConfig.get().rentalMountExpiryEpochMs - System.currentTimeMillis());
	}

	public static boolean isActive() {
		return remainingMs() > 0L;
	}

	public static void clear() {
		FishBiteConfig.get().rentalMountExpiryEpochMs = 0L;
		FishBiteConfig.get().save();
	}
}
