package dev.jade.fishbite.hud;

import dev.jade.fishbite.config.FishBiteConfig;
import dev.jade.fishbite.hud.editor.EditorPainter;
import dev.jade.fishbite.hud.editor.EditorTheme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * "HUD Studio": a polished, intuitive HUD layout editor. Widgets are shown in
 * place over the live (dimmed) game. Click to select, Shift-click or drag a
 * marquee to select several, and drag any selected widget to move the whole group
 * (magnetic guides + optional grid). Drag the edge/corner handles to resize a
 * single widget like a window — resizing snaps to 25% steps with free movement
 * between. A single selection gets a docked inspector (visibility, colours, reset)
 * that auto-docks opposite the widget; a "Widgets" rail lists every widget. Arrow
 * keys nudge the selection (Shift = 10px); Alt bypasses snapping.
 */
public class HudEditScreen extends Screen {
	private static final int SNAP_THRESHOLD = 6;
	private static final int EDGE = 2;
	private static final int MARQUEE_THRESHOLD = 3;
	private static final float SCALE_STEP = 0.25f;
	private static final float SCALE_SNAP_TOLERANCE = 0.04f;

	/** Resize-handle roles: {signX, signY} in {-1,0,1}, excluding the centre. */
	private static final int[][] HANDLE_SPECS = {
			{-1, -1}, {0, -1}, {1, -1},
			{-1, 0}, {1, 0},
			{-1, 1}, {0, 1}, {1, 1},
	};

	private final Screen parent;

	private final Set<HudObject> selection = new LinkedHashSet<>();

	// Group move (drag the body of any selected widget).
	private boolean groupDragging;
	private List<HudObject> dragWidgets;
	private List<int[]> dragOrigins;
	private int grabOriginX;
	private int grabOriginY;
	private int grabWidth;
	private int grabHeight;
	private int startCursorX;
	private int startCursorY;
	private int groupMinX;
	private int groupMinY;
	private int groupMaxX;
	private int groupMaxY;

	// Resize (single selection only).
	private boolean resizing;
	private int resizeSx;
	private int resizeSy;
	private int resizeAnchorX;
	private int resizeAnchorY;

	// Marquee (rubber-band) select.
	private boolean marqueeing;
	private boolean marqueeMoved;
	private boolean marqueeAdditive;
	private int marqueeStartX;
	private int marqueeStartY;
	private int marqueeCurX;
	private int marqueeCurY;
	private Set<HudObject> marqueeBase;

	private Integer guideX;
	private Integer guideY;
	private boolean snapEnabled = true;
	private boolean gridEnabled;

	// Inspector layout, recomputed in init() whenever the selection changes.
	private int[] panel;
	private int nameY;
	private int textSwatchX;
	private int textSwatchY;
	private int bgSwatchX;
	private int bgSwatchY;

	public HudEditScreen(Screen parent) {
		super(Text.translatable("fishbite.hud.editor.title"));
		this.parent = parent;
	}

	private HudObject singleSelected() {
		return selection.size() == 1 ? selection.iterator().next() : null;
	}

	// --- lifecycle ------------------------------------------------------------

	@Override
	protected void init() {
		this.panel = null;
		buildToolbar();
		HudObject one = singleSelected();
		if (one != null) {
			buildInspector(one);
		}
	}

	private void buildToolbar() {
		int y = this.height - EditorTheme.TOOLBAR_H + (EditorTheme.TOOLBAR_H - 20) / 2;
		int x = EditorTheme.MARGIN;
		this.addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> this.close())
				.dimensions(x, y, 48, 20).build());
		x += 52;
		this.addDrawableChild(ButtonWidget.builder(
						Text.translatable("fishbite.hud.editor.open_config"), b -> openMainConfig())
				.dimensions(x, y, 96, 20).build());
		x += 100;
		this.addDrawableChild(ButtonWidget.builder(
						Text.translatable("fishbite.hud.editor.reset_all"), b -> resetAll())
				.dimensions(x, y, 78, 20)
				.tooltip(Tooltip.of(Text.translatable("fishbite.hud.editor.reset_all.tooltip"))).build());

		int rx = this.width - EditorTheme.MARGIN - 64;
		this.addDrawableChild(ButtonWidget.builder(snapLabel(), b -> {
					snapEnabled = !snapEnabled;
					b.setMessage(snapLabel());
				}).dimensions(rx, y, 64, 20)
				.tooltip(Tooltip.of(Text.translatable("fishbite.hud.editor.snap.tooltip"))).build());
		rx -= 68;
		this.addDrawableChild(ButtonWidget.builder(gridLabel(), b -> {
					gridEnabled = !gridEnabled;
					b.setMessage(gridLabel());
				}).dimensions(rx, y, 64, 20)
				.tooltip(Tooltip.of(Text.translatable("fishbite.hud.editor.grid.tooltip"))).build());
	}

	private Text snapLabel() {
		return Text.translatable("fishbite.hud.editor.snap").append(": ").append(ScreenTexts.onOrOff(snapEnabled));
	}

	private Text gridLabel() {
		return Text.translatable("fishbite.hud.editor.grid").append(": ").append(ScreenTexts.onOrOff(gridEnabled));
	}

	private void buildInspector(HudObject widget) {
		HudObjectSettings s = widget.settings();
		int innerW = EditorTheme.PANEL_W - 2 * EditorTheme.PAD;
		boolean hasAction = widget.editorAction() != null;

		int rowStep = EditorTheme.ROW + EditorTheme.GAP;
		int contentH = EditorTheme.NAME_H
				+ rowStep * 4
				+ (hasAction ? rowStep : 0)
				+ EditorTheme.ROW;
		int panelH = contentH + 2 * EditorTheme.PAD;

		int panelX = dockLeft(widget)
				? EditorTheme.MARGIN + EditorTheme.RAIL_W + EditorTheme.GAP
				: this.width - EditorTheme.PANEL_W - EditorTheme.MARGIN;
		int toolbarTop = this.height - EditorTheme.TOOLBAR_H - EditorTheme.GAP;
		int panelY = EditorTheme.TOP;
		if (panelY + panelH > toolbarTop) {
			panelY = Math.max(2, toolbarTop - panelH);
		}
		this.panel = new int[]{panelX, panelY, EditorTheme.PANEL_W, panelH};

		int innerX = panelX + EditorTheme.PAD;
		int y = panelY + EditorTheme.PAD;

		this.nameY = y;
		y += EditorTheme.NAME_H;

		this.addDrawableChild(CyclingButtonWidget.onOffBuilder(s.enabled).build(
				innerX, y, innerW, EditorTheme.ROW,
				Text.translatable("fishbite.config.hud.enabled"),
				(b, v) -> s.enabled = v));
		y += rowStep;

		this.addDrawableChild(ButtonWidget.builder(
						Text.translatable("fishbite.config.hud.text_color"), b -> openPicker(widget, false))
				.dimensions(innerX, y, innerW - EditorTheme.SWATCH - EditorTheme.GAP, EditorTheme.ROW).build());
		this.textSwatchX = innerX + innerW - EditorTheme.SWATCH;
		this.textSwatchY = y;
		y += rowStep;

		this.addDrawableChild(CyclingButtonWidget.onOffBuilder(s.backgroundEnabled).build(
				innerX, y, innerW, EditorTheme.ROW,
				Text.translatable("fishbite.config.hud.background"),
				(b, v) -> s.backgroundEnabled = v));
		y += rowStep;

		this.addDrawableChild(ButtonWidget.builder(
						Text.translatable("fishbite.config.hud.background_color"), b -> openPicker(widget, true))
				.dimensions(innerX, y, innerW - EditorTheme.SWATCH - EditorTheme.GAP, EditorTheme.ROW).build());
		this.bgSwatchX = innerX + innerW - EditorTheme.SWATCH;
		this.bgSwatchY = y;
		y += rowStep;

		HudObject.EditorAction action = widget.editorAction();
		if (action != null) {
			this.addDrawableChild(ButtonWidget.builder(action.label(), b -> action.action().run())
					.dimensions(innerX, y, innerW, EditorTheme.ROW).build());
			y += rowStep;
		}

		this.addDrawableChild(ButtonWidget.builder(
						Text.translatable("fishbite.hud.editor.reset_widget"), b -> {
							widget.settings().resetTo(widget.defaultSettings());
							clearAndInit();
						})
				.dimensions(innerX, y, innerW, EditorTheme.ROW).build());
	}

	/** Dock the inspector on the left when the selected widget sits on the right half. */
	private boolean dockLeft(HudObject widget) {
		int[] b = widget.screenBounds(this.width, this.height, true);
		return (b[0] + b[2] / 2) > this.width / 2;
	}

	private void openPicker(HudObject widget, boolean background) {
		if (this.client == null) {
			return;
		}
		HudObjectSettings s = widget.settings();
		if (background) {
			this.client.setScreen(new ColorPickerScreen(this,
					Text.translatable("fishbite.config.hud.background_color"),
					s.backgroundColor, true, color -> s.backgroundColor = color));
		} else {
			this.client.setScreen(new ColorPickerScreen(this,
					Text.translatable("fishbite.config.hud.text_color"),
					s.textColor, false, color -> s.textColor = 0xFF000000 | (color & 0xFFFFFF)));
		}
	}

	private void openMainConfig() {
		MinecraftClient.getInstance().setScreen(
				dev.jade.fishbite.config.FishBiteConfigScreenFactory.create(this));
	}

	private void resetAll() {
		for (HudObject obj : HudObjects.all()) {
			obj.settings().resetTo(obj.defaultSettings());
		}
		selection.clear();
		clearAndInit();
	}

	private void selectOnly(HudObject obj) {
		selection.clear();
		selection.add(obj);
		clearAndInit();
	}

	private void toggleSelection(HudObject obj) {
		if (!selection.remove(obj)) {
			selection.add(obj);
		}
		clearAndInit();
	}

	// --- rendering ------------------------------------------------------------

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		// Custom light dim is drawn in render() so the live game stays visible;
		// suppress the vanilla blur/dirt background entirely.
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, this.width, this.height, EditorTheme.DIM);
		if (gridEnabled) {
			EditorPainter.gridOverlay(context, this.width, this.height, EditorTheme.GRID_STEP, EditorTheme.GRID);
		}

		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, EditorTheme.TITLE);
		context.drawCenteredTextWithShadow(this.textRenderer,
				Text.translatable("fishbite.hud.editor.hint"), this.width / 2, 24, EditorTheme.TEXT_DIM);

		HudObject one = singleSelected();
		for (HudObject obj : HudObjects.all()) {
			obj.render(context, true);
			int[] b = obj.screenBounds(this.width, this.height, true);
			boolean isSelected = selection.contains(obj);
			boolean hovered = !isSelected && contains(b, mouseX, mouseY) && !overUi(mouseX, mouseY);
			if (isSelected) {
				EditorPainter.outline(context, b[0] - 1, b[1] - 1, b[2] + 2, b[3] + 2, EditorTheme.accentPulse());
				if (obj == one) {
					drawResizeHandles(context, b);
				}
			} else if (hovered) {
				EditorPainter.outline(context, b[0] - 1, b[1] - 1, b[2] + 2, b[3] + 2, EditorTheme.HOVER_OUTLINE);
			}
			if (obj == one && resizing) {
				int pct = Math.round(obj.settings().scale * 100);
				context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(pct + "%"),
						b[0] + b[2] / 2, Math.max(2, b[1] - 12), EditorTheme.TEXT_ACCENT);
			} else if (isSelected || hovered) {
				drawNameChip(context, obj, b);
			}
		}

		if (guideX != null) {
			context.fill(guideX, 0, guideX + 1, this.height, EditorTheme.GUIDE);
		}
		if (guideY != null) {
			context.fill(0, guideY, this.width, guideY + 1, EditorTheme.GUIDE);
		}

		if (marqueeing && marqueeMoved) {
			int[] r = marqueeRect();
			context.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], EditorTheme.ROW_SELECTED);
			EditorPainter.outline(context, r[0], r[1], r[2], r[3], EditorTheme.GUIDE);
		}

		drawRail(context, mouseX, mouseY);
		if (one != null && panel != null) {
			drawInspector(context, one);
		} else if (selection.size() > 1) {
			context.drawCenteredTextWithShadow(this.textRenderer,
					Text.translatable("fishbite.hud.editor.multi_hint", selection.size()),
					this.width / 2, this.height - EditorTheme.TOOLBAR_H - 12, EditorTheme.TEXT_ACCENT);
		}

		context.fill(0, this.height - EditorTheme.TOOLBAR_H, this.width, this.height, EditorTheme.TOOLBAR_BG);
		context.drawCenteredTextWithShadow(this.textRenderer,
				Text.translatable("fishbite.hud.editor.nudge_hint"),
				this.width / 2, this.height - EditorTheme.TOOLBAR_H / 2 - 4, EditorTheme.TEXT_DIM);

		super.render(context, mouseX, mouseY, delta);
	}

	private void drawResizeHandles(DrawContext context, int[] b) {
		for (int[] spec : HANDLE_SPECS) {
			int cx = b[0] + (spec[0] + 1) * b[2] / 2;
			int cy = b[1] + (spec[1] + 1) * b[3] / 2;
			EditorPainter.resizeHandle(context, cx, cy, EditorTheme.HANDLE_SZ,
					EditorTheme.HANDLE, EditorTheme.PANEL_BG);
		}
	}

	private void drawNameChip(DrawContext context, HudObject obj, int[] b) {
		boolean enabled = obj.settings().enabled;
		Text label = enabled ? obj.displayName()
				: Text.translatable("fishbite.hud.editor.hidden_suffix", obj.displayName());
		int color = enabled ? EditorTheme.TEXT : EditorTheme.TEXT_HIDDEN;
		boolean above = b[1] - (this.textRenderer.fontHeight + 4) >= EditorTheme.TOP + 4;
		int chipY = above ? b[1] - this.textRenderer.fontHeight - 3 : b[1] + b[3] + 3;
		int chipX = MathHelper.clamp(b[0], EditorTheme.MARGIN,
				this.width - EditorTheme.MARGIN - this.textRenderer.getWidth(label));
		EditorPainter.nameChip(context, this.textRenderer, label, chipX, chipY, color);
	}

	private void drawRail(DrawContext context, int mouseX, int mouseY) {
		int[] r = railRect();
		EditorPainter.panel(context, r, EditorTheme.PANEL_BG, EditorTheme.PANEL_BORDER);
		context.drawText(this.textRenderer, Text.translatable("fishbite.hud.editor.layers"),
				r[0] + 5, r[1] + 3, EditorTheme.TEXT_ACCENT, false);

		List<HudObject> objects = HudObjects.all();
		for (int i = 0; i < objects.size(); i++) {
			HudObject obj = objects.get(i);
			int[] row = layerRowRect(i);
			if (selection.contains(obj)) {
				context.fill(row[0], row[1], row[0] + row[2], row[1] + row[3], EditorTheme.ROW_SELECTED);
			} else if (contains(row, mouseX, mouseY)) {
				context.fill(row[0], row[1], row[0] + row[2], row[1] + row[3], EditorTheme.ROW_HOVER);
			}

			boolean enabled = obj.settings().enabled;
			int textMaxW = row[2] - EditorTheme.RAIL_TOGGLE - 10;
			String name = this.textRenderer.trimToWidth(obj.displayName().getString(), textMaxW);
			context.drawText(this.textRenderer, Text.literal(name), row[0] + 4,
					row[1] + (row[3] - this.textRenderer.fontHeight) / 2 + 1,
					enabled ? EditorTheme.TEXT : EditorTheme.TEXT_HIDDEN, false);

			int[] tb = toggleRect(row);
			if (enabled) {
				context.fill(tb[0], tb[1], tb[0] + tb[2], tb[1] + tb[3], EditorTheme.TOGGLE_ON);
			} else {
				EditorPainter.outline(context, tb[0], tb[1], tb[2], tb[3], EditorTheme.TOGGLE_OFF);
			}
		}
	}

	private void drawInspector(DrawContext context, HudObject widget) {
		HudObjectSettings s = widget.settings();
		EditorPainter.panel(context, panel, EditorTheme.PANEL_BG, EditorTheme.PANEL_BORDER);
		int innerX = panel[0] + EditorTheme.PAD;
		int innerW = panel[2] - 2 * EditorTheme.PAD;

		String name = this.textRenderer.trimToWidth(widget.displayName().getString(), innerW);
		context.drawText(this.textRenderer, Text.literal(name), innerX, nameY, EditorTheme.TEXT, false);

		EditorPainter.swatch(context, textSwatchX, textSwatchY, EditorTheme.SWATCH, s.textColor | 0xFF000000);
		EditorPainter.swatch(context, bgSwatchX, bgSwatchY, EditorTheme.SWATCH, s.backgroundColor);
	}

	// --- geometry -------------------------------------------------------------

	private int[] railRect() {
		int count = HudObjects.all().size();
		int h = EditorTheme.RAIL_HEADER_H + count * EditorTheme.RAIL_ROW_H + EditorTheme.PAD;
		int y = EditorTheme.TOP;
		int toolbarTop = this.height - EditorTheme.TOOLBAR_H - EditorTheme.GAP;
		if (y + h > toolbarTop) {
			y = Math.max(2, toolbarTop - h);
		}
		return new int[]{EditorTheme.MARGIN, y, EditorTheme.RAIL_W, h};
	}

	private int[] layerRowRect(int index) {
		int[] r = railRect();
		int rowY = r[1] + EditorTheme.RAIL_HEADER_H + index * EditorTheme.RAIL_ROW_H;
		return new int[]{r[0] + 2, rowY, r[2] - 4, EditorTheme.RAIL_ROW_H};
	}

	private int[] toggleRect(int[] row) {
		int tb = EditorTheme.RAIL_TOGGLE;
		return new int[]{row[0] + row[2] - tb - 3, row[1] + (row[3] - tb) / 2, tb, tb};
	}

	private int[] marqueeRect() {
		int x = Math.min(marqueeStartX, marqueeCurX);
		int y = Math.min(marqueeStartY, marqueeCurY);
		int w = Math.abs(marqueeCurX - marqueeStartX);
		int h = Math.abs(marqueeCurY - marqueeStartY);
		return new int[]{x, y, w, h};
	}

	/** Resize handle under the cursor for {@code widget} (index into HANDLE_SPECS), or -1. */
	private int handleAt(HudObject widget, double mx, double my) {
		int[] b = widget.screenBounds(this.width, this.height, true);
		int hit = EditorTheme.HANDLE_SZ + 2;
		for (int i = 0; i < HANDLE_SPECS.length; i++) {
			int cx = b[0] + (HANDLE_SPECS[i][0] + 1) * b[2] / 2;
			int cy = b[1] + (HANDLE_SPECS[i][1] + 1) * b[3] / 2;
			if (mx >= cx - hit / 2.0 && mx <= cx + hit / 2.0 && my >= cy - hit / 2.0 && my <= cy + hit / 2.0) {
				return i;
			}
		}
		return -1;
	}

	private boolean overUi(double mx, double my) {
		return contains(railRect(), mx, my)
				|| (panel != null && contains(panel, mx, my))
				|| my >= this.height - EditorTheme.TOOLBAR_H;
	}

	private static boolean contains(int[] rect, double x, double y) {
		return x >= rect[0] && x <= rect[0] + rect[2] && y >= rect[1] && y <= rect[1] + rect[3];
	}

	private static boolean intersects(int[] b, int[] r) {
		return !(b[0] + b[2] < r[0] || b[0] > r[0] + r[2] || b[1] + b[3] < r[1] || b[1] > r[1] + r[3]);
	}

	// --- selection / drag start ----------------------------------------------

	private void startResize(HudObject widget, int handle) {
		int[] b = widget.screenBounds(this.width, this.height, true);
		resizeSx = HANDLE_SPECS[handle][0];
		resizeSy = HANDLE_SPECS[handle][1];
		resizeAnchorX = resizeSx > 0 ? b[0] : resizeSx < 0 ? b[0] + b[2] : b[0];
		resizeAnchorY = resizeSy > 0 ? b[1] : resizeSy < 0 ? b[1] + b[3] : b[1];
		resizing = true;
	}

	private void startGroupDrag(HudObject grab, double mx, double my) {
		dragWidgets = new ArrayList<>(selection);
		dragOrigins = new ArrayList<>(dragWidgets.size());
		groupMinX = Integer.MAX_VALUE;
		groupMinY = Integer.MAX_VALUE;
		groupMaxX = Integer.MIN_VALUE;
		groupMaxY = Integer.MIN_VALUE;
		for (HudObject w : dragWidgets) {
			int[] b = w.screenBounds(this.width, this.height, true);
			dragOrigins.add(new int[]{b[0], b[1]});
			groupMinX = Math.min(groupMinX, b[0]);
			groupMinY = Math.min(groupMinY, b[1]);
			groupMaxX = Math.max(groupMaxX, b[0] + b[2]);
			groupMaxY = Math.max(groupMaxY, b[1] + b[3]);
			if (w == grab) {
				grabOriginX = b[0];
				grabOriginY = b[1];
				grabWidth = b[2];
				grabHeight = b[3];
			}
		}
		startCursorX = (int) Math.round(mx);
		startCursorY = (int) Math.round(my);
		groupDragging = true;
	}

	private void startMarquee(double mx, double my, boolean additive) {
		marqueeing = true;
		marqueeMoved = false;
		marqueeAdditive = additive;
		marqueeStartX = (int) Math.round(mx);
		marqueeStartY = (int) Math.round(my);
		marqueeCurX = marqueeStartX;
		marqueeCurY = marqueeStartY;
		marqueeBase = additive ? new LinkedHashSet<>(selection) : null;
	}

	// --- input ----------------------------------------------------------------

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		if (super.mouseClicked(click, doubled)) {
			return true;
		}
		double mx = click.x();
		double my = click.y();
		boolean shift = (click.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;

		HudObject one = singleSelected();
		if (one != null) {
			int handle = handleAt(one, mx, my);
			if (handle >= 0) {
				startResize(one, handle);
				return true;
			}
			if (panel != null && contains(panel, mx, my)) {
				return true;
			}
		}

		List<HudObject> objects = HudObjects.all();
		for (int i = 0; i < objects.size(); i++) {
			int[] row = layerRowRect(i);
			if (contains(row, mx, my)) {
				HudObject obj = objects.get(i);
				int[] tb = toggleRect(row);
				if (contains(tb, mx, my)) {
					HudObjectSettings settings = obj.settings();
					settings.enabled = !settings.enabled;
					if (selection.contains(obj)) {
						clearAndInit();
					}
				} else if (shift) {
					toggleSelection(obj);
				} else {
					selectOnly(obj);
				}
				return true;
			}
		}
		if (contains(railRect(), mx, my)) {
			return true;
		}

		for (int i = objects.size() - 1; i >= 0; i--) {
			HudObject obj = objects.get(i);
			int[] b = obj.screenBounds(this.width, this.height, true);
			if (contains(b, mx, my) && !overUi(mx, my)) {
				if (shift) {
					toggleSelection(obj);
					return true;
				}
				if (!selection.contains(obj)) {
					selectOnly(obj);
				}
				startGroupDrag(obj, mx, my);
				return true;
			}
		}

		if (overUi(mx, my)) {
			return false;
		}
		startMarquee(mx, my, shift);
		return true;
	}

	@Override
	public boolean mouseDragged(Click click, double offsetX, double offsetY) {
		boolean snap = snapEnabled && (click.modifiers() & GLFW.GLFW_MOD_ALT) == 0;
		if (resizing) {
			doResize(click.x(), click.y(), snap);
			return true;
		}
		if (groupDragging) {
			doGroupMove((int) Math.round(click.x()), (int) Math.round(click.y()), snap);
			return true;
		}
		if (marqueeing) {
			updateMarquee((int) Math.round(click.x()), (int) Math.round(click.y()));
			return true;
		}
		return super.mouseDragged(click, offsetX, offsetY);
	}

	/** Aspect-locked resize that keeps the opposite corner/edge fixed; snaps scale to 25% steps. */
	private void doResize(double mx, double my, boolean snap) {
		HudObject widget = singleSelected();
		if (widget == null) {
			return;
		}
		float contentW = widget.contentWidth(true) + HudObject.PADDING * 2.0f;
		float contentH = widget.contentHeight(true) + HudObject.PADDING * 2.0f;
		float scale;
		if (resizeSx != 0 && resizeSy != 0) {
			double dirX = resizeSx * contentW;
			double dirY = resizeSy * contentH;
			double t = ((mx - resizeAnchorX) * dirX + (my - resizeAnchorY) * dirY) / (dirX * dirX + dirY * dirY);
			scale = (float) t;
		} else if (resizeSx != 0) {
			scale = (float) (Math.abs(mx - resizeAnchorX) / contentW);
		} else {
			scale = (float) (Math.abs(my - resizeAnchorY) / contentH);
		}
		scale = MathHelper.clamp(scale, HudObjectSettings.MIN_SCALE, HudObjectSettings.MAX_SCALE);
		if (snap) {
			float nearest = MathHelper.clamp(Math.round(scale / SCALE_STEP) * SCALE_STEP,
					HudObjectSettings.MIN_SCALE, HudObjectSettings.MAX_SCALE);
			if (Math.abs(scale - nearest) <= SCALE_SNAP_TOLERANCE) {
				scale = nearest;
			}
		}
		widget.settings().scale = scale;

		int w = Math.round(contentW * scale);
		int h = Math.round(contentH * scale);
		int newX = resizeSx > 0 ? resizeAnchorX : resizeSx < 0 ? resizeAnchorX - w : resizeAnchorX;
		int newY = resizeSy > 0 ? resizeAnchorY : resizeSy < 0 ? resizeAnchorY - h : resizeAnchorY;
		widget.setScreenBoxPosition(newX, newY, this.width, this.height);
	}

	/** Moves the whole selection rigidly, snapping the grabbed widget and clamping to the screen. */
	private void doGroupMove(int cursorX, int cursorY, boolean snap) {
		int rawX = grabOriginX + (cursorX - startCursorX);
		int rawY = grabOriginY + (cursorY - startCursorY);
		guideX = null;
		guideY = null;
		if (snap) {
			rawX = snapAxis(rawX, grabWidth, snapTargets(true), true);
			rawY = snapAxis(rawY, grabHeight, snapTargets(false), false);
		}
		int dx = MathHelper.clamp(rawX - grabOriginX, -groupMinX, this.width - groupMaxX);
		int dy = MathHelper.clamp(rawY - grabOriginY, -groupMinY, this.height - groupMaxY);
		for (int i = 0; i < dragWidgets.size(); i++) {
			int[] o = dragOrigins.get(i);
			dragWidgets.get(i).setScreenBoxPosition(o[0] + dx, o[1] + dy, this.width, this.height);
		}
	}

	private void updateMarquee(int cursorX, int cursorY) {
		marqueeCurX = cursorX;
		marqueeCurY = cursorY;
		if (Math.abs(cursorX - marqueeStartX) > MARQUEE_THRESHOLD
				|| Math.abs(cursorY - marqueeStartY) > MARQUEE_THRESHOLD) {
			marqueeMoved = true;
		}
		if (!marqueeMoved) {
			return;
		}
		selection.clear();
		if (marqueeAdditive && marqueeBase != null) {
			selection.addAll(marqueeBase);
		}
		int[] r = marqueeRect();
		for (HudObject obj : HudObjects.all()) {
			if (intersects(obj.screenBounds(this.width, this.height, true), r)) {
				selection.add(obj);
			}
		}
	}

	private int snapAxis(int pos, int size, int[] targets, boolean isX) {
		int bestPos = pos;
		int bestDistance = SNAP_THRESHOLD + 1;
		Integer bestGuide = null;
		for (int target : targets) {
			int[] candidates = {target, target - size, target - size / 2};
			for (int candidate : candidates) {
				int distance = Math.abs(pos - candidate);
				if (distance < bestDistance) {
					bestDistance = distance;
					bestPos = candidate;
					bestGuide = target;
				}
			}
		}
		if (bestGuide != null) {
			if (isX) {
				guideX = bestGuide;
			} else {
				guideY = bestGuide;
			}
		}
		return bestPos;
	}

	private int[] snapTargets(boolean isX) {
		int screenSize = isX ? this.width : this.height;
		List<Integer> targets = new ArrayList<>();
		targets.add(EDGE);
		targets.add(screenSize / 2);
		targets.add(screenSize - EDGE);
		if (gridEnabled) {
			for (int g = 0; g <= screenSize; g += EditorTheme.GRID_STEP) {
				targets.add(g);
			}
		}
		for (HudObject object : HudObjects.all()) {
			if (selection.contains(object)) {
				continue;
			}
			int[] b = object.screenBounds(this.width, this.height, true);
			int start = isX ? b[0] : b[1];
			int size = isX ? b[2] : b[3];
			targets.add(start);
			targets.add(start + size);
			targets.add(start + size / 2);
		}
		return targets.stream().mapToInt(Integer::intValue).toArray();
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (!selection.isEmpty()) {
			int step = (input.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0 ? 10 : 1;
			int dx = 0;
			int dy = 0;
			switch (input.key()) {
				case GLFW.GLFW_KEY_LEFT -> dx = -step;
				case GLFW.GLFW_KEY_RIGHT -> dx = step;
				case GLFW.GLFW_KEY_UP -> dy = -step;
				case GLFW.GLFW_KEY_DOWN -> dy = step;
				default -> {
				}
			}
			if (dx != 0 || dy != 0) {
				nudgeSelection(dx, dy);
				return true;
			}
		}
		return super.keyPressed(input);
	}

	/** Nudges all selected widgets by (dx,dy), clamped so the group stays on-screen. */
	private void nudgeSelection(int dx, int dy) {
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		for (HudObject obj : selection) {
			int[] b = obj.screenBounds(this.width, this.height, true);
			minX = Math.min(minX, b[0]);
			minY = Math.min(minY, b[1]);
			maxX = Math.max(maxX, b[0] + b[2]);
			maxY = Math.max(maxY, b[1] + b[3]);
		}
		dx = MathHelper.clamp(dx, -minX, this.width - maxX);
		dy = MathHelper.clamp(dy, -minY, this.height - maxY);
		for (HudObject obj : selection) {
			int[] b = obj.screenBounds(this.width, this.height, true);
			obj.setScreenBoxPosition(b[0] + dx, b[1] + dy, this.width, this.height);
		}
	}

	@Override
	public boolean mouseReleased(Click click) {
		if (marqueeing) {
			if (!marqueeMoved && !marqueeAdditive) {
				selection.clear();
			}
			marqueeing = false;
			marqueeBase = null;
			clearAndInit();
		}
		groupDragging = false;
		resizing = false;
		guideX = null;
		guideY = null;
		return super.mouseReleased(click);
	}

	@Override
	public void close() {
		FishBiteConfig.get().save();
		if (this.client != null) {
			this.client.setScreen(this.parent);
		}
	}
}
