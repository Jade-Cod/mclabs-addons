package dev.jade.labsaddons.gametest;

import dev.jade.labsaddons.server.McLabsSession;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;

import java.util.List;

/** The world the performance test plays in, and the chat it feeds the mod. */
final class TestWorld {
	private TestWorld() {
	}

	static TestSingleplayerContext open(ClientGameTestContext context) {
		TestSingleplayerContext world = context.worldBuilder().create();
		world.getConnection().waitForChunksRender();
		return world;
	}

	/** A sidebar titled MCLabs is what the mod takes as being on the server. */
	static void enterMcLabs(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runCommand("scoreboard objectives add labs dummy \"MCLabs\"");
		world.getServer().runCommand("scoreboard objectives setdisplay sidebar labs");
		context.waitFor(client -> McLabsSession.isActive());
	}

	/** Lines as the server's own system chat, through the same handler a real one takes. */
	static void chat(ClientGameTestContext context, List<String> lines) {
		context.runOnClient(client -> {
			for (String line : lines) {
				client.getConnection().handleSystemChat(
						new ClientboundSystemChatPacket(Component.literal(line), false));
			}
		});
	}
}
