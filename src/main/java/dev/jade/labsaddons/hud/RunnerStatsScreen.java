package dev.jade.labsaddons.hud;

import com.mojang.authlib.GameProfile;
import dev.jade.labsaddons.hud.editor.EditorPainter;
import dev.jade.labsaddons.hud.editor.EditorTheme;
import dev.jade.labsaddons.runner.RunnerHudObject;
import dev.jade.labsaddons.runner.RunnerLeaderboard;
import dev.jade.labsaddons.runner.RunnerTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Full-screen runner leaderboard, opened from the HUD Studio's "Stats" button.
 * Rows are the per-runner all-time stats persisted in
 * {@link dev.jade.labsaddons.config.LabsAddonsConfig#runnerStats}, ranked by
 * {@link RunnerLeaderboard}. Each row shows the runner's head; hovering renders
 * their 3D model, running in place and turned toward the panel, on the right; clicking opens their
 * {@link RunnerJobsScreen} history. Hand-drawn (the mod has no scrollable list
 * widget) with a simple scroll offset; reset goes through a {@link ConfirmScreen}.
 */
public class RunnerStatsScreen extends Screen {
	private static final int PAD = 12;
	private static final int TITLE_H = 18;
	private static final int COL_HEADER_H = 14;
	private static final int ROW_H = 15;
	private static final int BUTTON_H = 20;
	private static final int TOP_MARGIN = 24;
	private static final int MAX_CONTENT_W = 420;
	private static final int HEAD_SIZE = 11;
	private static final int MODEL_BOX_W = 120;
	private static final int MODEL_BOX_H = 190;
	private static final int MODEL_SIZE = 72;
	private static final int MODEL_MARGIN = 12;
	private static final float LIMB_SPEED = 0.9f;
	/** One game tick — {@link net.minecraft.entity.LimbAnimator#updateLimbs} is meant to be
	 *  called this often (that's how {@link net.minecraft.entity.LivingEntity#tick()} drives
	 *  it); calling it once per render frame instead plays the run cycle at frame rate. */
	private static final long LIMB_TICK_MS = 50L;

	// Column x offsets within the content area.
	private static final int COL_RANK = 0;
	private static final int COL_HEAD = 18;
	private static final int COL_NAME = 32;
	private static final int COL_DONE = 134;
	private static final int COL_FAIL = 178;
	private static final int COL_RATE = 216;
	private static final int COL_AVG = 304;
	private static final int COL_VALUE = 364;

	private static final int C_RANK = EditorTheme.TEXT_DIM;
	private static final int C_RANK_TOP = EditorTheme.TEXT_ACCENT;
	private static final int C_NAME = EditorTheme.TEXT;
	private static final int C_DONE = 0xFF55FF55;
	private static final int C_FAIL = 0xFFFF5555;
	private static final int C_RATE = EditorTheme.TEXT;
	private static final int C_AVG = EditorTheme.TEXT_DIM;
	private static final int C_VALUE = 0xFF80FF80;
	private static final int ROW_ALT = 0x11FFFFFF;

	private final Screen parent;
	private final Map<String, OtherClientPlayerEntity> entityCache = new HashMap<>();
	private int scrollOffset = 0;
	private int lastMaxScroll = 0;
	private long lastLimbTickMs = 0L;

	// Geometry + entries cached each frame for row hit-testing / hover.
	private List<RunnerLeaderboard.Entry> lastEntries = List.of();
	private int rowsX;
	private int rowsW;
	private int viewTop;
	private int viewBottom;
	private String hoveredRunner;

	public RunnerStatsScreen(Screen parent) {
		super(Text.translatable("labsaddons.hud.stats.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int cardW = cardW();
		int cardX = (this.width - cardW) / 2;
		int cardH = this.height - TOP_MARGIN * 2;
		int contentX = cardX + PAD;
		int contentW = cardW - 2 * PAD;
		int buttonsY = TOP_MARGIN + cardH - PAD - BUTTON_H;
		int halfW = (contentW - 8) / 2;

		this.addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> this.close())
				.dimensions(contentX, buttonsY, halfW, BUTTON_H).build());
		this.addDrawableChild(ButtonWidget.builder(
						Text.translatable("labsaddons.hud.stats.reset"), b -> openResetConfirm())
				.dimensions(contentX + halfW + 8, buttonsY, contentW - halfW - 8, BUTTON_H).build());
	}

	private int cardW() {
		return Math.min(MAX_CONTENT_W + 2 * PAD, this.width - 20);
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		super.renderBackground(context, mouseX, mouseY, delta);

		int cardW = cardW();
		int cardX = (this.width - cardW) / 2;
		int cardY = TOP_MARGIN;
		int cardH = this.height - TOP_MARGIN * 2;
		int contentX = cardX + PAD;
		int contentW = cardW - 2 * PAD;
		int headerY = cardY + PAD + TITLE_H;
		int viewportTop = headerY + COL_HEADER_H;
		int buttonsY = cardY + cardH - PAD - BUTTON_H;
		int viewportBottom = buttonsY - 6;

		EditorPainter.panel(context, new int[] {cardX, cardY, cardW, cardH},
				EditorTheme.PANEL_BG, EditorTheme.PANEL_BORDER);
		context.drawCenteredTextWithShadow(this.textRenderer,
				Text.translatable("labsaddons.hud.stats.title"), this.width / 2, cardY + PAD, EditorTheme.TITLE);

		drawHeader(context, contentX, headerY);
		context.fill(contentX, viewportTop - 2, contentX + contentW, viewportTop - 1, EditorTheme.PANEL_BORDER);

		List<RunnerLeaderboard.Entry> entries = RunnerLeaderboard.ranked(RunnerTracker.statsSnapshot());
		this.lastEntries = entries;
		this.rowsX = contentX;
		this.rowsW = contentW;
		this.viewTop = viewportTop;
		this.viewBottom = viewportBottom;
		this.hoveredRunner = null;

		if (entries.isEmpty()) {
			context.drawCenteredTextWithShadow(this.textRenderer,
					Text.translatable("labsaddons.hud.stats.empty"),
					this.width / 2, viewportTop + (viewportBottom - viewportTop) / 2 - 4, EditorTheme.TEXT_DIM);
			lastMaxScroll = 0;
			scrollOffset = 0;
			return;
		}

		int rowsAreaH = Math.max(0, viewportBottom - viewportTop);
		int contentH = entries.size() * ROW_H;
		lastMaxScroll = Math.max(0, contentH - rowsAreaH);
		scrollOffset = MathHelper.clamp(scrollOffset, 0, lastMaxScroll);
		int hoverIdx = rowIndexAt(mouseX, mouseY);

		context.enableScissor(contentX, viewportTop, contentX + contentW, viewportBottom);
		int y = viewportTop - scrollOffset;
		int rank = 1;
		for (RunnerLeaderboard.Entry entry : entries) {
			if (y + ROW_H >= viewportTop && y <= viewportBottom) {
				drawRow(context, contentX, y, contentW, rank, entry, rank - 1 == hoverIdx);
			}
			y += ROW_H;
			rank++;
		}
		context.disableScissor();

		if (hoverIdx >= 0 && hoverIdx < entries.size()) {
			this.hoveredRunner = entries.get(hoverIdx).name();
			renderRunnerModel(context, this.hoveredRunner, cardX + cardW);
		}
	}

	private void drawHeader(DrawContext context, int x, int y) {
		cell(context, x + COL_RANK, y, "labsaddons.hud.stats.col.rank");
		cell(context, x + COL_NAME, y, "labsaddons.hud.stats.col.runner");
		cell(context, x + COL_DONE, y, "labsaddons.hud.stats.col.completed");
		cell(context, x + COL_FAIL, y, "labsaddons.hud.stats.col.failed");
		cell(context, x + COL_RATE, y, "labsaddons.hud.stats.col.rate");
		cell(context, x + COL_AVG, y, "labsaddons.hud.stats.col.avg_time");
		cell(context, x + COL_VALUE, y, "labsaddons.hud.stats.col.value");
	}

	private void cell(DrawContext context, int x, int y, String langKey) {
		context.drawText(this.textRenderer, Text.translatable(langKey), x, y, EditorTheme.TEXT_DIM, false);
	}

	private void drawRow(DrawContext context, int x, int y, int contentW, int rank,
			RunnerLeaderboard.Entry entry, boolean hovered) {
		if (hovered) {
			context.fill(x, y - 2, x + contentW, y + ROW_H - 2, EditorTheme.ROW_HOVER);
		} else if (rank % 2 == 0) {
			context.fill(x, y - 2, x + contentW, y + ROW_H - 2, ROW_ALT);
		}
		PlayerSkinDrawer.draw(context, PlayerSkinCache.skin(entry.name()), x + COL_HEAD, y + 1, HEAD_SIZE);

		String name = this.textRenderer.trimToWidth(entry.name(), COL_DONE - COL_NAME - 4);
		double rate = entry.stats().successRate() * 100.0;
		String avg = entry.stats().completed > 0 ? formatDuration(entry.stats().avgTimeMs()) : "—";
		int ty = y + 3;

		text(context, x + COL_RANK, ty, String.valueOf(rank), rank <= 3 ? C_RANK_TOP : C_RANK);
		text(context, x + COL_NAME, ty, name, C_NAME);
		text(context, x + COL_DONE, ty, String.valueOf(entry.stats().completed), C_DONE);
		text(context, x + COL_FAIL, ty, String.valueOf(entry.stats().failed), C_FAIL);
		text(context, x + COL_RATE, ty, String.format(Locale.US, "%.1f%%", rate), C_RATE);
		text(context, x + COL_AVG, ty, avg, C_AVG);
		text(context, x + COL_VALUE, ty, "$" + RunnerHudObject.formatMoney(entry.stats().valueSold), C_VALUE);
	}

	private void text(DrawContext context, int x, int y, String s, int color) {
		context.drawText(this.textRenderer, Text.literal(s), x, y, color, true);
	}

	/**
	 * Draws the hovered runner's 3D model, running in place, to the right of the
	 * panel. Pinned to the window's right edge (not the panel's right edge) so it
	 * always stays on screen even when the panel takes up most of the width.
	 * {@code drawEntity} turns the whole model toward the given look point, so
	 * aiming it at the panel gives a slight lean/turn toward the leaderboard
	 * instead of a straight-on stare.
	 */
	private void renderRunnerModel(DrawContext context, String runner, int panelRight) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) {
			return;
		}
		GameProfile profile = PlayerSkinCache.profile(runner);
		if (profile == null) {
			return; // not resolved yet — nothing to render this frame
		}
		OtherClientPlayerEntity entity = entityCache.computeIfAbsent(runner,
				n -> new OtherClientPlayerEntity(client.world, profile));

		// Advance the run cycle once per game tick, same as LivingEntity.tick() would —
		// not once per render call, which played it back at frame rate instead of real time.
		long now = System.currentTimeMillis();
		if (now - lastLimbTickMs >= LIMB_TICK_MS) {
			entity.limbAnimator.updateLimbs(LIMB_SPEED, 0.4f, 1.0f);
			lastLimbTickMs = now;
		}

		int x2 = this.width - MODEL_MARGIN;
		int x1 = x2 - MODEL_BOX_W;
		int cy = this.height / 2;
		int y1 = cy - MODEL_BOX_H / 2;
		int y2 = cy + MODEL_BOX_H / 2;
		float lookX = panelRight;
		float lookY = (y1 + y2) / 2.0f;
		InventoryScreen.drawEntity(context, x1, y1, x2, y2, MODEL_SIZE, 0.0f, lookX, lookY, entity);
	}

	/** Row index under (mouseX,mouseY) within the viewport, or -1. */
	private int rowIndexAt(double mouseX, double mouseY) {
		if (lastEntries.isEmpty()
				|| mouseX < rowsX || mouseX > rowsX + rowsW
				|| mouseY < viewTop || mouseY >= viewBottom) {
			return -1;
		}
		int idx = ((int) mouseY - viewTop + scrollOffset) / ROW_H;
		return idx >= 0 && idx < lastEntries.size() ? idx : -1;
	}

	/** mm:ss up to an hour, then h/m. */
	private static String formatDuration(long ms) {
		long totalSec = ms / 1000;
		long m = totalSec / 60;
		long s = totalSec % 60;
		if (m >= 60) {
			return String.format(Locale.US, "%dh %02dm", m / 60, m % 60);
		}
		return String.format(Locale.US, "%dm %02ds", m, s);
	}

	private void openResetConfirm() {
		if (this.client == null) {
			return;
		}
		this.client.setScreen(new ConfirmScreen(confirmed -> {
			if (confirmed) {
				RunnerTracker.resetLeaderboard();
			}
			this.client.setScreen(this);
		}, Text.translatable("labsaddons.hud.stats.reset.title"),
				Text.translatable("labsaddons.hud.stats.reset.message")));
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		if (super.mouseClicked(click, doubled)) {
			return true;
		}
		if (click.button() == 0 && this.client != null) {
			int idx = rowIndexAt(click.x(), click.y());
			if (idx >= 0) {
				this.client.setScreen(new RunnerJobsScreen(this, lastEntries.get(idx).name()));
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		scrollOffset = MathHelper.clamp(
				scrollOffset - (int) Math.round(verticalAmount) * ROW_H * 2, 0, lastMaxScroll);
		return true;
	}

	@Override
	public void removed() {
		entityCache.clear();
	}

	@Override
	public void close() {
		if (this.client != null) {
			this.client.setScreen(this.parent);
		}
	}
}
