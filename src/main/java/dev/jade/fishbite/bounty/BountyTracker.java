package dev.jade.fishbite.bounty;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tracks the Spawn "Bounty Hunt" event from its {@code Bounty »} chat lines:
 * which chemical is hidden and how many chests remain. State is in-memory only —
 * a bounty has no expiry clock, so persisting it would risk a stale widget across
 * relogs; the server re-announces "Bounty Hunt active!" on Spawn entry, which
 * re-seeds us. The "N chests left" lines are authoritative counts, so a missed
 * message self-heals on the next one.
 */
public final class BountyTracker {
	/** "6 chests each with 27 stacks of Copaprinide have been hidden ..." (start),
	 *  and "5 chests each containing 27 stacks of X are hidden ..." (active on join). */
	private static final Pattern START = Pattern.compile(
			"(\\d+)\\s+chests each (?:with|containing)\\s+\\d+\\s+stacks of\\s+(.+?)\\s+(?:have been hidden|are hidden)",
			Pattern.CASE_INSENSITIVE);
	/** "There are 2 bounty chests left ..." / "There is one bounty chest left ...". */
	private static final Pattern LEFT = Pattern.compile(
			"There (?:are|is)\\s+(one|\\d+)\\s+bounty chests?\\s+left", Pattern.CASE_INSENSITIVE);
	private static final Pattern LAST = Pattern.compile(
			"has found the last bounty chest", Pattern.CASE_INSENSITIVE);
	private static final Pattern ENDED = Pattern.compile(
			"All bounty chests have been found", Pattern.CASE_INSENSITIVE);

	private static volatile boolean active;
	private static volatile String chem = "";
	private static volatile int remaining;

	private BountyTracker() {
	}

	public static synchronized void onMessage(String text) {
		Matcher start = START.matcher(text);
		if (start.find()) {
			remaining = parseInt(start.group(1));
			chem = start.group(2).trim();
			active = remaining > 0;
			return;
		}
		Matcher left = LEFT.matcher(text);
		if (left.find()) {
			remaining = parseCount(left.group(1));
			active = remaining > 0;
			return;
		}
		if (LAST.matcher(text).find()) {
			remaining = 0;
			active = false;
			return;
		}
		if (ENDED.matcher(text).find()) {
			clear();
		}
	}

	private static int parseCount(String value) {
		return value.equalsIgnoreCase("one") ? 1 : parseInt(value);
	}

	private static int parseInt(String value) {
		try {
			return Integer.parseInt(value.toLowerCase(Locale.ROOT));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	public static synchronized boolean isActive() {
		return active && remaining > 0;
	}

	public static synchronized String chem() {
		return chem;
	}

	public static synchronized int remaining() {
		return remaining;
	}

	public static synchronized void clear() {
		active = false;
		chem = "";
		remaining = 0;
	}
}
