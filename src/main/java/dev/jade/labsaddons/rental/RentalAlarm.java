package dev.jade.labsaddons.rental;

import dev.jade.labsaddons.config.LabsAddonsConfig;
import dev.jade.labsaddons.hud.TimeFormat;
import dev.jade.labsaddons.runner.RunnerAlarm;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Return reminder: a title and the runner alarm's two beeps once a rental is within the
 * configured minutes of its end. Once per rental; an extension that lifts it back over the
 * line rearms it.
 */
public final class RentalAlarm {
	public static final int MIN_MINUTES = 1;
	public static final int MAX_MINUTES = 60;
	public static final int DEFAULT_MINUTES = 10;

	private static final Set<String> alerted = new HashSet<>();

	private RentalAlarm() {
	}

	public static synchronized void check(long nowMs) {
		LabsAddonsConfig config = LabsAddonsConfig.get();
		long thresholdMs = config.rentalAlarmMinutes * 60_000L;
		for (RentalEntry entry : due(RentalTracker.entries(), nowMs, thresholdMs, config.rentalAlarmEnabled)) {
			long leftMs = entry.endMs - nowMs;
			RunnerAlarm.ring(
					Component.translatable("labsaddons.hud.rentals.alarm.title").withStyle(ChatFormatting.RED),
					Component.translatable("labsaddons.hud.rentals.alarm.subtitle", entry.name, TimeFormat.hms(leftMs)),
					config.rentalAlarmSound);
		}
	}

	/** Rentals that should ring now; marks them alerted and rearms any that are back over the threshold. */
	static synchronized List<RentalEntry> due(List<RentalEntry> entries, long nowMs, long thresholdMs, boolean enabled) {
		List<RentalEntry> ringing = new ArrayList<>();
		for (RentalEntry entry : entries) {
			long leftMs = entry.endMs - nowMs;
			if (leftMs > thresholdMs) {
				alerted.remove(entry.key());
			} else if (enabled && leftMs > 0 && alerted.add(entry.key())) {
				ringing.add(entry);
			}
		}
		return ringing;
	}
}
