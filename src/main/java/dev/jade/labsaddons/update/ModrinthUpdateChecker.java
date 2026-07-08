package dev.jade.labsaddons.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Checks Modrinth for a newer release of this mod and, if one exists, posts a
 * local-only chat line. Called once per join the moment McLabsSession confirms
 * the player is on MCLabs. Entirely off the client thread; any failure (offline,
 * Modrinth down, bad response) is logged and swallowed.
 */
public final class ModrinthUpdateChecker {
	private static final Logger LOGGER = LoggerFactory.getLogger("fishbite");
	private static final String MOD_ID = "fishbite";
	private static final String VERSIONS_URL = "https://api.modrinth.com/v2/project/mclabs-addons/version";
	private static final Duration TIMEOUT = Duration.ofSeconds(5);
	private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

	// Guards against overlapping checks (e.g. rapid rejoin/reconnect) firing duplicate
	// requests and, worse, duplicate "update available" chat lines.
	private static final AtomicBoolean CHECK_IN_FLIGHT = new AtomicBoolean(false);

	private ModrinthUpdateChecker() {
	}

	public static void checkAndNotify() {
		if (!CHECK_IN_FLIGHT.compareAndSet(false, true)) {
			return;
		}

		Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(MOD_ID);
		if (container.isEmpty()) {
			CHECK_IN_FLIGHT.set(false);
			return;
		}
		String currentVersion = container.get().getMetadata().getVersion().getFriendlyString();

		HttpRequest request = HttpRequest.newBuilder(URI.create(VERSIONS_URL))
				.timeout(TIMEOUT)
				.header("User-Agent", "fishbite-indicator/" + currentVersion + " (dev.jade.labsaddons)")
				.GET()
				.build();

		CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenAccept(response -> handleResponse(response, currentVersion))
				.exceptionally(e -> {
					LOGGER.debug("[fishbite] Update check failed (offline or Modrinth unreachable).", e);
					return null;
				})
				.whenComplete((unused, throwable) -> CHECK_IN_FLIGHT.set(false));
	}

	private static void handleResponse(HttpResponse<String> response, String currentVersion) {
		if (response.statusCode() != 200) {
			return;
		}
		try {
			String mcVersion = SharedConstants.getGameVersion().name();
			JsonArray versions = JsonParser.parseString(response.body()).getAsJsonArray();
			for (JsonElement element : versions) {
				JsonObject version = element.getAsJsonObject();
				if (!supportsGameVersion(version, mcVersion)) {
					continue;
				}
				String latest = version.get("version_number").getAsString();
				if (ModVersion.isNewer(latest, currentVersion)) {
					notifyPlayer(latest);
				}
				return; // entries are newest-first; the first matching entry is the answer
			}
		} catch (RuntimeException e) {
			LOGGER.debug("[fishbite] Failed to parse Modrinth version response.", e);
		}
	}

	private static boolean supportsGameVersion(JsonObject version, String mcVersion) {
		JsonArray gameVersions = version.getAsJsonArray("game_versions");
		for (JsonElement gv : gameVersions) {
			if (gv.getAsString().equals(mcVersion)) {
				return true;
			}
		}
		return false;
	}

	private static void notifyPlayer(String latestVersion) {
		MinecraftClient client = MinecraftClient.getInstance();
		client.execute(() -> {
			if (client.player != null) {
				client.player.sendMessage(Text.literal("[MCLabs Addons] ").formatted(Formatting.AQUA)
						.append(Text.literal("v" + latestVersion + " is available on Modrinth.")
								.formatted(Formatting.GRAY)), false);
			}
		});
	}
}
