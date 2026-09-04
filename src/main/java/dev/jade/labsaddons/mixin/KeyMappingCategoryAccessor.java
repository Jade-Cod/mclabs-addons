package dev.jade.labsaddons.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Exposes the static registry list of keybind categories. Mod categories are
 * appended after the vanilla ones (order = index in this list), so we use this to
 * move the "McLab Addons" category near the top of the Controls screen.
 */
@Mixin(KeyMapping.Category.class)
public interface KeyMappingCategoryAccessor {
	@Accessor("SORT_ORDER")
	static List<KeyMapping.Category> getCategories() {
		throw new AssertionError("Mixin accessor was not applied");
	}
}
