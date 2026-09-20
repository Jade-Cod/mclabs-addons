package dev.jade.labsaddons.mixin;

import dev.jade.labsaddons.coinflip.CfFlipBoard;
import dev.jade.labsaddons.config.LabsAddonsConfig;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.CloseScreenS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Notices the server taking a menu away.
 *
 * <p>Coinflip closes its own chest about two seconds after a flip resolves, which is not
 * long enough to read what just happened. This hook is the difference between "the server
 * closed it" and "the player pressed escape": only the former arrives as a packet, and only
 * the former gets the result put back up.
 *
 * <p>Nothing is cancelled. The container closes exactly as it always did; the mod simply
 * keeps a copy of what was on it.
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
	// At RETURN, not HEAD: every packet handler begins by bouncing itself onto the client
	// thread, and at HEAD this would run on the network thread instead.
	@Inject(method = "onCloseScreen(Lnet/minecraft/network/packet/s2c/play/CloseScreenS2CPacket;)V",
			at = @At("RETURN"))
	private void labsaddons$keepCoinflipResult(CloseScreenS2CPacket packet, CallbackInfo ci) {
		if (LabsAddonsConfig.get().coinflipHoldResult) {
			CfFlipBoard.INSTANCE.serverClosedScreen();
		}
	}
}
