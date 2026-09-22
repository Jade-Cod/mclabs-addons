package dev.jade.labsaddons.mixin;

import dev.jade.labsaddons.coinflip.CfChat;
import dev.jade.labsaddons.coinflip.CfFlipBoard;
import dev.jade.labsaddons.config.LabsAddonsConfig;
import dev.jade.labsaddons.mastery.MasteryKillTracker;
import dev.jade.labsaddons.server.McLabsSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Notices the server taking a menu away, the player clicking a command in chat, and damage
 * the server says the player caused.
 *
 * <p>Coinflip closes its own chest about two seconds after a flip resolves, which is not
 * long enough to read what just happened. This hook is the difference between "the server
 * closed it" and "the player pressed escape": only the former arrives as a packet, and only
 * the former gets the result put back up.
 *
 * <p>Nothing is cancelled. The container closes exactly as it always did; the mod simply
 * keeps a copy of what was on it.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	// At RETURN, not HEAD: every packet handler begins by bouncing itself onto the client
	// thread, and at HEAD this would run on the network thread instead.
	@Inject(method = "handleContainerClose(Lnet/minecraft/network/protocol/game/ClientboundContainerClosePacket;)V",
			at = @At("RETURN"))
	private void labsaddons$keepCoinflipResult(ClientboundContainerClosePacket packet, CallbackInfo ci) {
		if (LabsAddonsConfig.get().coinflipHoldResult) {
			CfFlipBoard.INSTANCE.serverClosedScreen();
		}
	}

	/**
	 * A command run by clicking a chat message. This is the only place to catch one: the
	 * client builds the packet here and hands it straight to the wire, so it never reaches
	 * {@code sendChatCommand} and no send listener fires. Coinflip's broadcast is clickable,
	 * which makes this the ordinary way a flip is taken.
	 *
	 * <p>Called on the client thread, from the click that opened the screen.
	 */
	// ponytail: HEAD, so a command the player is then asked to confirm and declines still
	// counts. Vanilla only asks when the command fails to parse or needs permissions, which
	// a clickable the server itself sent does not, and the cost of being wrong is a wager
	// armed for sixty seconds against a flip nobody took.
	@Inject(method = "sendUnattendedCommand(Ljava/lang/String;Lnet/minecraft/client/gui/screens/Screen;)V",
			at = @At("HEAD"))
	private void labsaddons$noteClickedCommand(String command, Screen screen, CallbackInfo ci) {
		CfChat.onCommandSent(command, Util.getMillis());
	}

	/**
	 * Attributes a hit to the player whatever dealt it.
	 *
	 * <p>{@link MasteryKillTracker} credits a Pit kill only to whoever landed the last blow,
	 * and its one source of attribution was Fabric's melee attack callback — which fires on a
	 * left-click on an entity and nothing else. So a mob killed with a Pit item's ability, a
	 * Fireball Staff's fireball or Stormbreaker's thunder, died unattributed and counted for
	 * nothing.
	 *
	 * <p>This packet carries the server's own answer: {@code sourceCauseId} is the entity it
	 * holds responsible, which for a projectile is whoever fired it rather than the projectile
	 * (that is {@code sourceDirectId}). It is broadcast to every player tracking the victim,
	 * so a hit of ours arrives whether or not we were the one being hurt, and the id is on the
	 * wire even for a positional source like an area effect.
	 *
	 * <p>Additive: the melee callback still runs, so nothing that counted before stops. A
	 * server that names no attacker sends -1 here, which matches no player and credits
	 * nothing, leaving the kill for the next {@code /mastery} scrape exactly as today.
	 */
	// At RETURN for the same reason as onCloseScreen: the handler bounces itself onto the
	// client thread on its first line, so at HEAD this would run on the network thread.
	@Inject(method = "handleDamageEvent(Lnet/minecraft/network/protocol/game/ClientboundDamageEventPacket;)V",
			at = @At("RETURN"))
	private void labsaddons$creditOurOwnDamage(ClientboundDamageEventPacket packet, CallbackInfo ci) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null && packet.sourceCauseId() == player.getId() && McLabsSession.isActive()) {
			MasteryKillTracker.onPlayerHit(packet.entityId());
		}
	}
}
