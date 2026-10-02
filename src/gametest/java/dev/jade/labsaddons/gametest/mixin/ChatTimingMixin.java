package dev.jade.labsaddons.gametest.mixin;

import dev.jade.labsaddons.LabsAddonsClient;
import dev.jade.labsaddons.gametest.Probe;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Times one chat line through every tracker, including any config saves it triggers. */
@Mixin(value = LabsAddonsClient.class, remap = false)
public abstract class ChatTimingMixin {
	@Inject(method = "dispatchChat(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"))
	private static void labsaddonsTest$begin(Component message, CallbackInfo ci) {
		Probe.CHAT.begin();
	}

	@Inject(method = "dispatchChat(Lnet/minecraft/network/chat/Component;)V", at = @At("RETURN"))
	private static void labsaddonsTest$end(Component message, CallbackInfo ci) {
		Probe.CHAT.end();
	}
}
