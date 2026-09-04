package dev.jade.labsaddons.raidmine;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads "+N<code>" gains out of a Raid Mine resource hologram, working on text
 * already flattened out of the component tree so the parsing stays testable
 * without a running client.
 *
 * <p>A hologram is one or more lines, each naming an amount and the resource it
 * belongs to — {@code +3ℯ} on one line, {@code +2𝕊} on the next. The resource
 * code is a single character the server colours, and the colour is what tells
 * apart the tiers that share a letter (𝕤 from 𝕊), so it is carried along.
 */
public final class RaidMineGains {
	/** One run of same-coloured text from the hologram. */
	public record Segment(String text, int color) {
	}

	/** One resource gain: how much, of which code, in the colour the server drew it. */
	public record Gain(String code, double amount, int color) {
	}

	private static final Pattern AMOUNT = Pattern.compile("\\+(\\d+(?:\\.\\d+)?)");

	private RaidMineGains() {
	}

	/**
	 * Every gain in one hologram. Resource codes are matched as whole code points,
	 * not chars: most of them (𝕤, 𝕍, 💰) live outside the basic plane and are two
	 * java chars each, so reading a single char would split them in half.
	 */
	public static List<Gain> parse(List<Segment> segments) {
		String text = concat(segments);
		List<Gain> gains = new ArrayList<>();
		Matcher matcher = AMOUNT.matcher(text);
		while (matcher.find()) {
			int codeStart = skipSpaces(text, matcher.end());
			if (codeStart >= text.length()) {
				continue;
			}
			int codePoint = text.codePointAt(codeStart);
			String code = new String(Character.toChars(codePoint));
			if (isAmbiguous(codePoint)) {
				continue;
			}
			gains.add(new Gain(code, Double.parseDouble(matcher.group(1)), colorAt(segments, codeStart)));
		}
		return gains;
	}

	/**
	 * A digit or separator directly after an amount means we are not looking at a
	 * resource code at all — a bare number, or one amount running into the next.
	 */
	private static boolean isAmbiguous(int codePoint) {
		return Character.isDigit(codePoint) || codePoint == '+' || codePoint == '-';
	}

	private static int skipSpaces(String text, int from) {
		int index = from;
		while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
			index++;
		}
		return index;
	}

	private static String concat(List<Segment> segments) {
		StringBuilder builder = new StringBuilder();
		for (Segment segment : segments) {
			builder.append(segment.text());
		}
		return builder.toString();
	}

	/** The colour of whichever segment the given index falls in. */
	private static int colorAt(List<Segment> segments, int index) {
		int cursor = 0;
		for (Segment segment : segments) {
			int end = cursor + segment.text().length();
			if (index < end) {
				return segment.color();
			}
			cursor = end;
		}
		return 0xFFFFFF;
	}
}
