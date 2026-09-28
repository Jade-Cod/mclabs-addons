package dev.jade.labsaddons.e2e;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Plays one scenario record against the local world: the only Minecraft-facing code in
 * the harness, and so the only file each version branch keeps its own copy of.
 *
 * <p>Record shapes are gui-dumper's (chat, actionbar, screen, full, delta, close) plus a
 * few the harness adds to set a scene or poke the client (sidebar, command,
 * playerCommand, key, mouse, wait, screenshot).
 */
final class Replay {
	private static final MenuType<?>[] ROWS = {
			MenuType.GENERIC_9x1, MenuType.GENERIC_9x2, MenuType.GENERIC_9x3,
			MenuType.GENERIC_9x4, MenuType.GENERIC_9x5, MenuType.GENERIC_9x6};

	/** Slot clicks the open menu received, as "slot:button:ACTION" — server thread writes, test reads. */
	static final List<String> CLICKS = Collections.synchronizedList(new ArrayList<>());

	private final ClientGameTestContext context;
	private final TestSingleplayerContext world;
	private String name = "";
	private int menus;
	/** The open replay menu's contents. Only touched on the server thread. */
	private SimpleContainer menu;

	Replay(ClientGameTestContext context, TestSingleplayerContext world) {
		this.context = context;
		this.world = world;
	}

	void play(JsonObject record) {
		String type = record.get("type").getAsString();
		switch (type) {
			case "capture", "end", "note" -> { }
			case "chat" -> send(record, record.has("overlay") && record.get("overlay").getAsBoolean());
			case "actionbar" -> send(record, true);
			case "screen" -> open(record.get("title"), record.get("containerSlots").getAsInt());
			case "full", "delta" -> fill(record.getAsJsonObject("slots"), type.equals("full"));
			case "close" -> {
				// A menu's last frame is where the mod's overlays sit (crate result, a
				// casino board mid-round), and it is gone once the menu shuts.
				context.takeScreenshot(name + "-menu" + ++menus);
				close();
			}
			case "sidebar" -> sidebar(record.get("title").getAsString());
			case "command" -> world.getServer().runCommand(record.get("command").getAsString());
			case "playerCommand" -> {
				String command = record.get("command").getAsString();
				context.runOnClient(client -> client.player.connection.sendCommand(command));
			}
			case "key" -> context.getInput().pressKey(InputConstants.getKey(record.get("key").getAsString()));
			case "mouse" -> click(record.get("x").getAsDouble(), record.get("y").getAsDouble(),
					record.has("button") ? record.get("button").getAsInt() : 0);
			case "wait" -> context.waitTicks(record.get("ticks").getAsInt());
			case "screenshot" -> context.takeScreenshot(record.get("name").getAsString());
			default -> throw new IllegalArgumentException("Unknown record type: " + type);
		}
	}

	void sidebar(String title) {
		world.getServer().runCommand("scoreboard objectives remove e2e");
		world.getServer().runCommand("scoreboard objectives add e2e dummy " + quote(title));
		world.getServer().runCommand("scoreboard objectives setdisplay sidebar e2e");
	}

	void begin(String scenario) {
		name = scenario;
		menus = 0;
	}

	/** Leaves no menu open, no clicks and no chat behind for the next scenario. */
	void reset() {
		close();
		CLICKS.clear();
		context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false));
	}

	private void send(JsonObject record, boolean overlay) {
		JsonElement json = record.has("text") ? record.get("text") : record.get("plain");
		world.getServer().runOnServer(server -> player(server).sendSystemMessage(text(server, json), overlay));
	}

	private void open(JsonElement title, int containerSlots) {
		int rows = Math.max(1, Math.min(6, (containerSlots + 8) / 9));
		world.getServer().runOnServer(server -> {
			SimpleContainer container = new SimpleContainer(rows * 9);
			menu = container;
			player(server).openMenu(new SimpleMenuProvider(
					(syncId, playerInventory, player) -> new ReplayMenu(rows, syncId, playerInventory, container),
					text(server, title)));
		});
		context.waitTicks(2);
	}

	private void fill(JsonObject slots, boolean full) {
		world.getServer().runOnServer(server -> {
			if (menu == null) {
				throw new IllegalStateException("Slot record with no menu open");
			}
			if (full) {
				menu.clearContent();
			}
			for (Map.Entry<String, JsonElement> slot : slots.entrySet()) {
				int index = Integer.parseInt(slot.getKey());
				if (index < menu.getContainerSize()) {
					menu.setItem(index, stack(server, slot.getValue()));
				}
			}
		});
		context.waitTicks(2);
	}

	private void close() {
		world.getServer().runOnServer(server -> {
			menu = null;
			player(server).closeContainer();
		});
		context.waitTicks(2);
	}

	/** x/y are in GUI-scaled pixels, the space every screen and HUD widget lays out in. */
	private void click(double x, double y, int button) {
		double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
		context.getInput().setCursorPos(x * scale, y * scale);
		context.getInput().pressMouse(button);
		context.waitTicks(2);
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static Component text(MinecraftServer server, JsonElement json) {
		if (json == null || json.isJsonNull()) {
			return Component.empty();
		}
		return ComponentSerialization.CODEC.parse(server.registryAccess().createSerializationContext(JsonOps.INSTANCE), json).getOrThrow();
	}

	/**
	 * Either shape the dumps come in: gui-dumper's {id, count, name, lore}, or a full
	 * item with a {@code components} map, which is vanilla's own item codec.
	 */
	private static ItemStack stack(MinecraftServer server, JsonElement json) {
		if (json == null || json.isJsonNull()) {
			return ItemStack.EMPTY;
		}
		JsonObject item = json.getAsJsonObject();
		if (item.has("components")) {
			return ItemStack.CODEC.parse(server.registryAccess().createSerializationContext(JsonOps.INSTANCE), item).getOrThrow();
		}
		ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(item.get("id").getAsString())),
				item.has("count") ? item.get("count").getAsInt() : 1);
		if (item.has("name")) {
			stack.set(DataComponents.CUSTOM_NAME, text(server, item.get("name")));
		}
		if (item.has("lore")) {
			List<Component> lore = new ArrayList<>();
			item.getAsJsonArray("lore").forEach(line -> lore.add(text(server, line)));
			stack.set(DataComponents.LORE, new ItemLore(lore));
		}
		return stack;
	}

	private static String quote(String s) {
		return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	/** A server-plugin style menu: every click is noted and cancelled, nothing moves. */
	private static final class ReplayMenu extends ChestMenu {
		ReplayMenu(int rows, int syncId, Inventory playerInventory, SimpleContainer container) {
			super(ROWS[rows - 1], syncId, playerInventory, container, rows);
		}

		@Override
		public void clicked(int slot, int button, ContainerInput action, Player player) {
			CLICKS.add(slot + ":" + button + ":" + action);
			sendAllDataToRemote();
		}

		@Override
		public ItemStack quickMoveStack(Player player, int slot) {
			return ItemStack.EMPTY;
		}
	}
}
