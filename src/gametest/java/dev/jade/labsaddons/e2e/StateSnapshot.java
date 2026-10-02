package dev.jade.labsaddons.e2e;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a scenario did to the mod's saved state, in a form that compares equal across
 * runs and across Minecraft versions.
 *
 * <p>Minecraft-free on purpose: this file is identical on every branch, so a golden
 * made on 1.21.11 is the same bar 26.2 and 26.3 have to clear.
 */
final class StateSnapshot {
	/** The persisted files a scenario can move. Settings and HUD layout are not state. */
	private static final String[] FILES = {"state.json", "runners.json"};
	/** Epoch-ms in this window is a timestamp, whatever its key is called. */
	private static final long EPOCH_MIN = 1_600_000_000_000L;
	private static final long EPOCH_MAX = 2_100_000_000_000L;
	/** A scenario takes a few seconds to play; timestamps are compared to the nearest ten. */
	private static final long ROUND_MS = 10_000L;
	/**
	 * Further back than this, a timestamp came from a calendar rather than from the replay: a
	 * rental's end date in a dump, the daily vote reset. Its distance from now is the wall
	 * clock, not the mod, so it only has to be in the past.
	 */
	private static final long CALENDAR_PAST_MS = 10L * 60_000L;

	private StateSnapshot() {
	}

	static JsonObject read(Path configDir) throws IOException {
		JsonObject out = new JsonObject();
		for (String name : FILES) {
			Path file = configDir.resolve(name);
			if (Files.exists(file)) {
				out.add(name, JsonParser.parseString(Files.readString(file)));
			}
		}
		return out;
	}

	/** Every top-level key that changed, as "file/key" to its normalised new value. */
	static JsonObject diff(JsonObject before, JsonObject after, long nowMs) {
		JsonObject changed = new JsonObject();
		for (Map.Entry<String, JsonElement> file : after.entrySet()) {
			JsonObject was = before.has(file.getKey()) ? before.getAsJsonObject(file.getKey()) : new JsonObject();
			for (Map.Entry<String, JsonElement> entry : file.getValue().getAsJsonObject().entrySet()) {
				if (!entry.getValue().equals(was.get(entry.getKey()))) {
					changed.add(file.getKey() + "/" + entry.getKey(), normalise(entry.getKey(), entry.getValue(), nowMs));
				}
			}
		}
		return changed;
	}

	/**
	 * Timestamps become "now+37m30s"-style offsets, and a "...Ms" duration is rounded to the
	 * second, since it measured how long the replay took between two lines. Everything else
	 * is left alone.
	 */
	static JsonElement normalise(String key, JsonElement value, long nowMs) {
		if (value.isJsonObject()) {
			JsonObject out = new JsonObject();
			value.getAsJsonObject().entrySet().forEach(e -> out.add(e.getKey(), normalise(e.getKey(), e.getValue(), nowMs)));
			return out;
		}
		if (value.isJsonArray()) {
			JsonArray out = new JsonArray();
			value.getAsJsonArray().forEach(e -> out.add(normalise(key, e, nowMs)));
			return out;
		}
		if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
			double number = value.getAsDouble();
			if (number >= EPOCH_MIN && number <= EPOCH_MAX && number == Math.rint(number)) {
				long delta = (long) number - nowMs;
				return new JsonPrimitive(delta < -CALENDAR_PAST_MS ? "past" : relative(delta));
			}
			if (key.endsWith("Ms") && number >= 0 && number < EPOCH_MIN) {
				return new JsonPrimitive(Math.round(number / 1000.0) + "s");
			}
		}
		return value;
	}

	/**
	 * How far a "now+37m30s" offset may drift from its expected value and still match.
	 *
	 * <p>Offsets are measured to the end of the scenario on the wall clock, so a game that
	 * stalls between the line that set a timestamp and that end shifts it: three clients at
	 * once put the integrated server 15 s behind and a 10 s claim read as 30 s. Exact text
	 * equality failed those runs for nothing. Thirty seconds covers the stalls seen and still
	 * catches a timer that is off by a minute.
	 */
	static final long OFFSET_TOLERANCE_MS = 30_000L;
	private static final Pattern OFFSET = Pattern.compile("now([+-])(\\d+)m(\\d+)s");

	/** {@code expected.equals(actual)}, except "now±XmYs" offsets match within {@link #OFFSET_TOLERANCE_MS}. */
	static boolean matches(JsonElement expected, JsonElement actual) {
		if (expected.isJsonObject() && actual.isJsonObject()) {
			JsonObject e = expected.getAsJsonObject();
			JsonObject a = actual.getAsJsonObject();
			return e.keySet().equals(a.keySet())
					&& e.keySet().stream().allMatch(key -> matches(e.get(key), a.get(key)));
		}
		if (expected.isJsonArray() && actual.isJsonArray()) {
			JsonArray e = expected.getAsJsonArray();
			JsonArray a = actual.getAsJsonArray();
			if (e.size() != a.size()) {
				return false;
			}
			for (int i = 0; i < e.size(); i++) {
				if (!matches(e.get(i), a.get(i))) {
					return false;
				}
			}
			return true;
		}
		Long e = offsetMs(expected);
		Long a = offsetMs(actual);
		if (e != null && a != null) {
			return Math.abs(e - a) <= OFFSET_TOLERANCE_MS;
		}
		return expected.equals(actual);
	}

	private static Long offsetMs(JsonElement value) {
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
			return null;
		}
		Matcher m = OFFSET.matcher(value.getAsString());
		if (!m.matches()) {
			return null;
		}
		long ms = (Long.parseLong(m.group(2)) * 60 + Long.parseLong(m.group(3))) * 1000;
		return m.group(1).equals("-") ? -ms : ms;
	}

	private static String relative(long deltaMs) {
		long rounded = Math.round(deltaMs / (double) ROUND_MS) * ROUND_MS / 1000;
		String sign = rounded < 0 ? "-" : "+";
		long s = Math.abs(rounded);
		return "now" + sign + (s / 60) + "m" + (s % 60) + "s";
	}
}
