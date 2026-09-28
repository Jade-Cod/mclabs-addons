package dev.jade.labsaddons.gametest;

import dev.jade.labsaddons.server.McLabsSession;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;

import java.util.List;
import org.lwjgl.sdl.SDLKeyboard;

/** What every in-game test needs: a world, an MCLabs session, and a way to talk to it. */
final class TestWorld {
	private TestWorld() {
	}

	/** A fresh flat world with its chunks drawn, so screenshots show the game and not a void. */
	static TestSingleplayerContext open(ClientGameTestContext context) {
		TestSingleplayerContext world = context.worldBuilder().create();
		world.getConnection().waitForChunksRender();
		return world;
	}

	/**
	 * Turns the MCLabs-only half of the mod on the same way the server does: a sidebar whose
	 * title says MCLabs. Everything gated on {@link McLabsSession} wakes up from this.
	 */
	static void enterMcLabs(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runCommand("scoreboard objectives add labs dummy \"MCLabs\"");
		world.getServer().runCommand("scoreboard objectives setdisplay sidebar labs");
		context.waitFor(client -> McLabsSession.isActive());
	}

	/**
	 * Delivers each line as a server system message, through the real packet handler, so it
	 * reaches the mod by the same route a line from MCLabs does.
	 */
	static void chat(ClientGameTestContext context, List<String> lines) {
		context.runOnClient(client -> {
			for (String line : lines) {
				client.getConnection().handleSystemChat(
						new ClientboundSystemChatPacket(Component.literal(line), false));
			}
		});
	}

	/** Moves the cursor to a point in GUI coordinates, which is what screens hit-test in. */
	static void cursorAt(ClientGameTestContext context, double guiX, double guiY) {
		double[] raw = context.computeOnClient(client -> toRaw(client, guiX, guiY));
		context.getInput().setCursorPos(raw[0], raw[1]);
	}

	/**
	 * Presses and releases a key through the game's own keyboard handler, with modifiers.
	 *
	 * <p>{@code TestInput#holdShift()} can't be used for this on 26.3: Fabric's test input
	 * builds every key and mouse event with its modifiers hard-coded to 0, so a held Shift
	 * never reaches a screen. This goes in one step later, at the same vanilla handler.
	 */
	static void key(ClientGameTestContext context, int key, int modifiers) {
		context.runOnClient(client -> {
			long window = client.getWindow().handle();
			KeyEvent event = new KeyEvent(key, SDLKeyboard.SDL_GetKeyFromScancode(key, (short) 0, false), modifiers);
			client.keyboardHandler.keyPress(window, InputConstants.PRESS, event);
			client.keyboardHandler.keyPress(window, InputConstants.RELEASE, event);
		});
	}

	/** A mouse button press or release with modifiers; see {@link #key}. */
	static void mouse(ClientGameTestContext context, int button, int action, int modifiers) {
		context.runOnClient(client -> client.mouseHandler.onButton(
				client.getWindow().handle(), new MouseButtonInfo(button, modifiers), action));
	}

	private static double[] toRaw(Minecraft client, double guiX, double guiY) {
		var window = client.getWindow();
		return new double[]{
				guiX * window.getScreenWidth() / window.getGuiScaledWidth(),
				guiY * window.getScreenHeight() / window.getGuiScaledHeight()};
	}
}
