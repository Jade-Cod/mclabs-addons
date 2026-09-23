package dev.jade.labsaddons.rental;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Where rentals are read from items: your inventory, once a second, and the
 * {@code /rent return} menu, once per open (the same passive scrape as
 * {@link dev.jade.labsaddons.runner.SupplierJobsReader}).
 */
public final class RentalReader {
	private static final int SCAN_INTERVAL_TICKS = 20;

	private static int ticksUntilScan = 0;

	private RentalReader() {
	}

	/** Once a second: pick up rented items you are holding, then check the reminder. */
	public static void tick(@Nullable Inventory inventory) {
		if (inventory == null || --ticksUntilScan > 0) {
			return;
		}
		ticksUntilScan = SCAN_INTERVAL_TICKS;
		long nowMs = System.currentTimeMillis();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			RentalLore.Rental rental = read(stack, RentalLore::fromItem);
			if (rental != null) {
				RentalTracker.onHeld(itemId(stack), rental, nowMs);
			}
		}
		RentalAlarm.check(nowMs);
	}

	/**
	 * @return true if this was the {@code /rent return} menu with at least one rental on it.
	 * An empty one is indistinguishable from any other screen, so it changes nothing — a
	 * rental returned from the menu is cleared by its chat line instead.
	 */
	public static boolean tryReadReturnMenu(AbstractContainerScreen<?> screen) {
		List<RentalEntry> listed = new ArrayList<>();
		for (Slot slot : screen.getMenu().slots) {
			ItemStack stack = slot.getItem();
			RentalLore.Rental rental = read(stack, RentalLore::fromReturnMenu);
			if (rental != null) {
				listed.add(new RentalEntry(itemId(stack), rental.name(), rental.owner(), rental.endMs()));
			}
		}
		if (listed.isEmpty()) {
			return false;
		}
		RentalTracker.reconcile(listed);
		return true;
	}

	@Nullable
	private static RentalLore.Rental read(ItemStack stack,
			BiFunction<String, List<String>, RentalLore.Rental> parser) {
		if (stack.isEmpty()) {
			return null;
		}
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null || lore.lines().isEmpty()) {
			return null;
		}
		return parser.apply(stack.getHoverName().getString(), lore.lines().stream().map(Component::getString).toList());
	}

	private static String itemId(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}
}
