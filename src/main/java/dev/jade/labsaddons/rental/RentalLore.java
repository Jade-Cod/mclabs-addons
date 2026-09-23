package dev.jade.labsaddons.rental;

import dev.jade.labsaddons.hud.Durations;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads {@code /rent} rentals out of item lore and chat. Minecraft-free, so the tests can
 * feed it the server's own lines.
 *
 * <p>The rented item carries its window as {@code 9/23/26, 1:01 AM - 9/23/26, 3:01 AM.} and
 * the {@code /rent return} menu carries {@code Expiry: 9/23/26, 4:01 AM (02h:49m)}. Neither
 * names a zone. A rental taken at 22:01 Pacific read 1:01 AM, so the server prints US Eastern.
 */
public final class RentalLore {
	// ponytail: fixed zone; if MCLabs ever moves hosts this is the one line to change.
	static final ZoneId SERVER_ZONE = ZoneId.of("America/New_York");

	private static final String STAMP = "\\d{1,2}/\\d{1,2}/\\d{2}, \\d{1,2}:\\d{2} [AP]M";
	private static final Pattern WINDOW = Pattern.compile(STAMP + "\\s*-\\s*(" + STAMP + ")");
	private static final Pattern EXPIRY = Pattern.compile("Expiry:\\s*(" + STAMP + ")");
	private static final Pattern OWNER = Pattern.compile("Rented from:\\s*(\\w+)");
	private static final Pattern RETURNED = Pattern.compile("You have returned the rented (.+) to (\\w+)\\.");
	private static final Pattern EXTENDED = Pattern.compile(
			"extend your rent of (.+) from (\\w+) for another .+?Your rental now ends in ([\\dwhms: ]+)");
	private static final DateTimeFormatter FORMAT = new DateTimeFormatterBuilder()
			.parseCaseInsensitive().appendPattern("M/d/yy, h:mm a").toFormatter(Locale.US);

	private RentalLore() {
	}

	public record Rental(String name, String owner, long endMs) {
	}

	public record Extension(String name, String owner, long remainingMs) {
	}

	public record Returned(String name, String owner) {
	}

	/** A rented item in your inventory: "Rented from:" plus its window line. */
	@Nullable
	public static Rental fromItem(String name, List<String> lore) {
		return read(name, lore, WINDOW);
	}

	/** A row of the {@code /rent return} menu: "Rented from:" plus its "Expiry:" line. */
	@Nullable
	public static Rental fromReturnMenu(String name, List<String> lore) {
		return read(name, lore, EXPIRY);
	}

	@Nullable
	public static Returned returned(String chat) {
		Matcher m = RETURNED.matcher(chat);
		return m.find() ? new Returned(m.group(1), m.group(2)) : null;
	}

	/** "Your rental now ends in 02h:50m" is the new time left, not the time added. */
	@Nullable
	public static Extension extended(String chat) {
		Matcher m = EXTENDED.matcher(chat);
		if (!m.find()) {
			return null;
		}
		long remaining = Durations.parseMs(m.group(3));
		return remaining > 0 ? new Extension(m.group(1), m.group(2), remaining) : null;
	}

	@Nullable
	private static Rental read(String name, List<String> lore, Pattern timeLine) {
		String owner = null;
		long endMs = 0L;
		for (String raw : lore) {
			String line = normalize(raw);
			Matcher o = OWNER.matcher(line);
			if (o.find()) {
				owner = o.group(1);
			}
			Matcher t = timeLine.matcher(line);
			if (t.find()) {
				endMs = toEpochMs(t.group(1));
			}
		}
		return owner != null && endMs > 0 && !name.isBlank() ? new Rental(name, owner, endMs) : null;
	}

	/** Java prints the AM/PM gap as a narrow no-break space, and the server does too in places. */
	private static String normalize(String line) {
		return line.replace(' ', ' ').replace(' ', ' ');
	}

	private static long toEpochMs(String stamp) {
		try {
			return LocalDateTime.parse(stamp, FORMAT).atZone(SERVER_ZONE).toInstant().toEpochMilli();
		} catch (DateTimeParseException e) {
			return 0L;
		}
	}
}
