package dev.jade.labsaddons.hud;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.entity.player.PlayerSkin;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Resolves and caches player heads/skins by username, for the runner screens and the
 * casino boards. Names arrive as plain strings (no UUID), so each is resolved once via an
 * async Mojang profile lookup; until it lands, a default skin is shown. Nothing is sent to
 * the MCLabs server — only public skin data is fetched, the same way vanilla renders any
 * player.
 *
 * <p>Both the size cap and the failure back-off exist because the coinflip lobby widened
 * what feeds this: the runner board asked about a bounded list of names, while the lobby
 * asks about every poster it has ever drawn, once per frame.
 */
public final class PlayerSkinCache {
	private record Entry(Supplier<PlayerSkin> skin, GameProfile profile) {
	}

	/** Sentinel for "resolve in flight" so a name is only looked up once. */
	private static final Entry IN_FLIGHT = new Entry(null, null);
	private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();
	/**
	 * Names whose lookup failed, and when. A failure used to drop straight out of the cache
	 * so the next render would ask again — which, on a screen that draws a head every
	 * frame, is a profile lookup every frame for as long as it keeps failing.
	 */
	private static final Map<String, Long> FAILED = new ConcurrentHashMap<>();
	private static final long RETRY_AFTER_MS = 60_000L;
	/**
	 * How many resolved names to keep. The coinflip lobby feeds this every poster it sees,
	 * so over a long session the set of names is open-ended where the runner board's was
	 * not.
	 */
	private static final int MAX_CACHED = 512;

	private PlayerSkinCache() {
	}

	/** Skin to draw for {@code name}: the real one once resolved, a default until then. */
	public static PlayerSkin skin(String name) {
		Entry entry = CACHE.get(name);
		if (entry != null && entry.skin() != null) {
			return entry.skin().get();
		}
		resolve(name);
		return DefaultPlayerSkin.get(offlineUuid(name));
	}

	/** Resolved {@link GameProfile} for {@code name}, or {@code null} if not yet available. */
	public static GameProfile profile(String name) {
		Entry entry = CACHE.get(name);
		if (entry != null) {
			return entry.profile();
		}
		resolve(name);
		return null;
	}

	private static void resolve(String name) {
		Long failedAtMs = FAILED.get(name);
		if (failedAtMs != null && System.currentTimeMillis() - failedAtMs < RETRY_AFTER_MS) {
			return; // failed recently; the default skin stands until the window is up
		}
		if (CACHE.putIfAbsent(name, IN_FLIGHT) != null) {
			return; // already resolving or resolved
		}
		Minecraft client = Minecraft.getInstance();
		ResolvableProfile.createUnresolved(name)
				.resolveProfile(client.services().profileResolver())
				.thenAccept(profile -> {
					Supplier<PlayerSkin> supplier = client.getSkinManager().createLookup(profile, false);
					CACHE.put(name, new Entry(supplier, profile));
					evictDownToCap();
				})
				.exceptionally(e -> {
					// Drop the IN_FLIGHT sentinel so a failed lookup (offline, unknown
					// name, rate-limited) can be retried — but not until the retry window
					// is up, so a name that keeps failing costs one lookup a minute rather
					// than one a frame.
					CACHE.remove(name);
					long now = System.currentTimeMillis();
					// Only names that failed inside the window can still be refused, so
					// pruning here is all it takes to bound this map.
					FAILED.values().removeIf(at -> now - at > RETRY_AFTER_MS);
					FAILED.put(name, now);
					return null;
				});
	}

	/**
	 * Trims the cache back to {@link #MAX_CACHED}.
	 *
	 * <p>ponytail: arbitrary victims, not least-recently-used. A miss costs one profile
	 * lookup and the cap only exists so a long session cannot grow this without bound; an
	 * LRU would want an access order this has no reason to track.
	 */
	private static void evictDownToCap() {
		if (CACHE.size() <= MAX_CACHED) {
			return;
		}
		Iterator<String> names = CACHE.keySet().iterator();
		while (names.hasNext() && CACHE.size() > MAX_CACHED) {
			names.next();
			names.remove();
		}
	}

	private static UUID offlineUuid(String name) {
		return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
	}
}
