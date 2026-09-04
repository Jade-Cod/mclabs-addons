package dev.jade.labsaddons.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Base for icon + countdown widgets with an optional caption above and extra
 * lines below: a header (what the timer is for), a row of icon + remaining
 * time, then any number of detail lines (e.g. a reward).
 */
public abstract class LabeledTimerHudObject extends HudObject {
	protected static final int ICON_SIZE = 16;
	protected static final int ICON_GAP = 4;
	protected static final int LINE_GAP = 2;

	/** Caption drawn above the timer, or null for none. */
	@Nullable
	protected abstract Component header(boolean preview);

	/** Icon drawn left of the time, or null for none. */
	@Nullable
	protected abstract ItemStack icon(boolean preview);

	protected abstract String timeText(boolean preview);

	/** Extra detail lines drawn below the timer (e.g. reward). */
	protected List<Component> extraLines(boolean preview) {
		return List.of();
	}

	private static Font font() {
		return Minecraft.getInstance().font;
	}

	private int rowHeight() {
		return Math.max(ICON_SIZE, font().lineHeight);
	}

	@Override
	public int contentWidth(boolean preview) {
		Font font = font();
		int width = 0;
		Component header = header(preview);
		if (header != null) {
			width = font.width(header);
		}
		int iconWidth = icon(preview) != null ? ICON_SIZE + ICON_GAP : 0;
		width = Math.max(width, iconWidth + font.width(timeText(preview)));
		for (Component line : extraLines(preview)) {
			width = Math.max(width, font.width(line));
		}
		return width;
	}

	@Override
	public int contentHeight(boolean preview) {
		int height = 0;
		if (header(preview) != null) {
			height += font().lineHeight + LINE_GAP;
		}
		height += rowHeight();
		List<Component> extra = extraLines(preview);
		if (!extra.isEmpty()) {
			height += extra.size() * (font().lineHeight + LINE_GAP);
		}
		return height;
	}

	@Override
	protected void renderContent(GuiGraphicsExtractor context, boolean preview) {
		Font font = font();
		int color = settings().textColor | 0xFF000000;
		int y = 0;

		Component header = header(preview);
		if (header != null) {
			context.text(font, header, 0, y, color, true);
			y += font.lineHeight + LINE_GAP;
		}

		ItemStack icon = icon(preview);
		int textX = 0;
		if (icon != null) {
			context.item(icon, 0, y + (rowHeight() - ICON_SIZE) / 2);
			textX = ICON_SIZE + ICON_GAP;
		}
		context.text(font, Component.literal(timeText(preview)),
				textX, y + (rowHeight() - font.lineHeight) / 2 + 1, color, true);
		y += rowHeight();

		for (Component line : extraLines(preview)) {
			y += LINE_GAP;
			context.text(font, line, 0, y, color, true);
			y += font.lineHeight;
		}
	}

	/** Convenience for subclasses building extra lines. */
	protected static List<Component> lines(Component... values) {
		return new ArrayList<>(List.of(values));
	}
}
