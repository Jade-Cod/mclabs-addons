package dev.jade.labsaddons.carnage;

import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * When Halloween Carnage runs: October 1 to midnight Eastern on November 1. Outside it the
 * tracking stands down: the widget isn't registered, and no chat, actionbar or menu is read for
 * it. The {@code /carnage} screen stays, since the menus are still there to look at.
 */
public final class CarnageEvent {
	private static final ZoneId EASTERN = ZoneId.of("America/New_York");
	// ponytail: this year's dates. Next year's event needs new ones, or a release without Carnage.
	static final long START_MS = ZonedDateTime.of(2026, 10, 1, 0, 0, 0, 0, EASTERN).toInstant().toEpochMilli();
	static final long END_MS = ZonedDateTime.of(2026, 11, 1, 0, 0, 0, 0, EASTERN).toInstant().toEpochMilli();
	/** The tests replay captured Carnage lines, so they run it whatever the date. */
	private static final boolean ALWAYS = Boolean.getBoolean("labsaddons.carnage.always");

	private CarnageEvent() {
	}

	public static boolean isOn() {
		return ALWAYS || within(System.currentTimeMillis());
	}

	static boolean within(long nowMs) {
		return nowMs >= START_MS && nowMs < END_MS;
	}
}
