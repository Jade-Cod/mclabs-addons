package dev.jade.labsaddons.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The one way into {@link DrawContextBridge}, so a missing mixin is heard about. */
public final class GuiElements {
	private static final Logger LOGGER = LoggerFactory.getLogger("labsaddons");
	private static boolean warned;

	private GuiElements() {
	}

	/**
	 * Adds an element to the GUI's own render list.
	 *
	 * <p>Says so once if it cannot. Everything drawn this way has the rest of its board drawn
	 * around it the ordinary way, so a failure here is not a blank screen — it is a wheel with
	 * no segments under its letters, or a candidate with no light behind it, which reads as a
	 * graphical fault rather than as a mod that did not load. Worth a line in the log to save
	 * anyone chasing it as the former.
	 *
	 * @return whether the element was taken
	 */
	public static boolean submit(DrawContext context, SimpleGuiElementRenderState element) {
		if (context instanceof DrawContextBridge bridge) {
			bridge.labsaddons$addSimpleElement(element);
			return true;
		}
		if (!warned) {
			warned = true;
			LOGGER.warn("[labsaddons] DrawContext mixin not applied: the Double² wheel and "
					+ "the crate glow cannot draw. Every other widget is unaffected.");
		}
		return false;
	}
}
