package dev.jade.fishbite;

import dev.jade.fishbite.config.FishBiteConfig;
import dev.jade.fishbite.mixin.FishingBobberEntityAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

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
	public static boolean isOwnBobber(FishingBobberEntity bobber) {
		MinecraftClient client = MinecraftClient.getInstance();
		PlayerEntity owner = bobber.getPlayerOwner();
		return owner != null && client.player != null
				&& owner.getUuid().equals(client.player.getUuid());
	}

	/**
	 * @return the marker text for this bobber, or {@code null} when no marker
	 *         should be drawn (mod disabled, or not the local player's bobber).
	 */
	public static Text markerFor(FishingBobberEntity bobber) {
		FishBiteConfig config = FishBiteConfig.get();
		if (!config.enabled || !isOwnBobber(bobber)) {
			return null;
		}

		boolean caught = bobber.getDataTracker()
				.get(FishingBobberEntityAccessor.getCaughtFishTracker());
		int color = caught ? config.biteColor : config.waitingColor;
		return Text.literal("!").formatted(Formatting.BOLD).withColor(color);
	}

	/** Label offset, raised so the marker stays above a submerged bobber's water. */
	public static Vec3d labelPosFor(FishingBobberEntity bobber, float tickProgress) {
		Vec3d lerped = bobber.getLerpedPos(tickProgress);
		double offsetY = BASE_OFFSET;

		World world = bobber.getEntityWorld();
		BlockPos pos = BlockPos.ofFloored(lerped);
		if (world.getFluidState(pos).isIn(FluidTags.WATER)) {
			int topY = pos.getY();
			while (topY - pos.getY() < MAX_SURFACE_SCAN
					&& world.getFluidState(new BlockPos(pos.getX(), topY + 1, pos.getZ())).isIn(FluidTags.WATER)) {
				topY++;
			}
			BlockPos top = new BlockPos(pos.getX(), topY, pos.getZ());
			double surfaceY = topY + world.getFluidState(top).getHeight(world, top);
			offsetY = Math.max(offsetY, surfaceY + SURFACE_CLEARANCE - lerped.y);
		}

		return new Vec3d(0.0, offsetY, 0.0);
	}
}
