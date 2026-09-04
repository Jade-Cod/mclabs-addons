package dev.jade.labsaddons.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the private, network-synced {@code CAUGHT_FISH} tracked data of the
 * fishing bobber. When this flag is {@code true} a fish is biting and the
 * bobber has been pulled under, which is exactly the window in which reeling in
 * lands a catch.
 */
@Mixin(FishingHook.class)
public interface FishingHookAccessor {
	@Accessor("DATA_BITING")
	static EntityDataAccessor<Boolean> getBitingTracker() {
		throw new AssertionError("Mixin accessor was not applied");
	}
}
