package dev.jade.labsaddons.event;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import dev.jade.labsaddons.hud.HudObjectSettings;
import dev.jade.labsaddons.hud.LabeledTimerHudObject;
import dev.jade.labsaddons.hud.TimeFormat;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

/** Mini-event widget: upcoming countdown, then active type + time + reward. */
public class MiniEventHudObject extends LabeledTimerHudObject {
	public static final String ID = "mini_event";
	private static final int DEFAULT_TEXT_COLOR = 0xFF7FE0FF;

	/** The server's own Chem Cache head, copied from the /minievent menu item. */
	private static final UUID CACHE_HEAD_ID = UUID.fromString("3c5bc3ab-6c86-47fe-bd2f-f35ca099b36b");
	private static final String CACHE_HEAD_TEXTURE =
			"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTBkMWIwNzMyYmY3YTcwZGU0ZGMwMTU1OWNjNWM5ODExMDY4ZWY3YjYwOTUwMTAzODI3MDlmOTQwOTM5MjdmNiJ9fX0=";
	private static ItemStack cacheHead;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public HudObjectSettings defaultSettings() {
		HudObjectSettings defaults = new HudObjectSettings();
		defaults.x = 0.985f;
		defaults.y = 0.42f;
		defaults.textColor = DEFAULT_TEXT_COLOR;
		return defaults;
	}

	@Override
	public boolean shouldRender() {
		return MiniEventTracker.isActive() || MiniEventTracker.isUpcoming();
	}

	@Override
	public EditorAction editorAction() {
		return new EditorAction(Text.translatable("labsaddons.hud.mini_event.clear"), MiniEventTracker::clear);
	}

	@Override
	@Nullable
	protected Text header(boolean preview) {
		if (MiniEventTracker.isActive()) {
			return Text.literal(MiniEventTracker.type() + " Event");
		}
		if (MiniEventTracker.isUpcoming()) {
			return Text.translatable("labsaddons.hud.mini_event.upcoming");
		}
		return Text.literal("Fishing Event");
	}

	@Override
	@Nullable
	protected ItemStack icon(boolean preview) {
		if (MiniEventTracker.isActive()) {
			return iconForType(MiniEventTracker.type());
		}
		if (MiniEventTracker.isUpcoming()) {
			return new ItemStack(Items.CLOCK);
		}
		return new ItemStack(Items.FISHING_ROD);
	}

	@Override
	protected String timeText(boolean preview) {
		if (MiniEventTracker.isActive()) {
			return TimeFormat.hms(MiniEventTracker.activeRemainingMs());
		}
		if (MiniEventTracker.isUpcoming()) {
			return TimeFormat.hms(MiniEventTracker.upcomingRemainingMs());
		}
		return "30:00";
	}

	private static ItemStack iconForType(String type) {
		String lower = type.toLowerCase(Locale.ROOT);
		if (lower.contains("cache")) {
			return cacheHead();
		}
		if (lower.contains("selling") || lower.contains("sell")) {
			return new ItemStack(Items.LEATHER_HORSE_ARMOR);
		}
		if (lower.contains("fishing")) {
			return new ItemStack(Items.FISHING_ROD);
		}
		if (lower.contains("farming") || lower.contains("farm")) {
			return new ItemStack(Items.IRON_HOE);
		}
		if (lower.contains("mining") || lower.contains("mine")) {
			return new ItemStack(Items.DIAMOND_PICKAXE);
		}
		if (lower.contains("pit") || lower.contains("hill") || lower.contains("koth")) {
			return new ItemStack(Items.NETHERITE_SWORD);
		}
		return new ItemStack(Items.CLOCK);
	}

	/** Player head wearing the Chem Cache skin; built once, then reused. */
	private static ItemStack cacheHead() {
		if (cacheHead == null) {
			PropertyMap properties = new PropertyMap(
					ImmutableMultimap.of("textures", new Property("textures", CACHE_HEAD_TEXTURE)));
			ItemStack head = new ItemStack(Items.PLAYER_HEAD);
			head.set(DataComponentTypes.PROFILE, ProfileComponent.ofStatic(new GameProfile(CACHE_HEAD_ID, "", properties)));
			cacheHead = head;
		}
		return cacheHead;
	}
}
