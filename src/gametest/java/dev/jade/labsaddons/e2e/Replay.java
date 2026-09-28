package dev.jade.labsaddons.e2e;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Identifier;

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
 * playerCommand, key, mouse, button, window, wait, screenshot).
 */
final class Replay {
	private static final ScreenHandlerType<?>[] ROWS = {
			ScreenHandlerType.GENERIC_9X1, ScreenHandlerType.GENERIC_9X2, ScreenHandlerType.GENERIC_9X3,
			ScreenHandlerType.GENERIC_9X4, ScreenHandlerType.GENERIC_9X5, ScreenHandlerType.GENERIC_9X6};

	/** Slot clicks the open menu received, as "slot:button:ACTION" — server thread writes, test reads. */
	static final List<String> CLICKS = Collections.synchronizedList(new ArrayList<>());

	private final ClientGameTestContext context;
	private final TestSingleplayerContext world;
	private String name = "";
	private int menus;
	/** The open replay menu's contents. Only touched on the server thread. */
	private SimpleInventory menu;

	Replay(ClientGameTestContext context, TestSingleplayerContext world) {
		this.context = context;
		this.world = world;
	}

	void play(JsonObject record) {
		String type = record.get("type").getAsString();
		switch (type) {
			// satchel: gui-dumper notes the Smuggler satchel it saw; nothing to replay.
			case "capture", "end", "note", "satchel" -> { }
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
				context.runOnClient(client -> client.player.networkHandler.sendChatCommand(command));
			}
			case "key" -> context.getInput().pressKey(InputUtil.fromTranslationKey(record.get("key").getAsString()));
			case "mouse" -> click(record.get("x").getAsDouble(), record.get("y").getAsDouble(),
					record.has("button") ? record.get("button").getAsInt() : 0);
			case "button" -> {
				context.clickScreenButton(record.get("text").getAsString());
				context.waitTicks(5);
			}
			case "window" -> {
				context.getInput().resizeWindow(record.get("width").getAsInt(), record.get("height").getAsInt());
				context.waitTicks(5);
			}
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
		context.runOnClient(client -> client.inGameHud.getChatHud().clear(false));
	}

	private void send(JsonObject record, boolean overlay) {
		JsonElement json = record.has("text") ? record.get("text") : record.get("plain");
		world.getServer().runOnServer(server -> player(server).sendMessage(text(server, json), overlay));
		// The client reads it on its next tick; a screenshot straight after would miss it.
		context.waitTicks(2);
	}

	private void open(JsonElement title, int containerSlots) {
		int rows = Math.max(1, Math.min(6, (containerSlots + 8) / 9));
		world.getServer().runOnServer(server -> {
			SimpleInventory inventory = new SimpleInventory(rows * 9);
			menu = inventory;
			player(server).openHandledScreen(new SimpleNamedScreenHandlerFactory(
					(syncId, playerInventory, player) -> new ReplayMenu(rows, syncId, playerInventory, inventory),
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
				menu.clear();
			}
			for (Map.Entry<String, JsonElement> slot : slots.entrySet()) {
				int index = Integer.parseInt(slot.getKey());
				if (index < menu.size()) {
					menu.setStack(index, stack(server, slot.getValue()));
				}
			}
		});
		context.waitTicks(2);
	}

	private void close() {
		world.getServer().runOnServer(server -> {
			menu = null;
			player(server).closeHandledScreen();
		});
		context.waitTicks(2);
	}

	/** x/y are in GUI-scaled pixels, the space every screen and HUD widget lays out in. */
	private void click(double x, double y, int button) {
		double scale = context.computeOnClient(client -> client.getWindow().getScaleFactor());
		context.getInput().setCursorPos(x * scale, y * scale);
		context.getInput().pressMouse(button);
		context.waitTicks(2);
	}

	private static ServerPlayerEntity player(MinecraftServer server) {
		return server.getPlayerManager().getPlayerList().getFirst();
	}

	private static Text text(MinecraftServer server, JsonElement json) {
		if (json == null || json.isJsonNull()) {
			return Text.empty();
		}
		return TextCodecs.CODEC.parse(server.getRegistryManager().getOps(JsonOps.INSTANCE), json).getOrThrow();
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
			return ItemStack.CODEC.parse(server.getRegistryManager().getOps(JsonOps.INSTANCE), item).getOrThrow();
		}
		ItemStack stack = new ItemStack(Registries.ITEM.get(Identifier.of(item.get("id").getAsString())),
				item.has("count") ? item.get("count").getAsInt() : 1);
		if (item.has("name")) {
			stack.set(DataComponentTypes.CUSTOM_NAME, text(server, item.get("name")));
		}
		if (item.has("lore")) {
			List<Text> lore = new ArrayList<>();
			item.getAsJsonArray("lore").forEach(line -> lore.add(text(server, line)));
			stack.set(DataComponentTypes.LORE, new LoreComponent(lore));
		}
		return stack;
	}

	private static String quote(String s) {
		return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	/** A server-plugin style menu: every click is noted and cancelled, nothing moves. */
	private static final class ReplayMenu extends GenericContainerScreenHandler {
		ReplayMenu(int rows, int syncId, PlayerInventory playerInventory, SimpleInventory inventory) {
			super(ROWS[rows - 1], syncId, playerInventory, inventory, rows);
		}

		@Override
		public void onSlotClick(int slot, int button, SlotActionType action, PlayerEntity player) {
			CLICKS.add(slot + ":" + button + ":" + action);
			syncState();
		}

		@Override
		public ItemStack quickMove(PlayerEntity player, int slot) {
			return ItemStack.EMPTY;
		}
	}
}
