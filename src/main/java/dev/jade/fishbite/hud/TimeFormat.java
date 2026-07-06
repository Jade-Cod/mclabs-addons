package dev.jade.fishbite.hud;

import java.util.Locale;

/** Shared remaining-time formatting for HUD timers. */
public final class TimeFormat {
	private TimeFormat() {
	}

	/** Same as {@link #hms}, but below a minute shows one decimal of seconds (e.g. "5.3"). */
	public static String precise(long remainingMs) {
		long clamped = Math.max(0L, remainingMs);
		return clamped < 60_000L
				? String.format(Locale.ROOT, "%.1f", clamped / 1000.0)
				: hms(remainingMs);
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
		String mStr = minutes < 10 ? "0" + minutes : String.valueOf(minutes);
		String sStr = seconds < 10 ? "0" + seconds : String.valueOf(seconds);
		return hours > 0 ? hours + ":" + mStr + ":" + sStr : minutes + ":" + sStr;
	}
}
