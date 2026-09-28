package dev.jade.labsaddons.gametest;

import dev.jade.labsaddons.BiteMarker;
import dev.jade.labsaddons.config.LabsAddonsConfig;
import dev.jade.labsaddons.mixin.FishingHookAccessor;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.projectile.FishingHook;

import static dev.jade.labsaddons.gametest.HudEditorGameTest.check;

/**
 * A real cast into real water, waiting for a real bite: the marker has to be up while the
 * bobber waits, and change colour on the tick the fish bites.
 */
public class BiteMarkerGameTest implements FabricClientGameTest {
	/** Lure III takes the wait to at most ~19 s; this is well past that. */
	private static final int BITE_TIMEOUT_TICKS = 2400;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = TestWorld.open(context)) {
			// A pool three deep in front of the player, and a rod that bites fast.
			world.getServer().runCommand("execute as @p at @s run fill ~3 ~-4 ~-3 ~9 ~-1 ~3 minecraft:water");
			world.getServer().runCommand("give @p minecraft:fishing_rod[enchantments={\"minecraft:lure\":3}]");
			world.getConnection().waitForChunksRender();
			BlockPos pool = context.computeOnClient(c -> c.player.blockPosition().offset(6, -1, 0));
			context.getInput().lookAt(pool);
			context.waitTicks(5);

			context.getInput().pressKey(options -> options.keyUse);
			context.waitFor(c -> bobber(c) != null && bobber(c).isInWater(), 200);
			context.waitTicks(10);

			Component waiting = context.computeOnClient(c -> BiteMarker.markerFor(bobber(c)));
			check(waiting != null, "a marker shows over our own bobber while it waits");
			check(colour(waiting) == rgb(LabsAddonsConfig.get().waitingColor), "waiting marker uses the waiting colour");
			context.takeScreenshot("bobber-01-waiting");

			context.waitFor(c -> bobber(c) != null && biting(bobber(c)), BITE_TIMEOUT_TICKS);
			Component bitten = context.computeOnClient(c -> BiteMarker.markerFor(bobber(c)));
			check(bitten != null && colour(bitten) == rgb(LabsAddonsConfig.get().biteColor),
					"the marker switches to the bite colour on a bite");
			context.takeScreenshot("bobber-02-bite");

			// Reel in: the marker goes with the bobber.
			context.getInput().pressKey(options -> options.keyUse);
			context.waitFor(c -> bobber(c) == null, 100);
		}
	}

	private static FishingHook bobber(Minecraft client) {
		return client.player == null ? null : client.player.fishing;
	}

	private static boolean biting(FishingHook hook) {
		return hook.getEntityData().get(FishingHookAccessor.getBitingTracker());
	}

	private static int colour(Component marker) {
		TextColor color = marker.getStyle().getColor();
		return color == null ? -1 : color.getValue();
	}

	/** Text colours carry no alpha; the config's may. */
	private static int rgb(int argb) {
		return argb & 0xFFFFFF;
	}
}
