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
					changed.add(file.getKey() + "/" + entry.getKey(), normalise(entry.getValue(), nowMs));
				}
			}
		}
		return changed;
	}

	/** Timestamps become "now+37m30s"-style offsets; everything else is left alone. */
	static JsonElement normalise(JsonElement value, long nowMs) {
		if (value.isJsonObject()) {
			JsonObject out = new JsonObject();
			value.getAsJsonObject().entrySet().forEach(e -> out.add(e.getKey(), normalise(e.getValue(), nowMs)));
			return out;
		}
		if (value.isJsonArray()) {
			JsonArray out = new JsonArray();
			value.getAsJsonArray().forEach(e -> out.add(normalise(e, nowMs)));
			return out;
		}
		if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
			double number = value.getAsDouble();
			if (number >= EPOCH_MIN && number <= EPOCH_MAX && number == Math.rint(number)) {
				return new JsonPrimitive(relative((long) number - nowMs));
			}
		}
		return value;
	}

	private static String relative(long deltaMs) {
		long rounded = Math.round(deltaMs / (double) ROUND_MS) * ROUND_MS / 1000;
		String sign = rounded < 0 ? "-" : "+";
		long s = Math.abs(rounded);
		return "now" + sign + (s / 60) + "m" + (s % 60) + "s";
	}
}
