package dev.jade.fishbite.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Click-to-pick colour editor: R/G/B (and optionally Alpha) sliders, a synced
 * hex field, and a live preview swatch. Applies via callback on Done.
 */
public class ColorPickerScreen extends Screen {
	private static final int ROW_WIDTH = 200;
	private static final int ROW_HEIGHT = 20;
	private static final int ROW_GAP = 24;
	private static final int SWATCH_SIZE = 36;

	private final Screen parent;
	private final boolean withAlpha;
	private final IntConsumer onApply;

	private int color;
	private final List<ChannelSlider> sliders = new ArrayList<>();
	private TextFieldWidget hexField;
	private boolean updatingHex;

	public ColorPickerScreen(Screen parent, Text title, int initialColor,
			boolean withAlpha, IntConsumer onApply) {
		super(title);
		this.parent = parent;
		this.withAlpha = withAlpha;
		this.onApply = onApply;
		this.color = withAlpha ? initialColor : initialColor | 0xFF000000;
	}

	/** One 0–255 channel bound to a byte of the ARGB int. */
	private class ChannelSlider extends SliderWidget {
		private final int shift;

		ChannelSlider(int x, int y, String name, int shift) {
			super(x, y, ROW_WIDTH, ROW_HEIGHT, Text.literal(name), ((color >> shift) & 0xFF) / 255.0);
			this.shift = shift;
			updateMessage();
		}

		private String channelName() {
			return switch (shift) {
				case 24 -> "A";
				case 16 -> "R";
				case 8 -> "G";
				default -> "B";
			};
		}

		int channelValue() {
			return (int) Math.round(value * 255.0);
		}

		void setChannel(int channelValue) {
			this.value = channelValue / 255.0;
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			setMessage(Text.literal(channelName() + ": " + channelValue()));
		}

		@Override
		protected void applyValue() {
			color = (color & ~(0xFF << shift)) | (channelValue() << shift);
			syncHexField();
			updateMessage();
		}
	}

	@Override
	protected void init() {
		sliders.clear();
		int x = this.width / 2 - ROW_WIDTH / 2;
		int y = 50;

		if (withAlpha) {
			sliders.add(new ChannelSlider(x, y, "A", 24));
			y += ROW_GAP;
		}
		sliders.add(new ChannelSlider(x, y, "R", 16));
		sliders.add(new ChannelSlider(x, y + ROW_GAP, "G", 8));
		sliders.add(new ChannelSlider(x, y + ROW_GAP * 2, "B", 0));
		sliders.forEach(this::addDrawableChild);
		y += ROW_GAP * 3;

		hexField = new TextFieldWidget(this.textRenderer, x, y, ROW_WIDTH, ROW_HEIGHT,
				Text.translatable("fishbite.color.hex"));
		hexField.setMaxLength(9);
		hexField.setChangedListener(this::onHexTyped);
		this.addDrawableChild(hexField);
		syncHexField();
		y += ROW_GAP + 4;

		this.addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> {
					onApply.accept(color);
					this.close();
				})
				.dimensions(x, y, ROW_WIDTH / 2 - 2, ROW_HEIGHT).build());
		this.addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, button -> this.close())
				.dimensions(x + ROW_WIDTH / 2 + 2, y, ROW_WIDTH / 2 - 2, ROW_HEIGHT).build());
	}

	private void syncHexField() {
		if (hexField == null || updatingHex) {
			return;
		}
		updatingHex = true;
		hexField.setText(withAlpha
				? String.format("#%08X", color)
				: String.format("#%06X", color & 0xFFFFFF));
		updatingHex = false;
	}

	private void onHexTyped(String text) {
		if (updatingHex) {
			return;
		}
		String hex = text.startsWith("#") ? text.substring(1) : text;
		int expected = withAlpha ? 8 : 6;
		if (hex.length() != expected || !hex.matches("^[0-9a-fA-F]+$")) {
			return;
		}
		try {
			long parsed = Long.parseUnsignedLong(hex, 16);
			color = withAlpha ? (int) parsed : (int) parsed | 0xFF000000;
			updatingHex = true;
			sliders.forEach(slider -> slider.setChannel((color >> slider.shift) & 0xFF));
			updatingHex = false;
		} catch (NumberFormatException ignored) {
			// Incomplete/invalid hex while typing — keep the previous colour.
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFFFF);

		// Preview swatch over a checkerboard so alpha is visible.
		int sx = this.width / 2 + ROW_WIDTH / 2 + 12;
		int sy = 50;
		for (int i = 0; i < SWATCH_SIZE / 6; i++) {
			for (int j = 0; j < SWATCH_SIZE / 6; j++) {
				int check = (i + j) % 2 == 0 ? 0xFF9A9A9A : 0xFF666666;
				context.fill(sx + i * 6, sy + j * 6, sx + i * 6 + 6, sy + j * 6 + 6, check);
			}
		}
		context.fill(sx, sy, sx + SWATCH_SIZE, sy + SWATCH_SIZE, color);
	}

	@Override
	public void close() {
		if (this.client != null) {
			this.client.setScreen(this.parent);
		}
	}
}
