package dev.jade.labsaddons;

import dev.jade.labsaddons.config.LabsAddonsConfig;
import dev.jade.labsaddons.mixin.FishingHookAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.tags.FluidTags;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;

/**
 * Decides which marker (if any) is shown above a fishing bobber and where. The
 * marker is always visible above the local player's bobber while fishing:
 * config-coloured yellow while waiting, switching to the bite colour while the
 * synced {@code CAUGHT_FISH} flag is set. When the bobber is dragged under, the
 * label position stays pinned above the water surface.
 */
public final class BiteMarker {
	private static final double BASE_OFFSET = 0.4;
	private static final double SURFACE_CLEARANCE = 0.35;
	private static final int MAX_SURFACE_SCAN = 8;

	private BiteMarker() {
	}

	/** Ownership by UUID — robust across servers and respawns. */
	public static boolean isOwnBobber(FishingHook bobber) {
		Minecraft client = Minecraft.getInstance();
		Player owner = bobber.getPlayerOwner();
		return owner != null && client.player != null
				&& owner.getUUID().equals(client.player.getUUID());
	}

	/**
	 * @return the marker text for this bobber, or {@code null} when no marker
	 *         should be drawn (mod disabled, or not the local player's bobber).
	 */
	public static Component markerFor(FishingHook bobber) {
		LabsAddonsConfig config = LabsAddonsConfig.get();
		if (!config.enabled || !isOwnBobber(bobber)) {
			return null;
		}

		boolean caught = bobber.getEntityData()
				.get(FishingHookAccessor.getBitingTracker());
		int color = caught ? config.biteColor : config.waitingColor;
		return Component.literal("!").withStyle(ChatFormatting.BOLD).withColor(color);
	}

	/** Label offset, raised so the marker stays above a submerged bobber's water. */
	public static Vec3 labelPosFor(FishingHook bobber, float tickProgress) {
		Vec3 lerped = bobber.getPosition(tickProgress);
		double offsetY = BASE_OFFSET;

		Level world = bobber.level();
		BlockPos pos = BlockPos.containing(lerped);
		if (world.getFluidState(pos).is(FluidTags.WATER)) {
			int topY = pos.getY();
			BlockPos.MutableBlockPos mutablePos = pos.mutable();
			while (topY - pos.getY() < MAX_SURFACE_SCAN
					&& world.getFluidState(mutablePos.set(pos.getX(), topY + 1, pos.getZ())).is(FluidTags.WATER)) {
				topY++;
			}
			mutablePos.set(pos.getX(), topY, pos.getZ());
			double surfaceY = topY + world.getFluidState(mutablePos).getHeight(world, mutablePos);
			offsetY = Math.max(offsetY, surfaceY + SURFACE_CLEARANCE - lerped.y);
		}

		return new Vec3(0.0, offsetY, 0.0);
	}
}
