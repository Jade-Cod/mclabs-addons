package dev.jade.fishbite.mixin;

import dev.jade.fishbite.config.FishBiteConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts fishing-bobber sounds client-side. Optionally mutes sounds from
 * bobbers that don't belong to the local player, and replaces the catch splash
 * of the player's own bobber with a configurable sound.
 */
@Mixin(SoundSystem.class)
public abstract class SoundSystemMixin {
	@Unique
	private static final String BOBBER_SOUND_PREFIX = "entity.fishing_bobber";
	@Unique
	private static final String SPLASH_SOUND_PATH = "entity.fishing_bobber.splash";
	/** A sound farther than this from every bobber is left untouched. */
	@Unique
	private static final double MAX_BOBBER_DISTANCE_SQ = 9.0;

	@Inject(
			method = "play(Lnet/minecraft/client/sound/SoundInstance;)Lnet/minecraft/client/sound/SoundSystem$PlayResult;",
			at = @At("HEAD"),
			cancellable = true
	)
	private void fishbite$interceptBobberSounds(SoundInstance sound,
			CallbackInfoReturnable<SoundSystem.PlayResult> cir) {
		Identifier id = sound.getId();
		if (id == null || !id.getPath().startsWith(BOBBER_SOUND_PREFIX)) {
			return;
		}

		FishBiteConfig config = FishBiteConfig.get();
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.player == null) {
			return;
		}

		FishingBobberEntity bobber = fishbite$nearestBobber(client,
				new Vec3d(sound.getX(), sound.getY(), sound.getZ()));
		if (bobber == null) {
			return;
		}

		boolean isOwn = bobber.getPlayerOwner() == client.player;
		if (!isOwn) {
			if (config.muteOtherBobbers) {
				cir.setReturnValue(SoundSystem.PlayResult.NOT_STARTED);
			}
			return;
		}

		if (id.getPath().equals(SPLASH_SOUND_PATH)) {
			fishbite$replaceCatchSound(config, client, sound, id, cir);
		}
	}

	@Unique
	private static FishingBobberEntity fishbite$nearestBobber(MinecraftClient client, Vec3d soundPos) {
		FishingBobberEntity nearest = null;
		double bestDistanceSq = MAX_BOBBER_DISTANCE_SQ;
		for (Entity entity : client.world.getEntities()) {
			if (entity instanceof FishingBobberEntity candidate) {
				double distanceSq = candidate.getEntityPos().squaredDistanceTo(soundPos);
				if (distanceSq < bestDistanceSq) {
					bestDistanceSq = distanceSq;
					nearest = candidate;
				}
			}
		}
		return nearest;
	}

	@Unique
	private static void fishbite$replaceCatchSound(FishBiteConfig config, MinecraftClient client,
			SoundInstance sound, Identifier originalId,
			CallbackInfoReturnable<SoundSystem.PlayResult> cir) {
		if (config.catchSound.isBlank()) {
			return;
		}

		Identifier replacementId = Identifier.tryParse(config.catchSound);
		if (replacementId == null || replacementId.equals(originalId)) {
			return;
		}

		SoundEvent replacement = Registries.SOUND_EVENT.get(replacementId);
		if (replacement == null) {
			return;
		}

		cir.setReturnValue(SoundSystem.PlayResult.NOT_STARTED);
		client.getSoundManager().play(new PositionedSoundInstance(
				replacement, sound.getCategory(), 1.0f, 1.0f, Random.create(),
				sound.getX(), sound.getY(), sound.getZ()));
	}
}
