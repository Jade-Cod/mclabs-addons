package dev.jade.labsaddons.police;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The neighbourhood outlines, so the client can tell where an arrest happened.
 *
 * <p>The server announces a neighbourhood with a boss bar on entry only — it fades
 * after a few seconds and never posts one on exit — so the bar is no use at the moment
 * a confiscation lands. The outlines are the server's own WorldGuard regions, taken
 * from the public Dynmap at {@code map.labs-mc.com:8439} and bundled as
 * {@code assets/labsaddons/neighbourhoods.json}: eight polygons, ~3KB, no network at
 * runtime. Re-fetch and replace that file if MCLabs ever redraws a region.
 *
 * <p>Yellow's polygon sits entirely inside Green's, which is not a mistake — walking
 * into Yellow raises both neighbourhoods' boss bars, because the region genuinely
 * nests. So membership is a set, not a single answer, and a Yellow arrest counts for
 * Green too.
 *
 * <p>Regions only exist in the Spawn world; anywhere else nothing contains you.
 */
public final class Neighbourhoods {
	private static final Logger LOGGER = LoggerFactory.getLogger("labsaddons");
	private static final String PATH = "/assets/labsaddons/neighbourhoods.json";

	/** Region name (lowercased) to its polygon as {x, z} pairs; empty if the file failed to load. */
	private static Map<String, double[][]> regions;

	private Neighbourhoods() {
	}

	/** Whether the local player is standing inside {@code region} right now. */
	public static boolean holdsPlayer(String region) {
		ClientPlayerEntity player = MinecraftClient.getInstance().player;
		return player != null && contains(region, player.getX(), player.getZ());
	}

	/** Whether {@code region}'s outline encloses the point. Unknown regions contain nothing. */
	static boolean contains(String region, double x, double z) {
		double[][] polygon = load().get(region.toLowerCase(Locale.ROOT).trim());
		return polygon != null && encloses(polygon, x, z);
	}

	/**
	 * Standard ray cast: count the edges crossed going left from the point. Odd means
	 * inside. Blocks on an edge may fall either way, which no challenge can hinge on.
	 */
	private static boolean encloses(double[][] polygon, double x, double z) {
		boolean inside = false;
		for (int i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
			double xi = polygon[i][0];
			double zi = polygon[i][1];
			double xj = polygon[j][0];
			double zj = polygon[j][1];
			if ((zi > z) != (zj > z) && x < (xj - xi) * (z - zi) / (zj - zi) + xi) {
				inside = !inside;
			}
		}
		return inside;
	}

	private static synchronized Map<String, double[][]> load() {
		if (regions != null) {
			return regions;
		}
		Map<String, double[][]> parsed = new HashMap<>();
		try (InputStream in = Neighbourhoods.class.getResourceAsStream(PATH)) {
			if (in == null) {
				throw new IllegalStateException(PATH + " is missing from the jar");
			}
			JsonObject root = JsonParser.parseReader(
					new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			for (String name : root.keySet()) {
				parsed.put(name.toLowerCase(Locale.ROOT), new Gson().fromJson(root.get(name), double[][].class));
			}
		} catch (Exception e) {
			// An empty map means every patrol condition reads "no", so a broken file costs
			// uncredited bumps the /mastery scrape will true up — never a wrong one.
			LOGGER.warn("[labsaddons] Couldn't read the neighbourhood outlines; "
					+ "region patrol challenges will only update on a /mastery scrape", e);
		}
		regions = Map.copyOf(parsed);
		return regions;
	}
}
