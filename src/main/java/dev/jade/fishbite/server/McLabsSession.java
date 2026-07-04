package dev.jade.fishbite.server;

import java.util.Locale;

/**
 * Tracks whether the client is currently on the MCLabs server, detected from its
 * join banner ("Welcome to MCLabs ...") as it flows through the chat pipeline.
 * Resets on every fresh connection so leaving MCLabs (or never having joined it,
 * e.g. singleplayer) fails closed.
 */
public final class McLabsSession {
	private static final String JOIN_BANNER_MARKER = "welcome to mclabs";

	private static volatile boolean onMcLabs = false;

	private McLabsSession() {
	}

	/** @return true the moment this call is the one that activates the session. */
	public static boolean onMessage(String plainText) {
		if (!onMcLabs && plainText.toLowerCase(Locale.ROOT).contains(JOIN_BANNER_MARKER)) {
			onMcLabs = true;
			return true;
		}
		return false;
	}

	/** Call on every fresh connection so a stale session can't leak forward. */
	public static void reset() {
		onMcLabs = false;
	}

	public static boolean isActive() {
		return onMcLabs;
	}
}
