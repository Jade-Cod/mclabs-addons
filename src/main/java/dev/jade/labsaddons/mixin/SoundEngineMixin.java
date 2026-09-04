package dev.jade.labsaddons.mixin;

import dev.jade.labsaddons.BiteMarker;
import dev.jade.labsaddons.config.LabsAddonsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.RandomSource;
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
@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
	@Unique
	private static final String BOBBER_SOUND_PREFIX = "entity.fishing_bobber";
	@Unique
	private static final String SPLASH_SOUND_PATH = "entity.fishing_bobber.splash";
	/** A sound farther than this from every bobber is left untouched. */
	@Unique
	private static final double MAX_BOBBER_DISTANCE_SQ = 9.0;

	@Inject(
			method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;",
			at = @At("HEAD"),
			cancellable = true
	)
	private void labsaddons$interceptBobberSounds(SoundInstance sound,
			CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
		Identifier id = sound.getIdentifier();
		if (id == null || !id.getPath().startsWith(BOBBER_SOUND_PREFIX)) {
			return;
		}

		LabsAddonsConfig config = LabsAddonsConfig.get();
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return;
		}

		FishingHook bobber = labsaddons$nearestBobber(client,
				new Vec3(sound.getX(), sound.getY(), sound.getZ()));
		if (bobber == null) {
			return;
		}

		boolean isOwn = BiteMarker.isOwnBobber(bobber);
		if (!isOwn) {
			if (config.muteOtherBobbers) {
				cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
			}
			return;
		}

		if (id.getPath().equals(SPLASH_SOUND_PATH)) {
			labsaddons$replaceCatchSound(config, client, sound, id, cir);
		}
	}

	@Unique
	private static FishingHook labsaddons$nearestBobber(Minecraft client, Vec3 soundPos) {
		FishingHook nearest = null;
		double bestDistanceSq = MAX_BOBBER_DISTANCE_SQ;
		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity instanceof FishingHook candidate) {
				double distanceSq = candidate.position().distanceToSqr(soundPos);
				if (distanceSq < bestDistanceSq) {
					bestDistanceSq = distanceSq;
					nearest = candidate;
				}
			}
		}
		return nearest;
	}

	@Unique
	private static void labsaddons$replaceCatchSound(LabsAddonsConfig config, Minecraft client,
			SoundInstance sound, Identifier originalId,
			CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
		if (config.catchSound.isBlank()) {
			return;
		}

		Identifier replacementId = Identifier.tryParse(config.catchSound);
		if (replacementId == null || replacementId.equals(originalId)) {
			return;
		}

		if (!BuiltInRegistries.SOUND_EVENT.containsKey(replacementId)) {
			return;
		}
		SoundEvent replacement = BuiltInRegistries.SOUND_EVENT.getValue(replacementId);

		cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
		client.getSoundManager().play(new SimpleSoundInstance(
				replacement, sound.getSource(), 1.0f, 1.0f, RandomSource.create(),
				sound.getX(), sound.getY(), sound.getZ()));
	}
}
