package dev.jade.fishbite.mixin;

import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.projectile.FishingBobberEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the private, network-synced {@code CAUGHT_FISH} tracked data of the
 * fishing bobber. When this flag is {@code true} a fish is biting and the
 * bobber has been pulled under, which is exactly the window in which reeling in
 * lands a catch.
 */
@Mixin(FishingBobberEntity.class)
public interface FishingBobberEntityAccessor {
	@Accessor("CAUGHT_FISH")
	static TrackedData<Boolean> getCaughtFishTracker() {
		throw new AssertionError("Mixin accessor was not applied");
	}
}
