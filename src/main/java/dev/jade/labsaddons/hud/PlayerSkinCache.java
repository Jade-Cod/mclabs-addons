package dev.jade.labsaddons.hud;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.entity.player.SkinTextures;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Resolves and caches player heads/skins by username for the runner screens.
 * Runner names are stored as plain strings (no UUID), so each name is resolved
 * once via an async Mojang profile lookup; until it lands, a default skin is
 * shown. Nothing is sent to the MCLabs server — only public skin data is
 * fetched, the same way vanilla renders any player.
 */
public final class PlayerSkinCache {
	private record Entry(Supplier<SkinTextures> skin, GameProfile profile) {
	}

	/** Sentinel for "resolve in flight" so a name is only looked up once. */
	private static final Entry IN_FLIGHT = new Entry(null, null);
	private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();

	private PlayerSkinCache() {
	}

	/** Skin to draw for {@code name}: the real one once resolved, a default until then. */
	public static SkinTextures skin(String name) {
		Entry entry = CACHE.get(name);
		if (entry != null && entry.skin() != null) {
			return entry.skin().get();
		}
		resolve(name);
		return DefaultSkinHelper.getSkinTextures(offlineUuid(name));
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
		if (CACHE.putIfAbsent(name, IN_FLIGHT) != null) {
			return; // already resolving or resolved
		}
		MinecraftClient client = MinecraftClient.getInstance();
		ProfileComponent.ofDynamic(name)
				.resolve(client.getApiServices().profileResolver())
				.thenAccept(profile -> {
					Supplier<SkinTextures> supplier = client.getSkinProvider().supplySkinTextures(profile, false);
					CACHE.put(name, new Entry(supplier, profile));
				})
				.exceptionally(e -> {
					// Drop the IN_FLIGHT sentinel so a failed lookup (offline, unknown
					// name, rate-limited) can be retried next render instead of sticking
					// on the default skin until restart.
					CACHE.remove(name);
					return null;
				});
	}

	private static UUID offlineUuid(String name) {
		return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
	}
}
