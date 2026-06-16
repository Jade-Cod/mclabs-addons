package dev.jade.fishbite.hud;

/** Shared remaining-time formatting for HUD timers. */
public final class TimeFormat {
	private TimeFormat() {
	}

	/** {@code Xd Yh} from a day up, {@code H:MM:SS} from an hour up, otherwise {@code M:SS}. */
	public static String hms(long remainingMs) {
		long totalSeconds = (Math.max(0L, remainingMs) + 999L) / 1000L;
		long days = totalSeconds / 86400L;
		long hours = (totalSeconds % 86400L) / 3600L;
		long minutes = (totalSeconds % 3600L) / 60L;
		long seconds = totalSeconds % 60L;
		if (days > 0) {
			return days + "d " + hours + "h";
		}
		return hours > 0
				? String.format("%d:%02d:%02d", hours, minutes, seconds)
				: String.format("%d:%02d", minutes, seconds);
	}
}
