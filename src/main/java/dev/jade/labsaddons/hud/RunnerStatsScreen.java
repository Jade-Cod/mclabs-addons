package dev.jade.labsaddons.hud;

import com.mojang.authlib.GameProfile;
import dev.jade.labsaddons.config.RunnerJob;
import dev.jade.labsaddons.hud.editor.EditorPainter;
import dev.jade.labsaddons.hud.editor.EditorTheme;
import dev.jade.labsaddons.runner.RunnerHudObject;
import dev.jade.labsaddons.runner.RunnerLeaderboard;
import dev.jade.labsaddons.runner.RunnerTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Full-screen runner leaderboard, opened from the HUD Studio's "Stats" button.
 * Rows are the per-runner all-time stats persisted in
 * {@link dev.jade.labsaddons.config.LabsAddonsConfig#runnerStats}, ranked by
 * {@link RunnerLeaderboard}. Each row shows the runner's head; hovering renders
 * their 3D model, running in place and turned toward the panel, on the right;
 * clicking a row rolls an inline "shutter" panel of their 10 most recent
 * completed jobs (paged) down directly beneath it, pushing the rows below it
 * further down. The model keeps running for that runner even while a
 * different row is hovered, until the shutter finishes rolling back up.
 * Hand-drawn (the mod has no scrollable list widget) with a simple scroll
 * offset; reset goes through a {@link ConfirmScreen}.
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
	 *  called this often (that's how {@link net.minecraft.world.entity.LivingEntity#tick()} drives
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

	// The inline "shutter" job-history accordion embedded under an expanded row.
	// Sized dynamically per runner (see accordionTargetHeight) instead of a fixed
	// 10-row block, so a runner with fewer jobs than a full page doesn't reserve
	// empty space underneath their list.
	private static final long ACCORDION_ANIM_MS = 400L;
	private static final int ACCORDION_HEADER_H = 14;
	private static final int ACCORDION_ROW_H = 14;
	private static final int ACCORDION_PER_PAGE = 10;
	private static final int ACCORDION_FOOTER_H = 20;
	private static final int ACCORDION_TOP_INSET = 4;
	private static final int ACCORDION_FOOTER_GAP = 6;
	private static final int ACCORDION_BOTTOM_GAP = 6;
	private static final int ACCORDION_EMPTY_H = 24;
	private static final int ACCORDION_BUTTON_W = 20;
	private static final int ACCORDION_BUTTON_GAP = 4;
	private static final int PAGE_TEXT_GAP = 8;

	// Job-table columns within the accordion, ported from the old per-runner jobs screen.
	private static final int JCOL_JOB = 0;
	private static final int JCOL_VALUE = 150;
	private static final int JCOL_TIME = 212;
	private static final int JCOL_DATE = 274;

	private static final DateTimeFormatter DATE_FMT =
			DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.US).withZone(ZoneId.systemDefault());

	private final Screen parent;
	private final Map<String, RemotePlayer> entityCache = new HashMap<>();
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

	// The one row currently opening/open (target height 1.0), plus any rows still
	// rolling shut after being deselected — a list, not a single slot, so rapid
	// clicking across rows doesn't snap an earlier close instead of finishing it.
	private Accordion openAccordion;
	private final List<Accordion> closingAccordions = new ArrayList<>();
	private Button accordionPrevButton;
	private Button accordionNextButton;

	/** One row's shutter animation state, keyed by runner name so it survives re-ranking. */
	private static final class Accordion {
		final String runner;
		double animFrom;
		long animStartMs;
		double target;
		int pageIndex;

		Accordion(String runner, long now) {
			this.runner = runner;
			this.animFrom = 0.0;
			this.animStartMs = now;
			this.target = 1.0;
			this.pageIndex = 0;
		}

		float progress() {
			float t = Math.min(1f, (System.currentTimeMillis() - animStartMs) / (float) ACCORDION_ANIM_MS);
			float eased = 1f - (1f - t) * (1f - t);
			return (float) (animFrom + (target - animFrom) * eased);
		}

		/** Captures the current instantaneous progress before flipping direction, so a
		 *  reversed animation eases from wherever it actually was instead of snapping. */
		void retarget(double newTarget, long now) {
			if (target != newTarget) {
				animFrom = progress();
				animStartMs = now;
				target = newTarget;
			}
		}
	}

	public RunnerStatsScreen(Screen parent) {
		super(Component.translatable("labsaddons.hud.stats.title"));
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

		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
				.bounds(contentX, buttonsY, halfW, BUTTON_H).build());
		this.addRenderableWidget(Button.builder(
						Component.translatable("labsaddons.hud.stats.reset"), b -> openResetConfirm())
				.bounds(contentX + halfW + 8, buttonsY, contentW - halfW - 8, BUTTON_H).build());

		this.accordionPrevButton = Button.builder(
						Component.translatable("labsaddons.hud.jobs.prev"), b -> turnAccordionPage(-1))
				.bounds(0, 0, ACCORDION_BUTTON_W, ACCORDION_FOOTER_H).build();
		this.accordionNextButton = Button.builder(
						Component.translatable("labsaddons.hud.jobs.next"), b -> turnAccordionPage(1))
				.bounds(0, 0, ACCORDION_BUTTON_W, ACCORDION_FOOTER_H).build();
		this.accordionPrevButton.visible = false;
		this.accordionNextButton.visible = false;
		this.addRenderableWidget(this.accordionPrevButton);
		this.addRenderableWidget(this.accordionNextButton);
	}

	private int cardW() {
		return Math.min(MAX_CONTENT_W + 2 * PAD, this.width - 20);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		super.extractBackground(context, mouseX, mouseY, delta);
		pruneClosingAccordions();

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
		context.centeredText(this.font,
				Component.translatable("labsaddons.hud.stats.title"), this.width / 2, cardY + PAD, EditorTheme.TITLE);

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
			context.centeredText(this.font,
					Component.translatable("labsaddons.hud.stats.empty"),
					this.width / 2, viewportTop + (viewportBottom - viewportTop) / 2 - 4, EditorTheme.TEXT_DIM);
			lastMaxScroll = 0;
			scrollOffset = 0;
			accordionPrevButton.visible = false;
			accordionNextButton.visible = false;
			return;
		}

		int rowsAreaH = Math.max(0, viewportBottom - viewportTop);
		int contentH = 0;
		for (RunnerLeaderboard.Entry entry : entries) {
			contentH += ROW_H + extraHeightFor(entry.name());
		}
		lastMaxScroll = Math.max(0, contentH - rowsAreaH);
		scrollOffset = Mth.clamp(scrollOffset, 0, lastMaxScroll);
		int hoverIdx = rowIndexAt(mouseX, mouseY);

		context.enableScissor(contentX, viewportTop, contentX + contentW, viewportBottom);
		int y = viewportTop - scrollOffset;
		int rank = 1;
		int openAccordionY = Integer.MIN_VALUE;
		for (RunnerLeaderboard.Entry entry : entries) {
			int extra = extraHeightFor(entry.name());
			int slotH = ROW_H + extra;
			if (openAccordion != null && openAccordion.runner.equals(entry.name())) {
				openAccordionY = y;
			}
			if (y + slotH >= viewportTop && y <= viewportBottom) {
				drawRow(context, contentX, y, contentW, rank, entry, rank - 1 == hoverIdx);
				if (extra > 0) {
					drawAccordionShutter(context, contentX, y + ROW_H, contentW, extra, entry, viewportTop, viewportBottom);
				}
			}
			y += slotH;
			rank++;
		}
		context.disableScissor();

		positionAccordionButtons(openAccordionY, contentX, contentW, viewportTop, viewportBottom);

		if (hoverIdx >= 0 && hoverIdx < entries.size()) {
			this.hoveredRunner = entries.get(hoverIdx).name();
		}
		String pinned = pinnedModelRunner();
		String modelRunner = pinned != null ? pinned : hoveredRunner;
		if (modelRunner != null) {
			renderRunnerModel(context, modelRunner, cardX + cardW);
		}
	}

	private void drawHeader(GuiGraphicsExtractor context, int x, int y) {
		cell(context, x + COL_RANK, y, "labsaddons.hud.stats.col.rank");
		cell(context, x + COL_NAME, y, "labsaddons.hud.stats.col.runner");
		cell(context, x + COL_DONE, y, "labsaddons.hud.stats.col.completed");
		cell(context, x + COL_FAIL, y, "labsaddons.hud.stats.col.failed");
		cell(context, x + COL_RATE, y, "labsaddons.hud.stats.col.rate");
		cell(context, x + COL_AVG, y, "labsaddons.hud.stats.col.avg_time");
		cell(context, x + COL_VALUE, y, "labsaddons.hud.stats.col.value");
	}

	private void cell(GuiGraphicsExtractor context, int x, int y, String langKey) {
		context.text(this.font, Component.translatable(langKey), x, y, EditorTheme.TEXT_DIM, false);
	}

	private void drawRow(GuiGraphicsExtractor context, int x, int y, int contentW, int rank,
			RunnerLeaderboard.Entry entry, boolean hovered) {
		if (hovered) {
			context.fill(x, y - 2, x + contentW, y + ROW_H - 2, EditorTheme.ROW_HOVER);
		} else if (rank % 2 == 0) {
			context.fill(x, y - 2, x + contentW, y + ROW_H - 2, ROW_ALT);
		}
		PlayerFaceExtractor.extractRenderState(context, PlayerSkinCache.skin(entry.name()), x + COL_HEAD, y + 1, HEAD_SIZE);

		String name = this.font.plainSubstrByWidth(entry.name(), COL_DONE - COL_NAME - 4);
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

	private void text(GuiGraphicsExtractor context, int x, int y, String s, int color) {
		context.text(this.font, Component.literal(s), x, y, color, true);
	}

	/** Currently animated (opening, open, or still-closing) extra height for {@code runner}, or 0. */
	private int extraHeightFor(String runner) {
		if (openAccordion != null && openAccordion.runner.equals(runner)) {
			return Math.round(openAccordion.progress() * accordionTargetHeight(runner));
		}
		for (Accordion a : closingAccordions) {
			if (a.runner.equals(runner)) {
				return Math.round(a.progress() * accordionTargetHeight(runner));
			}
		}
		return 0;
	}

	/** Height {@code runner}'s shutter needs when fully open, sized to the jobs actually
	 *  shown on its current page — not a fixed 10-row block — so a runner with fewer jobs
	 *  than a full page (or a shorter final page) doesn't reserve empty space underneath. */
	private int accordionTargetHeight(String runner) {
		int jobCount = RunnerTracker.recentJobs(runner).size();
		if (jobCount == 0) {
			return ACCORDION_TOP_INSET + ACCORDION_EMPTY_H;
		}
		Accordion state = accordionStateFor(runner);
		int pageIndex = state != null ? state.pageIndex : 0;
		int start = pageIndex * ACCORDION_PER_PAGE;
		int rowsShown = Math.max(0, Math.min(ACCORDION_PER_PAGE, jobCount - start));
		return ACCORDION_TOP_INSET + ACCORDION_HEADER_H + 2
				+ rowsShown * ACCORDION_ROW_H
				+ ACCORDION_FOOTER_GAP + ACCORDION_FOOTER_H + ACCORDION_BOTTOM_GAP;
	}

	private Accordion accordionStateFor(String runner) {
		if (openAccordion != null && openAccordion.runner.equals(runner)) {
			return openAccordion;
		}
		for (Accordion a : closingAccordions) {
			if (a.runner.equals(runner)) {
				return a;
			}
		}
		return null;
	}

	private void pruneClosingAccordions() {
		long now = System.currentTimeMillis();
		closingAccordions.removeIf(a -> a.target == 0.0 && now - a.animStartMs >= ACCORDION_ANIM_MS);
	}

	/**
	 * Draws {@code entry}'s job-history shutter beneath its row. Content is always
	 * drawn at the offsets it would occupy fully open; a scissor narrowed to the
	 * currently animated height does the actual "rolling" reveal. {@code GuiGraphicsExtractor}'s
	 * scissor is a stack, not a single rect — {@code disableScissor} pops back to
	 * whatever was active before this call (the outer viewport scissor from the row
	 * loop), so the row loop can keep clipping normally for the rows that follow.
	 */
	private void drawAccordionShutter(GuiGraphicsExtractor context, int x, int y, int w, int h,
			RunnerLeaderboard.Entry entry, int viewTop, int viewBottom) {
		int clipTop = Math.max(y, viewTop);
		int clipBottom = Math.min(y + h, viewBottom);
		if (clipBottom <= clipTop) {
			return;
		}
		context.enableScissor(x, clipTop, x + w, clipBottom);
		drawAccordionContent(context, x, y, w, entry);
		context.disableScissor();
	}

	private void drawAccordionContent(GuiGraphicsExtractor context, int x, int y, int w, RunnerLeaderboard.Entry entry) {
		Accordion state = accordionStateFor(entry.name());
		if (state == null) {
			return;
		}
		List<RunnerJob> jobs = RunnerTracker.recentJobs(entry.name());
		if (jobs.isEmpty()) {
			context.centeredText(this.font,
					Component.translatable("labsaddons.hud.jobs.empty"),
					x + w / 2, y + ACCORDION_TOP_INSET + 6, EditorTheme.TEXT_DIM);
			return;
		}

		int headerY = y + ACCORDION_TOP_INSET;
		int rowsTop = headerY + ACCORDION_HEADER_H;
		cell(context, x + JCOL_JOB, headerY, "labsaddons.hud.jobs.col.job");
		cell(context, x + JCOL_VALUE, headerY, "labsaddons.hud.jobs.col.value");
		cell(context, x + JCOL_TIME, headerY, "labsaddons.hud.jobs.col.time");
		cell(context, x + JCOL_DATE, headerY, "labsaddons.hud.jobs.col.date");
		context.fill(x, rowsTop - 2, x + w, rowsTop - 1, EditorTheme.PANEL_BORDER);

		int pageCount = accordionPageCount(entry.name());
		state.pageIndex = Math.min(state.pageIndex, pageCount - 1);
		int start = state.pageIndex * ACCORDION_PER_PAGE;
		int end = Math.min(jobs.size(), start + ACCORDION_PER_PAGE);
		int ry = rowsTop + 2;
		for (int i = start; i < end; i++) {
			drawJobRow(context, x, ry, w, (i - start) % 2 == 1, jobs.get(i));
			ry += ACCORDION_ROW_H;
		}

		int footerY = y + accordionTargetHeight(entry.name()) - ACCORDION_BOTTOM_GAP - ACCORDION_FOOTER_H;
		drawPageIndicator(context, x, w, footerY, state.pageIndex + 1, pageCount);
	}

	/** Right-aligns the "Page X/Y" label a fixed gap before the `<`/`>` buttons, measuring
	 *  the actual text width instead of a hand-tuned offset — a wider label (bigger page
	 *  counts) can't drift into the buttons this way. */
	private void drawPageIndicator(GuiGraphicsExtractor context, int x, int w, int footerY, int page, int pageCount) {
		Component label = Component.translatable("labsaddons.hud.jobs.page", page, pageCount);
		int buttonsLeft = x + w - ACCORDION_BUTTON_W - ACCORDION_BUTTON_GAP - ACCORDION_BUTTON_W;
		int textRight = buttonsLeft - PAGE_TEXT_GAP;
		context.text(this.font, label,
				textRight - this.font.width(label), footerY + 6, EditorTheme.TEXT_DIM);
	}

	private void drawJobRow(GuiGraphicsExtractor context, int x, int y, int contentW, boolean alt, RunnerJob job) {
		if (alt) {
			context.fill(x, y - 2, x + contentW, y + ACCORDION_ROW_H - 2, ROW_ALT);
		}
		String jobLabel = job.qty > 0 && !job.drug.isEmpty()
				? job.qty + "x " + job.drug
				: (job.drug.isEmpty() ? "—" : job.drug);
		jobLabel = this.font.plainSubstrByWidth(jobLabel, JCOL_VALUE - JCOL_JOB - 4);
		String time = job.durationMs > 0 ? formatDuration(job.durationMs) : "—";
		String date = job.completedMs > 0 ? DATE_FMT.format(Instant.ofEpochMilli(job.completedMs)) : "—";

		text(context, x + JCOL_JOB, y, jobLabel, EditorTheme.TEXT);
		text(context, x + JCOL_VALUE, y, "$" + RunnerHudObject.formatMoney(job.value), C_VALUE);
		text(context, x + JCOL_TIME, y, time, EditorTheme.TEXT_DIM);
		text(context, x + JCOL_DATE, y, date, EditorTheme.TEXT_DIM);
	}

	private int accordionPageCount(String runner) {
		return Math.max(1, (RunnerTracker.recentJobs(runner).size() + ACCORDION_PER_PAGE - 1) / ACCORDION_PER_PAGE);
	}

	private void turnAccordionPage(int delta) {
		if (openAccordion == null) {
			return;
		}
		int pageCount = accordionPageCount(openAccordion.runner);
		openAccordion.pageIndex = Math.max(0, Math.min(openAccordion.pageIndex + delta, pageCount - 1));
	}

	/** Repositions the paging buttons to track the open accordion's footer live, following
	 *  the shutter down as it rolls open rather than popping in once it's fully open — so
	 *  they read as part of the same motion instead of appearing to "come from nowhere". */
	private void positionAccordionButtons(int slotY, int contentX, int contentW, int viewTop, int viewBottom) {
		if (openAccordion == null) {
			accordionPrevButton.visible = false;
			accordionNextButton.visible = false;
			return;
		}
		int extra = extraHeightFor(openAccordion.runner);
		int footerY = Math.max(slotY + ROW_H, slotY + ROW_H + extra - ACCORDION_BOTTOM_GAP - ACCORDION_FOOTER_H);
		if (footerY < viewTop || footerY + ACCORDION_FOOTER_H > viewBottom) {
			accordionPrevButton.visible = false;
			accordionNextButton.visible = false;
			return;
		}
		accordionPrevButton.setX(contentX + contentW - ACCORDION_BUTTON_W - ACCORDION_BUTTON_GAP - ACCORDION_BUTTON_W);
		accordionPrevButton.setY(footerY);
		accordionNextButton.setX(contentX + contentW - ACCORDION_BUTTON_W);
		accordionNextButton.setY(footerY);
		accordionPrevButton.visible = true;
		accordionNextButton.visible = true;
		int pageCount = accordionPageCount(openAccordion.runner);
		accordionPrevButton.active = openAccordion.pageIndex > 0;
		accordionNextButton.active = openAccordion.pageIndex < pageCount - 1;
	}

	/** Whichever runner's model should be pinned visible regardless of hover — the row
	 *  currently expanded/opening, or (if none) the most recently deselected still-closing
	 *  row, so the model keeps running until its shutter finishes rolling up. */
	private String pinnedModelRunner() {
		if (openAccordion != null) {
			return openAccordion.runner;
		}
		if (!closingAccordions.isEmpty()) {
			return closingAccordions.get(closingAccordions.size() - 1).runner;
		}
		return null;
	}

	/**
	 * Draws the given runner's 3D model, running in place, to the right of the
	 * panel. Pinned to the window's right edge (not the panel's right edge) so it
	 * always stays on screen even when the panel takes up most of the width.
	 * {@code drawEntity} turns the whole model toward the given look point, so
	 * aiming it at the panel gives a slight lean/turn toward the leaderboard
	 * instead of a straight-on stare.
	 */
	private void renderRunnerModel(GuiGraphicsExtractor context, String runner, int panelRight) {
		if (minecraft.level == null) {
			return;
		}
		GameProfile profile = PlayerSkinCache.profile(runner);
		if (profile == null) {
			return; // not resolved yet — nothing to render this frame
		}
		RemotePlayer entity = entityCache.computeIfAbsent(runner,
				n -> new RemotePlayer(minecraft.level, profile));

		// Advance the run cycle once per game tick, same as LivingEntity.tick() would —
		// not once per render call, which played it back at frame rate instead of real time.
		long now = System.currentTimeMillis();
		if (now - lastLimbTickMs >= LIMB_TICK_MS) {
			entity.walkAnimation.update(LIMB_SPEED, 0.4f, 1.0f);
			lastLimbTickMs = now;
		}

		int x2 = this.width - MODEL_MARGIN;
		int x1 = x2 - MODEL_BOX_W;
		int cy = this.height / 2;
		int y1 = cy - MODEL_BOX_H / 2;
		int y2 = cy + MODEL_BOX_H / 2;
		float lookX = panelRight;
		float lookY = (y1 + y2) / 2.0f;
		InventoryScreen.extractEntityInInventoryFollowsMouse(context, x1, y1, x2, y2, MODEL_SIZE, 0.0f, lookX, lookY, entity);
	}

	/** Row index under (mouseX,mouseY) within the viewport, or -1. Only the row's own
	 *  header band is hit — its accordion body (if any) is inert text/widgets below it. */
	private int rowIndexAt(double mouseX, double mouseY) {
		if (lastEntries.isEmpty()
				|| mouseX < rowsX || mouseX > rowsX + rowsW
				|| mouseY < viewTop || mouseY >= viewBottom) {
			return -1;
		}
		double y = viewTop - scrollOffset;
		for (int i = 0; i < lastEntries.size(); i++) {
			double slotH = ROW_H + extraHeightFor(lastEntries.get(i).name());
			if (mouseY >= y && mouseY < y + slotH) {
				return mouseY < y + ROW_H ? i : -1;
			}
			y += slotH;
		}
		return -1;
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
		if (this.minecraft == null) {
			return;
		}
		this.minecraft.setScreenAndShow(new ConfirmScreen(confirmed -> {
			if (confirmed) {
				RunnerTracker.resetLeaderboard();
				openAccordion = null;
				closingAccordions.clear();
			}
			this.minecraft.setScreenAndShow(this);
		}, Component.translatable("labsaddons.hud.stats.reset.title"),
				Component.translatable("labsaddons.hud.stats.reset.message")));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		if (super.mouseClicked(click, doubled)) {
			return true;
		}
		if (click.button() == 0 && this.minecraft != null) {
			int idx = rowIndexAt(click.x(), click.y());
			if (idx >= 0) {
				toggleAccordion(lastEntries.get(idx).name());
				return true;
			}
		}
		return false;
	}

	/** Opens {@code runner}'s shutter, closing whichever other one is currently open (both
	 *  animate concurrently) — or closes it if it's the one already open. */
	private void toggleAccordion(String runner) {
		long now = System.currentTimeMillis();
		if (openAccordion != null && openAccordion.runner.equals(runner)) {
			openAccordion.retarget(0.0, now);
			closingAccordions.add(openAccordion);
			openAccordion = null;
			return;
		}
		if (openAccordion != null) {
			openAccordion.retarget(0.0, now);
			closingAccordions.add(openAccordion);
			openAccordion = null;
		}
		Accordion resumed = null;
		for (Accordion a : closingAccordions) {
			if (a.runner.equals(runner)) {
				resumed = a;
				break;
			}
		}
		if (resumed != null) {
			closingAccordions.remove(resumed);
			resumed.retarget(1.0, now); // smooth reversal from wherever it was mid-close
			openAccordion = resumed;
		} else {
			openAccordion = new Accordion(runner, now);
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		scrollOffset = Mth.clamp(
				scrollOffset - (int) Math.round(verticalAmount) * ROW_H * 2, 0, lastMaxScroll);
		return true;
	}

	@Override
	public void removed() {
		entityCache.clear();
	}

	@Override
	public void onClose() {
		if (this.minecraft != null) {
			this.minecraft.setScreenAndShow(this.parent);
		}
	}
}
