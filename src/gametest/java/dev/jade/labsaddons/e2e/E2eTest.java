package dev.jade.labsaddons.e2e;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.jade.labsaddons.config.E2eStore;
import dev.jade.labsaddons.config.LabsAddonsConfig;
import dev.jade.labsaddons.server.McLabsSession;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Replays every scenario in {@code src/gametest/e2e/scenarios} (committed, server lines
 * only) and {@code src/gametest/e2e/local} (gitignored: raw captures and whole session
 * logs) through a local world with the real mod loaded, and compares what each one did to
 * the mod's saved state (plus the menu clicks it sent) against
 * {@code expected/<scenario>.json} or {@code local-expected/<scenario>.json}.
 *
 * <p>A scenario with no expected file writes one and is reported NEW, for review;
 * {@code -Pe2eUpdate} rewrites them all. Minecraft-free apart from the context types,
 * so it is the same file on every version branch.
 */
public class E2eTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("labsaddons-e2e");
	/** A capture's pauses are kept, up to this: a minute of idle menu tests nothing. */
	private static final long MAX_GAP_MS = 3_000L;
	private static final int SETTLE_TICKS = 10;

	@Override
	public void runTest(ClientGameTestContext context) {
		Path dir = Path.of(System.getProperty("labsaddons.e2e.dir"));
		Path out = Path.of(System.getProperty("labsaddons.e2e.out"));
		boolean update = Boolean.getBoolean("labsaddons.e2e.update");
		String only = System.getProperty("labsaddons.e2e.only", "");

		JsonArray report = new JsonArray();
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			Replay replay = new Replay(context, world);
			replay.sidebar("MCLabs Spawn");
			context.waitFor(client -> McLabsSession.isActive());
			for (Path scenario : scenarios(dir.resolve("scenarios"), only)) {
				report.add(runOne(context, replay, scenario, dir.resolve("expected"), update));
			}
			// The local captures run for ten minutes; a boost timer set by a committed scenario
			// would run out somewhere inside them and land in whichever scenario was playing.
			// A fresh store keeps each phase's results its own.
			context.runOnClient(client -> E2eStore.fresh(FabricLoader.getInstance().getGameDir().resolve("e2e-local")));
			for (Path scenario : scenarios(dir.resolve("local"), only)) {
				report.add(runOne(context, replay, scenario, dir.resolve("local-expected"), update));
			}
		}
		write(out.resolve("report.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));

		List<String> failed = new ArrayList<>();
		report.forEach(r -> {
			JsonObject result = r.getAsJsonObject();
			if (result.get("status").getAsString().equals("FAIL")) {
				failed.add(result.get("scenario").getAsString());
			}
		});
		if (!failed.isEmpty()) {
			throw new AssertionError(failed.size() + " scenario(s) failed: " + failed + " — see " + out.resolve("report.json"));
		}
	}

	private static JsonObject runOne(ClientGameTestContext context, Replay replay, Path scenario, Path expectedDir, boolean update) {
		String name = scenario.getFileName().toString().replaceFirst("\\.jsonl$", "");
		LOGGER.info("[e2e] BEGIN {}", name);
		JsonObject result = new JsonObject();
		result.addProperty("scenario", name);
		JsonArray problems = new JsonArray();
		long logStart = logSize();

		JsonObject before = snapshot(context);
		replay.begin(name);
		long lastT = -1;
		for (String line : lines(scenario)) {
			if (line.isBlank()) {
				continue;
			}
			JsonObject record = JsonParser.parseString(line).getAsJsonObject();
			if (record.has("t")) {
				long t = record.get("t").getAsLong();
				if (lastT >= 0 && t > lastT) {
					context.waitTicks((int) (Math.min(t - lastT, MAX_GAP_MS) / 50));
				}
				lastT = t;
			}
			try {
				replay.play(record);
			} catch (RuntimeException e) {
				problems.add(record.get("type").getAsString() + ": " + e);
				break;
			}
		}
		context.waitTicks(SETTLE_TICKS);
		result.addProperty("screenshot", context.takeScreenshot(name).toString());

		JsonObject actual = new JsonObject();
		actual.add("state", StateSnapshot.diff(before, snapshot(context), System.currentTimeMillis()));
		JsonArray clicks = new JsonArray();
		new ArrayList<>(Replay.CLICKS).forEach(clicks::add);
		actual.add("clicks", clicks);
		replay.reset();

		logErrorsSince(logStart).forEach(problems::add);
		result.add("actual", actual);
		result.add("problems", problems);

		Path expectedFile = expectedDir.resolve(name + ".json");
		String status;
		if (update || !Files.exists(expectedFile)) {
			write(expectedFile, new GsonBuilder().setPrettyPrinting().create().toJson(actual) + "\n");
			status = update ? "UPDATED" : "NEW";
		} else {
			JsonObject expected = JsonParser.parseString(read(expectedFile)).getAsJsonObject();
			result.add("expected", expected);
			status = expected.equals(actual) ? "PASS" : "FAIL";
		}
		if (!problems.isEmpty()) {
			status = "FAIL";
		}
		result.addProperty("status", status);
		LOGGER.info("[e2e] END {} {}", name, status);
		return result;
	}

	private static JsonObject snapshot(ClientGameTestContext context) {
		context.runOnClient(client -> LabsAddonsConfig.get().saveNow());
		try {
			return StateSnapshot.read(LabsAddonsConfig.storage().root());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static List<Path> scenarios(Path dir, String only) {
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		try (Stream<Path> files = Files.list(dir)) {
			return files.filter(p -> p.toString().endsWith(".jsonl"))
					.filter(p -> only.isEmpty() || Arrays.stream(only.split(",")).anyMatch(p.getFileName().toString()::contains))
					.sorted()
					.toList();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static Path log() {
		return FabricLoader.getInstance().getGameDir().resolve("logs/latest.log");
	}

	private static long logSize() {
		try {
			return Files.size(log());
		} catch (IOException e) {
			return 0;
		}
	}

	/** Anything logged at ERROR, or any stack trace, while the scenario played. */
	private static List<String> logErrorsSince(long offset) {
		try {
			byte[] all = Files.readAllBytes(log());
			String since = new String(all, (int) Math.min(offset, all.length), (int) Math.max(0, all.length - offset), StandardCharsets.UTF_8);
			return since.lines()
					.filter(l -> l.contains("/ERROR]") || l.contains("Exception") || l.startsWith("Caused by"))
					.toList();
		} catch (IOException e) {
			return List.of("could not read log: " + e);
		}
	}

	private static List<String> lines(Path file) {
		return read(file).lines().toList();
	}

	private static String read(Path file) {
		try {
			return Files.readString(file);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void write(Path file, String content) {
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, content);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
