package dev.jade.labsaddons.runner;

import dev.jade.labsaddons.config.LabsAddonsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;

/**
 * Low-jobs alert for the Runner Jobs HUD widget: once {@link RunnerTracker#postedJobs()}
 * drops to/below the configured threshold, shows a title/subtitle and plays a sound.
 * Edge-triggered so it fires once per dip rather than on every update while still low.
 */
public final class RunnerAlarm {
	public static final List<String> SOUND_IDS = List.of(
			"minecraft:block.note_block.bell",
			"minecraft:block.note_block.pling",
			"minecraft:block.anvil.land",
			"minecraft:entity.experience_orb.pickup",
			"minecraft:entity.wither.spawn");

	private static final Map<String, String> SOUND_NAMES = Map.of(
			"minecraft:block.note_block.bell", "Bell",
			"minecraft:block.note_block.pling", "Pling",
			"minecraft:block.anvil.land", "Anvil",
			"minecraft:entity.experience_orb.pickup", "XP Orb",
			"minecraft:entity.wither.spawn", "Wither");

	public static final String DEFAULT_SOUND = SOUND_IDS.get(0);

	/** Ticks between the two alarm beeps, so they're audibly distinct rather than
	 *  stacking into one louder sound when played in the same frame. */
	private static final int REPLAY_DELAY_TICKS = 5;

	private static boolean fired = false;
	// Second-beep schedule: -1 idle, else ticks remaining until the replay.
	private static int replayCountdown = -1;
	private static String replaySound = DEFAULT_SOUND;

	private RunnerAlarm() {
	}

	public static boolean isValidSound(String id) {
		return id != null && SOUND_IDS.contains(id);
	}

	/** Short display name for a sound id, for the HUD Studio picker. */
	public static Component soundLabel(String id) {
		return Component.literal(SOUND_NAMES.getOrDefault(id, id));
	}

	/** Re-evaluate the alarm against the current posted-jobs count; call after it changes. */
	public static synchronized void checkThreshold() {
		LabsAddonsConfig config = LabsAddonsConfig.get();
		if (!config.runnerAlarmEnabled) {
			fired = false;
			return;
		}
		if (RunnerTracker.postedJobs() <= config.runnerAlarmThreshold) {
			if (!fired) {
				fired = true;
				fire(config.runnerAlarmSound);
			}
		} else {
			fired = false;
		}
	}

	/** Rearm the alarm (called on session reset so a fresh session starts unarmed). */
	public static synchronized void reset() {
		fired = false;
		replayCountdown = -1;
	}

	/** Drive the delayed second beep. Call once per client tick. */
	public static synchronized void tick() {
		if (replayCountdown < 0) {
			return;
		}
		if (replayCountdown == 0) {
			playSound(replaySound);
			replayCountdown = -1;
			return;
		}
		replayCountdown--;
	}

	private static void fire(String soundId) {
		Minecraft client = Minecraft.getInstance();
		Hud hud = client.gui.hud;
		if (hud != null) {
			hud.resetTitleTimes();
			hud.setTitle(Component.translatable("labsaddons.hud.runner_jobs.alarm.title")
					.withStyle(ChatFormatting.RED));
			hud.setSubtitle(Component.translatable(
					"labsaddons.hud.runner_jobs.alarm.subtitle", RunnerTracker.postedJobs()));
		}

		// First beep now; schedule the second so the two are heard as a distinct pair.
		playSound(soundId);
		replaySound = soundId;
		replayCountdown = REPLAY_DELAY_TICKS;
	}

	private static void playSound(String soundId) {
		Identifier soundEventId = Identifier.tryParse(isValidSound(soundId) ? soundId : DEFAULT_SOUND);
		if (soundEventId == null || !BuiltInRegistries.SOUND_EVENT.containsKey(soundEventId)) {
			return;
		}
		SoundEvent event = BuiltInRegistries.SOUND_EVENT.getValue(soundEventId);
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, 1.0f));
	}
}
