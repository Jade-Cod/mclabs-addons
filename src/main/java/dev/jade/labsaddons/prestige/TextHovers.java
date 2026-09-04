package dev.jade.labsaddons.prestige;

import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Collects the hover tooltips carried by a chat message.
 *
 * <p>MCLabs puts the exact prestige figures in hovers rather than in the visible
 * line: {@code /prestige progress} shows {@code [||||] Wheatium (0%)} but hovering
 * a row reveals {@code Wheatium: 0/806,400}, and the sell confirmation's visible
 * text names the base chems while its hover gives the precise amount each earned.
 *
 * <p>The client receives the whole {@code Component} tree, hovers included, so this
 * reads them without the player hovering anything — {@code getString()} is simply
 * the wrong accessor, not a missing capability.
 *
 * <p>Every tooltip here is self-describing (each names its own chem), so the
 * visible text it hangs off is not needed and is deliberately not paired up.
 */
public final class TextHovers {
	private TextHovers() {
	}

	/**
	 * Every distinct {@code show_text} tooltip in {@code message}, in order.
	 *
	 * <p>A single hover usually spans several sibling runs, which would otherwise
	 * yield the same tooltip once per run, so duplicates are collapsed. That is safe
	 * here precisely because each tooltip names its own chem and so is unique.
	 */
	public static List<String> tooltips(Component message) {
		if (message == null) {
			return List.of();
		}
		Set<String> found = new LinkedHashSet<>();
		message.visit((style, literal) -> {
			String tooltip = showText(style);
			if (tooltip != null && !tooltip.isBlank()) {
				found.add(tooltip);
			}
			return Optional.empty();
		}, Style.EMPTY);
		return new ArrayList<>(found);
	}

	/** The tooltip text of a {@code show_text} hover, or null for any other (or no) hover. */
	private static String showText(Style style) {
		HoverEvent hover = style.getHoverEvent();
		return hover instanceof HoverEvent.ShowText showText ? showText.value().getString() : null;
	}
}
