package dev.jade.labsaddons.crate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Both voter crates' published odds, shipped in the jar.
 *
 * <p>These tables do not change — they are a property of the crate, not of the player — so
 * asking somebody to go and punch a crate before the mod could tell them anything was a setup
 * step for data the mod could simply have. Read once, off the jar, and used whenever nothing
 * better has been scraped.
 *
 * <p>Scraping stays, and still wins. If MCLabs ever retunes a table the shipped one goes
 * stale, and punching that crate puts it right without waiting for a mod update.
 *
 * <p>Duplicates are kept exactly as the server listed them. The Voter Crate names two
 * different rewards "Enhanced Farming Access", and collapsing that here would hide the
 * ambiguity from {@link VoteOdds.Table}, which is the thing that knows to decline it.
 */
final class VoteOddsDefaults {
	private static final Logger LOGGER = LoggerFactory.getLogger("labsaddons");
	private static final String PATH = "/assets/labsaddons/vote_odds.json";

	private static Map<String, List<VoteOddsEntry>> bundled;

	private VoteOddsDefaults() {
	}

	/** Crate name to its rewards, or an empty map if the file could not be read. */
	static synchronized Map<String, List<VoteOddsEntry>> get() {
		if (bundled != null) {
			return bundled;
		}
		Map<String, List<VoteOddsEntry>> parsed = new LinkedHashMap<>();
		try (InputStream in = VoteOddsDefaults.class.getResourceAsStream(PATH)) {
			if (in == null) {
				throw new IllegalStateException(PATH + " is missing from the jar");
			}
			JsonObject root = JsonParser.parseReader(
					new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			for (String crate : root.keySet()) {
				parsed.put(crate, read(crate, root.getAsJsonArray(crate)));
			}
		} catch (Exception broken) {
			// An empty map is the pre-existing behaviour: each draw reads "odds unknown" until
			// the player happens to punch a crate. Never a wrong figure.
			LOGGER.warn("[labsaddons] Couldn't read the bundled voter crate odds; "
					+ "a three-way choice will show no figures until a crate is punched", broken);
		}
		bundled = Map.copyOf(parsed);
		return bundled;
	}

	private static List<VoteOddsEntry> read(String crate, JsonArray rewards) {
		List<VoteOddsEntry> out = new ArrayList<>();
		for (JsonElement element : rewards) {
			JsonObject reward = element.getAsJsonObject();
			out.add(new VoteOddsEntry(crate, reward.get("item").getAsString(),
					reward.get("chance").getAsDouble()));
		}
		return out;
	}
}
