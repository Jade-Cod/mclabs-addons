package dev.jade.labsaddons.rental;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
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
	public static void tick(@Nullable PlayerInventory inventory) {
		if (inventory == null || --ticksUntilScan > 0) {
			return;
		}
		ticksUntilScan = SCAN_INTERVAL_TICKS;
		long nowMs = System.currentTimeMillis();
		for (int i = 0; i < inventory.size(); i++) {
			ItemStack stack = inventory.getStack(i);
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
	public static boolean tryReadReturnMenu(HandledScreen<?> screen) {
		List<RentalEntry> listed = new ArrayList<>();
		for (Slot slot : screen.getScreenHandler().slots) {
			ItemStack stack = slot.getStack();
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
		LoreComponent lore = stack.get(DataComponentTypes.LORE);
		if (lore == null || lore.lines().isEmpty()) {
			return null;
		}
		return parser.apply(stack.getName().getString(), lore.lines().stream().map(Text::getString).toList());
	}

	private static String itemId(ItemStack stack) {
		return Registries.ITEM.getId(stack.getItem()).toString();
	}
}
