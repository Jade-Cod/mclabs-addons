package dev.jade.labsaddons.server;

import java.util.regex.Pattern;

/**
 * Tells MCLabs player chat apart from the server's own lines. Player chat arrives as plain game
 * messages, the same channel the server uses, so without this anyone typing "Vote registered!"
 * would move the HUD of everyone who can see it.
 */
public final class ChatLines {
	/**
	 * "[S] [VIP] bob: hi", "Castlenite: hi", or an advert's "Advert by name:" header. Rank tags in
	 * brackets, then a name, then a colon: no server line the mod reads is shaped like that.
	 */
	// ponytail: a denylist of the chat shapes in the captures. Private messages, lab chat and /me
	// aren't in any capture yet, so a parser that writes saved data also anchors on its own prefix.
	private static final Pattern PLAYER = Pattern.compile("^(?:\\[[^\\]\\n]*]\\s*)*[A-Za-z0-9_.]{1,17}:\\s|^Advert by ");

	private ChatLines() {
	}

	public static boolean isPlayerChat(String text) {
		return PLAYER.matcher(text.strip()).find();
	}
}
