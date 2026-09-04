package dev.jade.labsaddons.cooldown;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A provider of cooldown rows for {@link CooldownHudObject}. Register one per
 * feature (mcMMO abilities today; any future item/ability cooldown source can
 * plug in without touching the widget).
 */
public interface CooldownSource {

	/** Current rows, sorted for display (active first, then soonest-ready). */
	List<CooldownEntry> entries(long nowMs);

	/** Icon for an entry key this source produced, or null for no icon. */
	@Nullable
	ItemStack iconFor(String key);

	/** Drops all tracked state (editor "clear" action). */
	void clear();

	/** A key/label pair for the widget's per-entry visibility flyout. */
	record Toggleable(String key, String label) {
	}

	/** Heading for this source's group in the widget's visibility flyout, or null to omit it. */
	@Nullable
	default String categoryLabel() {
		return null;
	}

	/** Every key (and display label) this source can ever produce, for the visibility flyout. */
	default List<Toggleable> allKeys() {
		return List.of();
	}
}
