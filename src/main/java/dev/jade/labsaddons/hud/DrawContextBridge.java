package dev.jade.labsaddons.hud;

import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;

/**
 * Lets a board hand the GUI a render element of its own, which {@code DrawContext} has no
 * public method for.
 *
 * <p>Implemented on {@code DrawContext} by {@code DrawContextMixin}. Two things are drawn
 * through it — the Double² wheel and the crate chamber's glow — because both are hundreds of
 * primitives that {@code fill()} would charge a whole render-state object apiece for. Go
 * through {@link GuiElements#submit} rather than casting to this directly, so a context that
 * somehow is not the real one is reported rather than silently dropped.
 */
public interface DrawContextBridge {
	void labsaddons$addSimpleElement(SimpleGuiElementRenderState element);
}
