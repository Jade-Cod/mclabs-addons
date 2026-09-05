package dev.jade.labsaddons.hud;

import dev.jade.labsaddons.config.LabsAddonsConfig;
import dev.jade.labsaddons.hud.editor.EditorPainter;
import dev.jade.labsaddons.hud.editor.EditorTheme;
import dev.jade.labsaddons.hud.editor.Shutter;
import dev.jade.labsaddons.runner.RunnerAlarm;
import dev.jade.labsaddons.runner.RunnerHudObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.function.IntConsumer;
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
	private static final int GROUP_INDENT = 6;
	/** Extra breathing room between Reset Widget and the "Ability Visibility" heading below it. */
	private static final int GROUPS_HEADING_EXTRA = 6;
	/**
	 * Everything in a rail row that is not the label: the row's inset either side, the
	 * visibility toggle, and its gap. Kept in step with {@code layerRowRect} and
	 * {@code toggleRect} so {@link #railWidth()} can size the rail around a name.
	 */
	private static final int RAIL_TEXT_INSET = 22;
	/** Longest a name may render before it is elided, as a share of the screen. */
	private static final int RAIL_MAX_SHARE = 3;
	/** Rail roll-up timing, matched to the Runner Leaderboard's job-history shutter. */
	private static final long RAIL_ROLL_MS = 400L;
	/** How quickly the rail ghosts out once you start moving a widget. */
	private static final long CHROME_FADE_MS = 150L;
	/** What the rail fades to while a widget is being dragged, resized, or nudged. */
	private static final float DRAG_ALPHA = 0.25f;
	/** How long a keyboard nudge keeps the rail faded, since there's no key-up to watch. */
	/** Extra toolbar height per wrapped row. */
	private static final int WRAP_ROW_H = 24;
	private static final int PROFILE_BTN_W = 92;
	private static final long NUDGE_FADE_MS = 600L;
	/** {@link #expandedGroups} key for the Runner Jobs widget's low-job alarm section. */
	private static final String ALARM_GROUP_KEY = "runner_alarm";

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
	/** Lazily measured by {@link #railWidth()}; 0 means "not measured yet". */
	private int railWidthCache;

	/** Widgets rail roll-up, and the chrome's fade-out while a widget is being moved. */
	private final Shutter railRoll = new Shutter(
			LabsAddonsConfig.get().hudEditorRailCollapsed ? 0.0 : 1.0,
			RAIL_ROLL_MS, System.currentTimeMillis());
	private final Shutter chromeFade = new Shutter(1.0, CHROME_FADE_MS, System.currentTimeMillis());
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

	/** When the arrows last moved the selection — arrow keys have no release to watch. */
	private long lastNudgeMs;

	private Integer guideX;
	private Integer guideY;
	private boolean snapEnabled = true;
	private boolean gridEnabled;

	// First-run welcome guard: check once per screen instance (init() also runs on resize).
	private boolean welcomeChecked;

	// Inspector layout, recomputed in init() whenever the selection changes.
	private int[] panel;
	private int nameY;
	private int textSwatchX;
	private int textSwatchY;
	private int bgSwatchX;
	private int bgSwatchY;
	private int groupsLabelY;
	private Component groupsLabelText;

	/** Inspector-owned widgets, faded with the panel; the toolbar's are deliberately not. */
	private final List<AbstractWidget> inspectorWidgets = new ArrayList<>();

	// Which toggle groups (by label) are expanded in the current inspector.
	private final Set<String> expandedGroups = new LinkedHashSet<>();

	/** A single collapsed-group ability row, painted and hit-tested like the Widgets rail. */
	private record AbilityRow(int[] rect, HudObject.ToggleOption option) {
	}

	private final List<AbilityRow> abilityRows = new ArrayList<>();

	public HudEditScreen(Screen parent) {
		super(Component.translatable("labsaddons.hud.editor.title"));
		this.parent = parent;
	}

	private HudObject singleSelected() {
		return selection.size() == 1 ? selection.iterator().next() : null;
	}

	// --- lifecycle ------------------------------------------------------------

	/** Profile this screen's widgets were built against; see {@link #render}. */
	private String builtForProfile = "";
	/** Toolbar rows in use; 2 when the button groups will not fit side by side. */
	private int toolbarRows = 1;
	/** Right edge of the toolbar's left button group. */
	private int leftGroupEnd;
	/** Left edge of the toolbar's right button group, when it shares the bottom row. */
	private int rightGroupStart;

	@Override
	protected void init() {
		this.panel = null;
		builtForProfile = LabsAddonsConfig.get().activeProfile;
		buildToolbar();
		HudObject one = singleSelected();
		if (one != null) {
			buildInspector(one);
		}
		maybeShowWelcome();
	}

	/** Show the welcome guide once, the first time a player ever opens the editor. */
	private void maybeShowWelcome() {
		if (welcomeChecked) {
			return;
		}
		welcomeChecked = true;
		if (!LabsAddonsConfig.get().hasSeenWelcome && this.minecraft != null) {
			this.minecraft.setScreenAndShow(new HelpScreen(this, true));
		}
	}

	private void openHelp() {
		if (this.minecraft != null) {
			this.minecraft.setScreenAndShow(new HelpScreen(this, false));
		}
	}

	private void openStatistics() {
		if (this.minecraft != null) {
			this.minecraft.setScreenAndShow(new RunnerStatsScreen(this));
		}
	}

	/** Height of the toolbar, which grows by a row when its buttons cannot fit on one. */
	private int toolbarH() {
		return EditorTheme.TOOLBAR_H + (toolbarRows - 1) * WRAP_ROW_H;
	}

	private void buildToolbar() {
		// The right-hand group (Profile/Grid/Snap) drops to its own row rather than
		// running into the left group, which it does below roughly 570px of scaled
		// width — GUI scale 4 on a 1080p screen, among others.
		leftGroupEnd = EditorTheme.MARGIN + 48 + 4 + 96 + 4 + 78 + 4 + 48 + 4 + 48;
		int rightW = PROFILE_BTN_W + 4 + 64 + 4 + 64 + EditorTheme.MARGIN;
		toolbarRows = leftGroupEnd + EditorTheme.GAP + rightW <= this.width ? 1 : 2;

		int y = this.height - EditorTheme.TOOLBAR_H + (EditorTheme.TOOLBAR_H - 20) / 2;
		int x = EditorTheme.MARGIN;
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
				.bounds(x, y, 48, 20).build());
		x += 52;
		this.addRenderableWidget(Button.builder(
						Component.translatable("labsaddons.hud.editor.open_config"), b -> openMainConfig())
				.bounds(x, y, 96, 20).build());
		x += 100;
		this.addRenderableWidget(Button.builder(
						Component.translatable("labsaddons.hud.editor.reset_all"), b -> resetAll())
				.bounds(x, y, 78, 20)
				.tooltip(Tooltip.create(Component.translatable("labsaddons.hud.editor.reset_all.tooltip"))).build());
		x += 82;
		this.addRenderableWidget(Button.builder(
						Component.translatable("labsaddons.hud.editor.help"), b -> openHelp())
				.bounds(x, y, 48, 20)
				.tooltip(Tooltip.create(Component.translatable("labsaddons.hud.editor.help.tooltip"))).build());
		x += 52;
		this.addRenderableWidget(Button.builder(
						Component.translatable("labsaddons.hud.editor.stats"), b -> openStatistics())
				.bounds(x, y, 48, 20)
				.tooltip(Tooltip.create(Component.translatable("labsaddons.hud.editor.stats.tooltip"))).build());

		int ry = toolbarRows == 1 ? y : y - WRAP_ROW_H;
		int rx = this.width - EditorTheme.MARGIN - 64;
		rightGroupStart = rx - 68 - PROFILE_BTN_W;
		this.addRenderableWidget(Button.builder(profileLabel(), b -> openProfiles())
				.bounds(rx - 68 - PROFILE_BTN_W, ry, PROFILE_BTN_W, 20)
				.tooltip(Tooltip.create(Component.translatable("labsaddons.hud.editor.profiles.tooltip"))).build());
		this.addRenderableWidget(Button.builder(snapLabel(), b -> {
					snapEnabled = !snapEnabled;
					b.setMessage(snapLabel());
				}).bounds(rx, ry, 64, 20)
				.tooltip(Tooltip.create(Component.translatable("labsaddons.hud.editor.snap.tooltip"))).build());
		rx -= 68;
		this.addRenderableWidget(Button.builder(gridLabel(), b -> {
					gridEnabled = !gridEnabled;
					b.setMessage(gridLabel());
				}).bounds(rx, ry, 64, 20)
				.tooltip(Tooltip.create(Component.translatable("labsaddons.hud.editor.grid.tooltip"))).build());
	}

	/** "Profile: fishing", trimmed so a long id cannot push the button off the toolbar. */
	private Component profileLabel() {
		String id = LabsAddonsConfig.get().activeProfile;
		String shown = id.length() > 9 ? id.substring(0, 8) + "\u2026" : id;
		return Component.translatable("labsaddons.hud.editor.profiles", shown);
	}

	private void openProfiles() {
		if (this.minecraft != null) {
			this.minecraft.setScreenAndShow(new HudProfileScreen(this));
		}
	}

	private Component snapLabel() {
		return Component.translatable("labsaddons.hud.editor.snap").append(": ").append(CommonComponents.optionStatus(snapEnabled));
	}

	private Component gridLabel() {
		return Component.translatable("labsaddons.hud.editor.grid").append(": ").append(CommonComponents.optionStatus(gridEnabled));
	}

	private void buildInspector(HudObject widget) {
		inspectorWidgets.clear();
		HudObjectSettings s = widget.settings();
		int innerW = EditorTheme.PANEL_W - 2 * EditorTheme.PAD;
		boolean hasAction = widget.editorAction() != null;
		List<HudObject.ToggleOption> toggles = widget.toggleOptions();
		HudObject.SwitchOption switchOption = widget.switchOption();
		List<HudObject.ToggleGroup> groups = widget.toggleGroups();
		boolean hasGroups = !groups.isEmpty();
		boolean isRunnerJobs = widget instanceof RunnerHudObject;
		boolean alarmExpanded = isRunnerJobs && expandedGroups.contains(ALARM_GROUP_KEY);

		int rowStep = EditorTheme.ROW + EditorTheme.GAP;
		int groupsRows = 0;
		if (hasGroups) {
			for (HudObject.ToggleGroup group : groups) {
				groupsRows += 1; // collapsible header
				if (expandedGroups.contains(group.label().getString())) {
					groupsRows += group.options().size();
				}
			}
		}
		int contentH = EditorTheme.NAME_H
				+ rowStep * 4
				+ rowStep * toggles.size()
				+ (switchOption != null ? rowStep : 0)
				+ (hasAction ? rowStep : 0)
				+ rowStep
				+ (isRunnerJobs ? rowStep : 0)
				+ (alarmExpanded ? rowStep * 3 : 0)
				+ (hasGroups ? GROUPS_HEADING_EXTRA + EditorTheme.NAME_H : 0)
				+ rowStep * groupsRows;
		int panelH = contentH + 2 * EditorTheme.PAD;

		int panelX = dockLeft(widget)
				? EditorTheme.MARGIN + railWidth() + EditorTheme.GAP
				: this.width - EditorTheme.PANEL_W - EditorTheme.MARGIN;
		int toolbarTop = this.height - toolbarH() - EditorTheme.GAP;
		int panelY = EditorTheme.TOP;
		if (panelY + panelH > toolbarTop) {
			panelY = Math.max(2, toolbarTop - panelH);
		}
		this.panel = new int[]{panelX, panelY, EditorTheme.PANEL_W, panelH};

		int innerX = panelX + EditorTheme.PAD;
		int y = panelY + EditorTheme.PAD;

		this.nameY = y;
		y += EditorTheme.NAME_H;

		inspectorChild(CycleButton.onOffBuilder(s.enabled).create(
				innerX, y, innerW, EditorTheme.ROW,
				Component.translatable("labsaddons.config.hud.enabled"),
				(b, v) -> s.enabled = v));
		y += rowStep;

		inspectorChild(Button.builder(
						Component.translatable("labsaddons.config.hud.text_color"), b -> openPicker(widget, false))
				.bounds(innerX, y, innerW - EditorTheme.SWATCH - EditorTheme.GAP, EditorTheme.ROW).build());
		this.textSwatchX = innerX + innerW - EditorTheme.SWATCH;
		this.textSwatchY = y;
		y += rowStep;

		inspectorChild(CycleButton.onOffBuilder(s.backgroundEnabled).create(
				innerX, y, innerW, EditorTheme.ROW,
				Component.translatable("labsaddons.config.hud.background"),
				(b, v) -> s.backgroundEnabled = v));
		y += rowStep;

		inspectorChild(Button.builder(
						Component.translatable("labsaddons.config.hud.background_color"), b -> openPicker(widget, true))
				.bounds(innerX, y, innerW - EditorTheme.SWATCH - EditorTheme.GAP, EditorTheme.ROW).build());
		this.bgSwatchX = innerX + innerW - EditorTheme.SWATCH;
		this.bgSwatchY = y;
		y += rowStep;

		for (HudObject.ToggleOption toggle : toggles) {
			inspectorChild(CycleButton.onOffBuilder(toggle.value().getAsBoolean()).create(
					innerX, y, innerW, EditorTheme.ROW, toggle.label(),
					(b, v) -> toggle.onChange().accept(v)));
			y += rowStep;
		}

		if (switchOption != null) {
			inspectorChild(new DirectionSwitch(innerX, y, innerW, EditorTheme.ROW, switchOption));
			y += rowStep;
		}

		HudObject.EditorAction action = widget.editorAction();
		if (action != null) {
			inspectorChild(Button.builder(action.label(), b -> action.action().run())
					.bounds(innerX, y, innerW, EditorTheme.ROW).build());
			y += rowStep;
		}

		inspectorChild(Button.builder(
						Component.translatable("labsaddons.hud.editor.reset_widget"), b -> {
							widget.settings().resetTo(widget.defaultSettings());
							rebuildWidgets();
						})
				.bounds(innerX, y, innerW, EditorTheme.ROW).build());
		y += rowStep;

		if (isRunnerJobs) {
			LabsAddonsConfig config = LabsAddonsConfig.get();
			inspectorChild(Button.builder(
							groupHeaderLabel(Component.translatable("labsaddons.hud.runner_jobs.alarm"), alarmExpanded),
							b -> {
								if (!expandedGroups.remove(ALARM_GROUP_KEY)) {
									expandedGroups.add(ALARM_GROUP_KEY);
								}
								rebuildWidgets();
							})
					.bounds(innerX, y, innerW, EditorTheme.ROW).build());
			y += rowStep;

			if (alarmExpanded) {
				inspectorChild(CycleButton.onOffBuilder(config.runnerAlarmEnabled).create(
						innerX, y, innerW, EditorTheme.ROW,
						Component.translatable("labsaddons.hud.runner_jobs.alarm.enabled"),
						(b, v) -> config.runnerAlarmEnabled = v));
				y += rowStep;

				inspectorChild(new ThresholdSlider(innerX, y, innerW, EditorTheme.ROW,
						config.runnerAlarmThreshold, v -> config.runnerAlarmThreshold = v));
				y += rowStep;

				inspectorChild(CycleButton.builder(RunnerAlarm::soundLabel, config.runnerAlarmSound)
						.withValues(RunnerAlarm.SOUND_IDS)
						.create(innerX, y, innerW, EditorTheme.ROW,
								Component.translatable("labsaddons.hud.runner_jobs.alarm.sound"),
								(b, v) -> config.runnerAlarmSound = v));
				y += rowStep;
			}
		}

		this.groupsLabelText = hasGroups ? widget.toggleGroupsLabel() : null;
		this.abilityRows.clear();
		if (hasGroups) {
			y += GROUPS_HEADING_EXTRA;
			this.groupsLabelY = y;
			y += EditorTheme.NAME_H;
			for (HudObject.ToggleGroup group : groups) {
				String groupKey = group.label().getString();
				boolean expanded = expandedGroups.contains(groupKey);
				inspectorChild(Button.builder(groupHeaderLabel(group.label(), expanded), b -> {
							if (!expandedGroups.remove(groupKey)) {
								expandedGroups.add(groupKey);
							}
							rebuildWidgets();
						})
						.bounds(innerX, y, innerW, EditorTheme.ROW).build());
				y += rowStep;
				if (expanded) {
					for (HudObject.ToggleOption option : group.options()) {
						int[] rect = {innerX + GROUP_INDENT, y, innerW - GROUP_INDENT, EditorTheme.ROW};
						this.abilityRows.add(new AbilityRow(rect, option));
						y += rowStep;
					}
				}
			}
		}
	}

	private static Component groupHeaderLabel(Component label, boolean expanded) {
		return Component.literal(expanded ? "v " : "> ").append(label);
	}

	/**
	 * A pill-shaped two-position switch: a sliding accent thumb spanning half the
	 * track, labelled with whichever side it currently occupies. Clicking anywhere
	 * on the switch flips it; the thumb eases to its new side over
	 * {@value #ANIM_MS}ms rather than jumping instantly.
	 */
	/**
	 * The low-job alarm threshold: a whole number of jobs from 0 to
	 * {@link RunnerAlarm#MAX_THRESHOLD}. A slider rather than a text box because the
	 * range is small and closed — there is no invalid value to type, nothing to parse,
	 * and, being drawn by hand like {@link DirectionSwitch}, it ghosts with the rest of
	 * the panel instead of staying a stark white box over the game.
	 */
	private static final class ThresholdSlider extends AbstractSliderButton {
		private final IntConsumer onChange;

		ThresholdSlider(int x, int y, int width, int height, int initial, IntConsumer onChange) {
			super(x, y, width, height, Component.empty(),
					Math.clamp(initial, 0, RunnerAlarm.MAX_THRESHOLD) / (double) RunnerAlarm.MAX_THRESHOLD);
			this.onChange = onChange;
			updateMessage();
		}

		private int jobs() {
			return (int) Math.round(value * RunnerAlarm.MAX_THRESHOLD);
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.translatable("labsaddons.hud.runner_jobs.alarm.threshold", jobs()));
		}

		@Override
		protected void applyValue() {
			onChange.accept(jobs());
		}

		@Override
		public void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
			float a = this.alpha;
			EditorPainter.pill(context, getX(), getY(), getWidth(), getHeight(),
					EditorTheme.withAlpha(EditorTheme.SWITCH_TRACK, a));

			// Only once the filled part is at least as wide as it is tall: below that the
			// pill's two semicircular caps would overlap and invert the middle rect.
			int filled = (int) Math.round(value * getWidth());
			if (filled >= getHeight()) {
				EditorPainter.pill(context, getX(), getY(), filled, getHeight(),
						EditorTheme.withAlpha(EditorTheme.ROW_SELECTED, a));
			}

			Font font = Minecraft.getInstance().font;
			Component label = getMessage();
			context.text(font, label, getX() + (getWidth() - font.width(label)) / 2,
					getY() + (getHeight() - font.lineHeight) / 2 + 1,
					EditorTheme.withAlpha(EditorTheme.TEXT, a), true);
		}
	}

	private static final class DirectionSwitch extends AbstractSliderButton {
		private static final long ANIM_MS = 150L;

		private final HudObject.SwitchOption option;
		private boolean currentRight;
		private double animFrom;
		private long animStartMs;

		DirectionSwitch(int x, int y, int width, int height, HudObject.SwitchOption option) {
			super(x, y, width, height, Component.empty(), option.isRight().getAsBoolean() ? 1.0 : 0.0);
			this.option = option;
			this.currentRight = option.isRight().getAsBoolean();
			this.animFrom = this.currentRight ? 1.0 : 0.0;
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			setMessage(currentRight ? option.rightLabel() : option.leftLabel());
		}

		@Override
		protected void applyValue() {
			setRight(value >= 0.5);
		}

		/** A click anywhere on the switch flips it — where exactly you click doesn't matter. */
		@Override
		public void onClick(MouseButtonEvent click, boolean doubled) {
			setRight(!currentRight);
		}

		private void setRight(boolean right) {
			if (right != currentRight) {
				animFrom = currentRight ? 1.0 : 0.0;
				animStartMs = System.currentTimeMillis();
				currentRight = right;
				option.onChange().accept(right);
			}
			this.value = right ? 1.0 : 0.0;
			updateMessage();
		}

		private float progress() {
			float t = Math.min(1f, (System.currentTimeMillis() - animStartMs) / (float) ANIM_MS);
			float eased = 1f - (1f - t) * (1f - t);
			double target = currentRight ? 1.0 : 0.0;
			return (float) (animFrom + (target - animFrom) * eased);
		}

		@Override
		public void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
			// Drawn by hand rather than from a texture, so setAlpha() does nothing for us
			// on its own — the fade has to be applied to each colour.
			float a = this.alpha;
			EditorPainter.pill(context, getX(), getY(), getWidth(), getHeight(),
					EditorTheme.withAlpha(EditorTheme.SWITCH_TRACK, a));

			float progress = progress();
			int thumbW = getWidth() / 2;
			int thumbX = getX() + Math.round(progress * (getWidth() - thumbW));
			EditorPainter.pill(context, thumbX, getY(), thumbW, getHeight(),
					EditorTheme.withAlpha(EditorTheme.ACCENT, a));

			Font font = Minecraft.getInstance().font;
			Component label = progress < 0.5f ? option.leftLabel() : option.rightLabel();
			int textX = thumbX + (thumbW - font.width(label)) / 2;
			int textY = getY() + (getHeight() - font.lineHeight) / 2 + 1;
			context.text(font, label, textX, textY,
					EditorTheme.withAlpha(EditorTheme.SWITCH_THUMB_TEXT, a), false);
		}
	}

	/** Toggles the ability row under (mx,my), if any; true if the click was consumed. */
	private boolean clickAbilityRow(double mx, double my) {
		for (AbilityRow row : abilityRows) {
			if (contains(row.rect(), mx, my)) {
				HudObject.ToggleOption option = row.option();
				option.onChange().accept(!option.value().getAsBoolean());
				rebuildWidgets();
				return true;
			}
		}
		return false;
	}

	/** Dock the inspector on the left when the selected widget sits on the right half. */
	private boolean dockLeft(HudObject widget) {
		int[] b = widget.screenBounds(this.width, this.height, true);
		return (b[0] + b[2] / 2) > this.width / 2;
	}

	private void openPicker(HudObject widget, boolean background) {
		if (this.minecraft == null) {
			return;
		}
		HudObjectSettings s = widget.settings();
		if (background) {
			this.minecraft.setScreenAndShow(new ColorPickerScreen(this,
					Component.translatable("labsaddons.config.hud.background_color"),
					s.backgroundColor, true, color -> s.backgroundColor = color));
		} else {
			this.minecraft.setScreenAndShow(new ColorPickerScreen(this,
					Component.translatable("labsaddons.config.hud.text_color"),
					s.textColor, false, color -> s.textColor = 0xFF000000 | (color & 0xFFFFFF)));
		}
	}

	private void openMainConfig() {
		Minecraft.getInstance().setScreenAndShow(
				dev.jade.labsaddons.config.LabsAddonsConfigScreenFactory.create(this));
	}

	/**
	 * Restores the active profile to factory state — every widget's placement and the
	 * display choices that travel with a layout. Deliberately scoped to the profile you
	 * are looking at: other profiles, tracked server state and your global settings are
	 * untouched, which is the only reading that makes sense once layouts are named.
	 */
	private void resetAll() {
		LabsAddonsConfig config = LabsAddonsConfig.get();
		for (HudObject obj : HudObjects.all()) {
			obj.settings().resetTo(obj.defaultSettings());
		}
		// These live in the profile too, so leaving them behind would make a "reset"
		// still show hidden cooldowns and pinned rows from before.
		config.pinnedProgressRows.clear();
		config.hiddenCooldownKeys.clear();
		config.hiddenRaidMineCodes.clear();
		config.cooldownsStackVertical = false;
		selection.clear();
		rebuildWidgets();
	}

	private void selectOnly(HudObject obj) {
		selection.clear();
		selection.add(obj);
		rebuildWidgets();
	}

	private void toggleSelection(HudObject obj) {
		if (!selection.remove(obj)) {
			selection.add(obj);
		}
		rebuildWidgets();
	}

	// --- rendering ------------------------------------------------------------

	@Override
	public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		// Custom light dim is drawn in render() so the live game stays visible;
		// suppress the vanilla blur/dirt background entirely.
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		// A world change can auto-switch the profile while the editor is open, which
		// swaps every widget's settings object out from under the inspector.
		if (!LabsAddonsConfig.get().activeProfile.equals(builtForProfile)) {
			refreshForProfileChange();
		}
		context.fill(0, 0, this.width, this.height, EditorTheme.DIM);
		if (gridEnabled) {
			EditorPainter.gridOverlay(context, this.width, this.height, EditorTheme.GRID_STEP, EditorTheme.GRID);
		}

		context.centeredText(this.font, this.title, this.width / 2, 12, EditorTheme.TITLE);
		context.centeredText(this.font,
				Component.translatable("labsaddons.hud.editor.hint"), this.width / 2, 24, EditorTheme.TEXT_DIM);

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
				context.centeredText(this.font, Component.literal(pct + "%"),
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

		long now = System.currentTimeMillis();
		chromeFade.retarget(isMovingWidget(now) ? DRAG_ALPHA : 1.0, now);
		// Vanilla widgets draw themselves in super.render(), so they are faded by their
		// own alpha rather than by our colours. The toolbar keeps its full opacity: it is
		// the editor's frame, not a panel in the way, and its nudge hint is worth reading
		// precisely while you are nudging.
		float chromeAlpha = chromeFade.progress();
		for (AbstractWidget child : inspectorWidgets) {
			child.setAlpha(chromeAlpha);
		}
		drawRail(context, mouseX, mouseY);
		if (one != null && panel != null) {
			drawInspector(context, one, mouseX, mouseY);
		} else if (selection.size() > 1) {
			context.centeredText(this.font,
					Component.translatable("labsaddons.hud.editor.multi_hint", selection.size()),
					this.width / 2, this.height - toolbarH() - 12, EditorTheme.TEXT_ACCENT);
		}

		context.fill(0, this.height - toolbarH(), this.width, this.height, EditorTheme.TOOLBAR_BG);
		// Centred in the gap the buttons actually leave, not the whole width — at a large
		// GUI scale the screen's midpoint sits underneath the left button group. If the
		// gap cannot hold the hint, drop it: unreadable text over the buttons is worse
		// than no hint, and the arrow keys work whether or not it is on screen.
		int gapStart = leftGroupEnd + EditorTheme.GAP;
		int gapEnd = (toolbarRows == 1 ? rightGroupStart : this.width) - EditorTheme.GAP;
		Component hint = Component.translatable("labsaddons.hud.editor.nudge_hint");
		if (gapEnd - gapStart >= this.font.width(hint)) {
			context.centeredText(this.font, hint, (gapStart + gapEnd) / 2,
					this.height - EditorTheme.TOOLBAR_H / 2 - 4, EditorTheme.TEXT_DIM);
		}

		super.extractRenderState(context, mouseX, mouseY, delta);
	}

	private void drawResizeHandles(GuiGraphicsExtractor context, int[] b) {
		for (int[] spec : HANDLE_SPECS) {
			int cx = b[0] + (spec[0] + 1) * b[2] / 2;
			int cy = b[1] + (spec[1] + 1) * b[3] / 2;
			EditorPainter.resizeHandle(context, cx, cy, EditorTheme.HANDLE_SZ,
					EditorTheme.HANDLE, EditorTheme.PANEL_BG);
		}
	}

	private void drawNameChip(GuiGraphicsExtractor context, HudObject obj, int[] b) {
		boolean enabled = obj.settings().enabled;
		Component label = enabled ? obj.displayName()
				: Component.translatable("labsaddons.hud.editor.hidden_suffix", obj.displayName());
		int color = enabled ? EditorTheme.TEXT : EditorTheme.TEXT_HIDDEN;
		boolean above = b[1] - (this.font.lineHeight + 4) >= EditorTheme.TOP + 4;
		int chipY = above ? b[1] - this.font.lineHeight - 3 : b[1] + b[3] + 3;
		int chipX = Mth.clamp(b[0], EditorTheme.MARGIN,
				this.width - EditorTheme.MARGIN - this.font.width(label));
		EditorPainter.nameChip(context, this.font, label, chipX, chipY, color);
	}

	/**
	 * The rail. Rows are always laid out where they'd sit fully rolled down; a scissor
	 * narrowed to the animated height does the rolling, the same trick the Runner
	 * Leaderboard's job-history shutter uses.
	 */
	private void drawRail(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		int[] r = railRect();
		EditorPainter.panel(context, r, fade(EditorTheme.PANEL_BG), fade(EditorTheme.PANEL_BORDER));

		boolean open = railRoll.target() != 0.0;
		context.text(this.font,
				groupHeaderLabel(Component.translatable("labsaddons.hud.editor.layers"), open),
				r[0] + 5, r[1] + 3, fade(EditorTheme.TEXT_ACCENT), false);
		if (r[3] <= EditorTheme.RAIL_HEADER_H) {
			return;
		}

		context.enableScissor(r[0], r[1] + EditorTheme.RAIL_HEADER_H, r[0] + r[2], r[1] + r[3]);
		List<HudObject> objects = HudObjects.all();
		for (int i = 0; i < objects.size(); i++) {
			HudObject obj = objects.get(i);
			int[] row = layerRowRect(i);
			if (selection.contains(obj)) {
				context.fill(row[0], row[1], row[0] + row[2], row[1] + row[3], fade(EditorTheme.ROW_SELECTED));
			} else if (contains(row, mouseX, mouseY) && contains(r, mouseX, mouseY)) {
				context.fill(row[0], row[1], row[0] + row[2], row[1] + row[3], fade(EditorTheme.ROW_HOVER));
			}

			boolean enabled = obj.settings().enabled;
			int textMaxW = row[2] - EditorTheme.RAIL_TOGGLE - 10;
			String name = elide(obj.displayName().getString(), textMaxW);
			context.text(this.font, Component.literal(name), row[0] + 4,
					row[1] + (row[3] - this.font.lineHeight) / 2 + 1,
					fade(enabled ? EditorTheme.TEXT : EditorTheme.TEXT_HIDDEN), false);

			int[] tb = toggleRect(row);
			if (enabled) {
				context.fill(tb[0], tb[1], tb[0] + tb[2], tb[1] + tb[3], fade(EditorTheme.TOGGLE_ON));
			} else {
				EditorPainter.outline(context, tb[0], tb[1], tb[2], tb[3], fade(EditorTheme.TOGGLE_OFF));
			}
		}
		context.disableScissor();
	}

	/**
	 * Scales a colour's alpha by the chrome fade, so the rail ghosts out of the way while
	 * you are actually moving a widget and you can watch it travel underneath.
	 */
	private int fade(int argb) {
		return EditorTheme.withAlpha(argb, chromeFade.progress());
	}

	/** True while the selection is being dragged, resized, or was just nudged with the arrows. */
	private boolean isMovingWidget(long now) {
		return groupDragging || resizing || now - lastNudgeMs < NUDGE_FADE_MS;
	}

	private void drawInspector(GuiGraphicsExtractor context, HudObject widget, int mouseX, int mouseY) {
		HudObjectSettings s = widget.settings();
		EditorPainter.panel(context, panel, fade(EditorTheme.PANEL_BG), fade(EditorTheme.PANEL_BORDER));
		int innerX = panel[0] + EditorTheme.PAD;
		int innerW = panel[2] - 2 * EditorTheme.PAD;

		String name = elide(widget.displayName().getString(), innerW);
		context.text(this.font, Component.literal(name), innerX, nameY, fade(EditorTheme.TEXT), false);

		float alpha = chromeFade.progress();
		EditorPainter.swatch(context, textSwatchX, textSwatchY, EditorTheme.SWATCH,
				s.textColor | 0xFF000000, alpha);
		EditorPainter.swatch(context, bgSwatchX, bgSwatchY, EditorTheme.SWATCH, s.backgroundColor, alpha);

		if (groupsLabelText != null) {
			drawGroupsHeading(context);
		}
		drawAbilityRows(context, mouseX, mouseY);
	}

	/** The "Ability Visibility" heading, styled identically to the widget name above it. */
	private void drawGroupsHeading(GuiGraphicsExtractor context) {
		int innerX = panel[0] + EditorTheme.PAD;
		int innerW = panel[2] - 2 * EditorTheme.PAD;
		String name = elide(groupsLabelText.getString(), innerW);
		context.text(this.font, Component.literal(name), innerX, groupsLabelY, fade(EditorTheme.TEXT), false);
	}

	/** Ability toggle rows, styled like the Widgets rail: filled/hollow tick + normal/italic-red label. */
	private void drawAbilityRows(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		for (AbilityRow row : abilityRows) {
			int[] r = row.rect();
			if (contains(r, mouseX, mouseY)) {
				context.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], fade(EditorTheme.ROW_HOVER));
			}

			boolean on = row.option().value().getAsBoolean();
			int[] tb = toggleRect(r);
			if (on) {
				context.fill(tb[0], tb[1], tb[0] + tb[2], tb[1] + tb[3], fade(EditorTheme.TOGGLE_ON));
			} else {
				EditorPainter.outline(context, tb[0], tb[1], tb[2], tb[3], fade(EditorTheme.TOGGLE_OFF));
			}

			int textMaxW = r[2] - EditorTheme.RAIL_TOGGLE - 10;
			String name = elide(row.option().label().getString(), textMaxW);
			Component label = Component.literal(name).withStyle(style -> style.withItalic(!on));
			context.text(this.font, label, r[0] + 4,
					r[1] + (r[3] - this.font.lineHeight) / 2 + 1,
					fade(on ? EditorTheme.TEXT : EditorTheme.TEXT_HIDDEN), false);
		}
	}

	// --- geometry -------------------------------------------------------------

	/**
	 * Rail width, widened to fit the longest widget name so no row has to be cut.
	 *
	 * <p>{@link EditorTheme#RAIL_W} is the floor rather than the width: it fits most
	 * names but not all of them, and a fixed rail silently truncates any widget added
	 * later. Capped at a share of the screen so a long name cannot crowd out the canvas
	 * — the floor is folded into the cap too, or a very narrow window would invert the
	 * two bounds.
	 *
	 * <p>Cached because the widget list cannot change while the editor is open, and this
	 * is reached several times per row per frame through {@link #layerRowRect}.
	 */
	private int railWidth() {
		if (railWidthCache == 0) {
			int widest = 0;
			for (HudObject obj : HudObjects.all()) {
				widest = Math.max(widest, this.font.width(obj.displayName()));
			}
			int cap = Math.max(EditorTheme.RAIL_W, this.width / RAIL_MAX_SHARE);
			railWidthCache = Math.clamp(widest + RAIL_TEXT_INSET, EditorTheme.RAIL_W, cap);
		}
		return railWidthCache;
	}

	/**
	 * {@code text} shortened to {@code maxWidth}, ending in an ellipsis if anything was
	 * dropped. Plain {@code trimToWidth} stops mid-word with no sign it did so, which
	 * reads as a broken label rather than a shortened one.
	 */
	private String elide(String text, int maxWidth) {
		if (this.font.width(text) <= maxWidth) {
			return text;
		}
		String ellipsis = "…";
		int budget = maxWidth - this.font.width(ellipsis);
		// Too narrow for even one character plus the ellipsis: a hard cut is all that fits.
		return budget <= 0 ? this.font.plainSubstrByWidth(text, maxWidth)
				: this.font.plainSubstrByWidth(text, budget) + ellipsis;
	}

	/** Height of the rows block below the header when the rail is fully rolled down. */
	private int railRowsHeight() {
		return HudObjects.all().size() * EditorTheme.RAIL_ROW_H + EditorTheme.PAD;
	}

	/**
	 * The rail as it currently stands — header plus however much of the rows block has
	 * rolled down. Everything (panel, hit tests, {@link #overUi}) reads this, so a rolled-up
	 * rail stops eating clicks in the same motion that it stops covering the screen.
	 */
	private int[] railRect() {
		int h = EditorTheme.RAIL_HEADER_H + Math.round(railRoll.progress() * railRowsHeight());
		int y = EditorTheme.TOP;
		int toolbarTop = this.height - toolbarH() - EditorTheme.GAP;
		if (y + h > toolbarTop) {
			y = Math.max(2, toolbarTop - h);
		}
		return new int[]{EditorTheme.MARGIN, y, railWidth(), h};
	}

	/** The clickable "Widgets" caption that rolls the rail up and down. */
	private int[] railHeaderRect() {
		int[] r = railRect();
		return new int[]{r[0], r[1], r[2], EditorTheme.RAIL_HEADER_H};
	}

	/** Adds an inspector widget, remembering it so {@link #render} can ghost it with the panel. */
	private <T extends AbstractWidget> T inspectorChild(T widget) {
		inspectorWidgets.add(widget);
		return this.addRenderableWidget(widget);
	}

	private void toggleRail() {
		boolean collapse = railRoll.target() != 0.0;
		railRoll.retarget(collapse ? 0.0 : 1.0, System.currentTimeMillis());
		LabsAddonsConfig.get().hudEditorRailCollapsed = collapse;
		LabsAddonsConfig.get().save();
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
				|| my >= this.height - toolbarH();
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
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
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
			if (clickAbilityRow(mx, my)) {
				return true;
			}
			if (panel != null && contains(panel, mx, my)) {
				return true;
			}
		}

		// Rescue: a selected widget hidden under the rail/inspector can still be
		// grabbed and dragged out (e.g. a left-edge widget whose default position
		// sits beneath the Widgets rail). The open-area body pass below is
		// unchanged; this only adds the under-chrome case for selected widgets.
		// The rail header is never rescued out from under: it is the only way back to an
		// expanded rail, so a selected widget parked there must not swallow the click.
		if (!shift && overUi(mx, my) && !contains(railHeaderRect(), mx, my)) {
			for (HudObject sel : selection) {
				if (contains(sel.screenBounds(this.width, this.height, true), mx, my)) {
					startGroupDrag(sel, mx, my);
					return true;
				}
			}
		}

		List<HudObject> objects = HudObjects.all();
		// Rail clicks, gated on the rail as it currently stands: rolled up (or still
		// rolling), the rows below the visible edge belong to the canvas, not the rail.
		if (contains(railRect(), mx, my)) {
			if (contains(railHeaderRect(), mx, my)) {
				toggleRail();
				return true;
			}
			for (int i = 0; i < objects.size(); i++) {
				int[] row = layerRowRect(i);
				if (contains(row, mx, my)) {
					HudObject obj = objects.get(i);
					int[] tb = toggleRect(row);
					if (contains(tb, mx, my)) {
						HudObjectSettings settings = obj.settings();
						settings.enabled = !settings.enabled;
						if (selection.contains(obj)) {
							rebuildWidgets();
						}
					} else if (shift) {
						toggleSelection(obj);
					} else {
						selectOnly(obj);
					}
					return true;
				}
			}
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
	public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
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
		scale = Mth.clamp(scale, HudObjectSettings.MIN_SCALE, HudObjectSettings.MAX_SCALE);
		if (snap) {
			float nearest = Mth.clamp(Math.round(scale / SCALE_STEP) * SCALE_STEP,
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
		int dx = Mth.clamp(rawX - grabOriginX, -groupMinX, this.width - groupMaxX);
		int dy = Mth.clamp(rawY - grabOriginY, -groupMinY, this.height - groupMaxY);
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
	public boolean keyPressed(KeyEvent input) {
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
				lastNudgeMs = System.currentTimeMillis();
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
		dx = Mth.clamp(dx, -minX, this.width - maxX);
		dy = Mth.clamp(dy, -minY, this.height - maxY);
		for (HudObject obj : selection) {
			int[] b = obj.screenBounds(this.width, this.height, true);
			obj.setScreenBoxPosition(b[0] + dx, b[1] + dy, this.width, this.height);
		}
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent click) {
		if (marqueeing) {
			if (!marqueeMoved && !marqueeAdditive) {
				selection.clear();
			}
			marqueeing = false;
			marqueeBase = null;
			rebuildWidgets();
		}
		groupDragging = false;
		resizing = false;
		guideX = null;
		guideY = null;
		return super.mouseReleased(click);
	}

	/**
	 * Rebuilds the rail and inspector after the HUD profile changed underneath us —
	 * every widget's settings object is a different instance now.
	 */
	public void refreshForProfileChange() {
		builtForProfile = LabsAddonsConfig.get().activeProfile;
		this.rebuildWidgets();
	}

	@Override
	public void onClose() {
		// saveNow, not save: leaving the editor is when a layout edit must be on disk.
		LabsAddonsConfig.get().saveNow();
		if (this.minecraft != null) {
			this.minecraft.setScreenAndShow(this.parent);
		}
	}
}
