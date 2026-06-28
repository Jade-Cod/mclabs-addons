package dev.jade.fishbite.mixin;

import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Exposes the static registry list of keybind categories. Mod categories are
 * appended after the vanilla ones (order = index in this list), so we use this to
 * move the "McLab Addons" category near the top of the Controls screen.
 */
@Mixin(KeyBinding.Category.class)
public interface KeyBindingCategoryAccessor {
	@Accessor("CATEGORIES")
	static List<KeyBinding.Category> getCategories() {
		throw new AssertionError("Mixin accessor was not applied");
	}
}
