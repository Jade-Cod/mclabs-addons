package dev.jade.labsaddons.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes {@code TextDisplay.getText()}, which is private here. The Raid Mine
 * shows what a broken block generated as a floating text display rather than
 * dropping items, so reading that text is the only way to see the gain.
 *
 * <p>Needed only on this branch: the same method is accessible on 1.21.11, which
 * calls it directly.
 */
@Mixin(Display.TextDisplay.class)
public interface TextDisplayInvoker {
	@Invoker("getText")
	Component labsaddons$getText();
}
