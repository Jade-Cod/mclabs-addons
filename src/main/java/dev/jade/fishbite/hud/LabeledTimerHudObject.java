package dev.jade.fishbite.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
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
	protected abstract Text header(boolean preview);

	/** Icon drawn left of the time, or null for none. */
	@Nullable
	protected abstract ItemStack icon(boolean preview);

	protected abstract String timeText(boolean preview);

	/** Extra detail lines drawn below the timer (e.g. reward). */
	protected List<Text> extraLines(boolean preview) {
		return List.of();
	}

	private static TextRenderer font() {
		return MinecraftClient.getInstance().textRenderer;
	}

	private int rowHeight() {
		return Math.max(ICON_SIZE, font().fontHeight);
	}

	@Override
	public int contentWidth(boolean preview) {
		TextRenderer font = font();
		int width = 0;
		Text header = header(preview);
		if (header != null) {
			width = font.getWidth(header);
		}
		int iconWidth = icon(preview) != null ? ICON_SIZE + ICON_GAP : 0;
		width = Math.max(width, iconWidth + font.getWidth(timeText(preview)));
		for (Text line : extraLines(preview)) {
			width = Math.max(width, font.getWidth(line));
		}
		return width;
	}

	@Override
	public int contentHeight(boolean preview) {
		int height = 0;
		if (header(preview) != null) {
			height += font().fontHeight + LINE_GAP;
		}
		height += rowHeight();
		List<Text> extra = extraLines(preview);
		if (!extra.isEmpty()) {
			height += extra.size() * (font().fontHeight + LINE_GAP);
		}
		return height;
	}

	@Override
	protected void renderContent(DrawContext context, boolean preview) {
		TextRenderer font = font();
		int color = settings().textColor | 0xFF000000;
		int y = 0;

		Text header = header(preview);
		if (header != null) {
			context.drawText(font, header, 0, y, color, true);
			y += font.fontHeight + LINE_GAP;
		}

		ItemStack icon = icon(preview);
		int textX = 0;
		if (icon != null) {
			context.drawItem(icon, 0, y + (rowHeight() - ICON_SIZE) / 2);
			textX = ICON_SIZE + ICON_GAP;
		}
		context.drawText(font, Text.literal(timeText(preview)),
				textX, y + (rowHeight() - font.fontHeight) / 2 + 1, color, true);
		y += rowHeight();

		for (Text line : extraLines(preview)) {
			y += LINE_GAP;
			context.drawText(font, line, 0, y, color, true);
			y += font.fontHeight;
		}
	}

	/** Convenience for subclasses building extra lines. */
	protected static List<Text> lines(Text... values) {
		return new ArrayList<>(List.of(values));
	}
}
