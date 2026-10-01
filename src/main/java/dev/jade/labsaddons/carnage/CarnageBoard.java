package dev.jade.labsaddons.carnage;

import dev.jade.labsaddons.carnage.CarnageMenu.Page;
import dev.jade.labsaddons.carnage.CarnageTracker.Daily;
import dev.jade.labsaddons.carnage.CarnageTracker.HuntSet;
import dev.jade.labsaddons.carnage.CarnageTracker.Mission;
import dev.jade.labsaddons.casino.CasinoPanel;
import dev.jade.labsaddons.casino.SlotView;
import dev.jade.labsaddons.config.LabsAddonsConfig;
import dev.jade.labsaddons.hud.Durations;
import dev.jade.labsaddons.hud.HudObject;
import dev.jade.labsaddons.hud.PlayerSkinCache;
import dev.jade.labsaddons.hud.TimeFormat;
import dev.jade.labsaddons.hud.editor.EditorPainter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.util.Util;

import java.util.List;
import java.util.Locale;

/**
 * The {@code /carnage} menus, redrawn: one panel with a tab per page, standing in for all six
 * of the server's chests and clicking through to their own nav slots.
 *
 * <p>Motion is kept to what explains something, and none of it waits on the player. The tab
 * underline slides to the page you picked, so the strip says where you went. A new page's
 * content rises 4px into place with a short stagger, and its bars fill from empty. Hover is
 * instant, because it happens constantly. A press flashes for {@link #PRESS_MS} because the
 * server takes a moment to answer and the click needs to show it was heard.
 *
 * <p>The server reopens the chest on every page change. {@link #holdKey} gives every Carnage
 * page the same key, so the board holds across that reopen instead of letting the real chest
 * flash through for a frame.
 */
public final class CarnageBoard extends CasinoPanel {
	public static final CarnageBoard INSTANCE = new CarnageBoard();

	// --- palette: the server's own blood red and pumpkin, on the shared dark panel ---
	private static final int PUMPKIN = 0xFFFF8C1A;
	private static final int BLOOD = 0xFFE0455A;
	private static final int GHOST = 0xFFB69CFF;
	private static final int GOLD = 0xFFFFD45E;
	private static final int SILVER = 0xFFC9D1D9;
	private static final int BRONZE = 0xFFE0915A;
	private static final int CARD = 0x12FFFFFF;
	private static final int CARD_HOT = 0x22FFFFFF;
	/** Lighter than the casino track: an empty bar has to read against the panel, not vanish into it. */
	private static final int RAIL = 0xFF333947;

	// --- layout ---
	private static final int H = 220;
	private static final int TAB_Y = 20;
	private static final int TAB_H = 12;
	private static final int BODY_Y = 40;
	private static final int BODY_W = PANEL_W - PAD * 2;
	private static final int FOOT_Y = H - PAD - 13;
	private static final int COL_W = 134;
	private static final int RIGHT_X = PANEL_W - PAD - COL_W;
	private static final int BAR_H = 4;
	private static final String[] TAB_LABELS = {"Today", "Hunt", "Shop", "Leaders", "Bestiary", "Tags"};

	// --- motion ---
	private static final long TAB_SLIDE_MS = 200;
	private static final long ENTER_MS = 220;
	private static final long STAGGER_MS = 30;
	private static final long BAR_MS = 320;
	private static final long PRESS_MS = 140;
	private static final float RISE_PX = 4f;
	/** A gap this long between frames means the menu was closed; the next frame is a fresh open. */
	private static final long REOPEN_GAP_MS = 250;
	/** Items and skins can't be drawn translucent, so they wait until their element is mostly in. */
	private static final float ICON_CUTOFF = 0.5f;

	private long now;
	private long lastFrameMs;
	private String shownTitle = "";
	private long enterMs;
	private float underlineX = -1;
	private float underlineW;
	private float slideFromX;
	private float slideFromW;
	private long slideMs;
	private int pressedSlot = -1;
	private long pressedMs;
	/** The current element's entrance, 0..1, applied to every colour drawn inside it. */
	private float alpha = 1f;

	private CarnageBoard() {
	}

	// --- what the casino panel asks ---

	@Override
	protected boolean enabled() {
		return LabsAddonsConfig.get().carnageOverlay;
	}

	@Override
	protected int panelHeight() {
		return H;
	}

	@Override
	protected boolean looksLike(AbstractContainerMenu handler) {
		return CarnageMenu.looksLike(nameOf(handler, CarnageMenu.NAV_DASHBOARD), nameOf(handler, CarnageMenu.COUNTDOWN));
	}

	@Override
	protected boolean titleAllows(String title) {
		return Page.of(title) != null;
	}

	@Override
	protected boolean parses(List<SlotView> slots) {
		return CarnageMenu.looksLike(SlotView.nameAt(slots, CarnageMenu.NAV_DASHBOARD),
				SlotView.nameAt(slots, CarnageMenu.COUNTDOWN));
	}

	@Override
	protected boolean parses(List<SlotView> slots, String title) {
		return Page.of(title) != null && parses(slots);
	}

	/** Every page the same, so the board holds across the reopen a tab click causes. */
	@Override
	protected String holdKey(String title) {
		return Page.of(title) == null ? title : "carnage";
	}

	@Override
	protected void onClick(int slot) {
		pressedSlot = slot;
		pressedMs = Util.getMillis();
	}

	// --- frame ---

	@Override
	protected void draw(GuiGraphicsExtractor context, Font font, List<SlotView> slots, String title, float deviceScale) {
		now = Util.getMillis();
		Page page = Page.of(title);
		boolean freshOpen = now - lastFrameMs > REOPEN_GAP_MS;
		lastFrameMs = now;
		if (freshOpen || !title.equals(shownTitle)) {
			enterMs = now;
			shownTitle = title;
			moveUnderline(font, page, freshOpen);
		}

		alpha = 1f;
		header(context, font, slots);
		tabs(context, font, page);
		switch (page) {
			case DASHBOARD -> dashboard(context, font, slots);
			case HUNT -> hunt(context, font, slots);
			case SHOP -> shop(context, font, slots);
			case LEADERBOARD -> leaderboard(context, font, slots, title);
			case BESTIARY -> bestiary(context, font, slots);
			case TAGS -> tags(context, font, slots, title);
		}
		alpha = 1f;
	}

	private void header(GuiGraphicsExtractor context, Font font, List<SlotView> slots) {
		context.pose().pushMatrix();
		context.pose().translate(PAD, 2);
		context.pose().scale(0.75f, 0.75f);
		context.item(new ItemStack(Items.JACK_O_LANTERN), 0, 0);
		context.pose().popMatrix();
		text(context, font, "HALLOWEEN CARNAGE", PAD + 16, 5, PUMPKIN);

		String[] stage = CarnageMenu.stageLine(slots);
		if (stage != null) {
			String left = TimeFormat.hms(Durations.parseMs(stage[1])) + " left";
			int x = PANEL_W - PAD - font.width(left);
			text(context, font, left, x, 5, TEXT_DIM);
			String name = stage[0].toUpperCase(Locale.ROOT) + "  ";
			text(context, font, name, x - font.width(name), 5, BLOOD);
		}
		context.fill(PAD, 16, PANEL_W - PAD, 17, shade(PUMPKIN, 0x40));
	}

	// --- tabs ---

	/** {x, w} of tab {@code i}: each as wide as its label, the spare width shared out evenly. */
	private static int[] tab(Font font, int i) {
		int labels = 0;
		for (String label : TAB_LABELS) {
			labels += font.width(label);
		}
		int spare = (BODY_W - labels) / TAB_LABELS.length;
		int x = PAD;
		for (int j = 0; j < i; j++) {
			x += font.width(TAB_LABELS[j]) + spare;
		}
		return new int[]{x, font.width(TAB_LABELS[i]) + spare};
	}

	private void tabs(GuiGraphicsExtractor context, Font font, Page active) {
		Page[] pages = Page.values();
		for (int i = 0; i < pages.length; i++) {
			int[] bounds = tab(font, i);
			int x = bounds[0];
			int w = bounds[1];
			boolean isActive = pages[i] == active;
			boolean hot = !isActive && hovered(x, TAB_Y, w, TAB_H + 2);
			float press = pressAmount(pages[i].navSlot);
			if (press > 0) {
				HudObject.drawRoundedRect(context, x + 1, TAB_Y - 1, w - 2, TAB_H + 2, shade(PUMPKIN, (int) (0x40 * press)));
			} else if (hot) {
				HudObject.drawRoundedRect(context, x + 1, TAB_Y - 1, w - 2, TAB_H + 2, ROW_HOVER);
			}
			String label = TAB_LABELS[i];
			text(context, font, label, x + (w - font.width(label)) / 2, TAB_Y + 2,
					isActive ? 0xFFFFFFFF : hot ? TEXT : TEXT_DIM);
			if (!isActive) {
				clickable(x, TAB_Y - 1, w, TAB_H + 2, pages[i].navSlot);
			}
		}
		context.fill(PAD, TAB_Y + TAB_H + 2, PANEL_W - PAD, TAB_Y + TAB_H + 3, DIVIDER);
		float t = ease(progress(slideMs, TAB_SLIDE_MS));
		float x = lerp(slideFromX, underlineX, t);
		float w = lerp(slideFromW, underlineW, t);
		EditorPainter.pill(context, Math.round(x), TAB_Y + TAB_H + 1, Math.round(w), 2, PUMPKIN);
	}

	/** Slides from wherever the underline is drawn right now, so a quick second click retargets smoothly. */
	private void moveUnderline(Font font, Page page, boolean jump) {
		int i = page.ordinal();
		float toW = font.width(TAB_LABELS[i]) + 6;
		int[] bounds = tab(font, i);
		float toX = bounds[0] + (bounds[1] - toW) / 2f;
		if (jump || underlineX < 0) {
			slideFromX = toX;
			slideFromW = toW;
		} else {
			float t = ease(progress(slideMs, TAB_SLIDE_MS));
			slideFromX = lerp(slideFromX, underlineX, t);
			slideFromW = lerp(slideFromW, underlineW, t);
		}
		underlineX = toX;
		underlineW = toW;
		slideMs = now;
	}

	// --- Today ---

	private void dashboard(GuiGraphicsExtractor context, Font font, List<SlotView> slots) {
		Daily daily = CarnageReader.dashboard(slots, System.currentTimeMillis());
		if (daily == null) {
			return;
		}
		int x = PAD;
		int y = BODY_Y;

		enter(context, 0);
		caption(context, font, x, y, COL_W, "DAILY SCORE", percent(daily.score(), daily.scoreGoal()));
		context.pose().pushMatrix();
		context.pose().translate(x, y + 11);
		context.pose().scale(2f, 2f);
		String score = number(daily.score());
		text(context, font, score, 0, 0, 0xFFFFFFFF);
		context.pose().popMatrix();
		text(context, font, "/ " + number(daily.scoreGoal()), x + font.width(score) * 2 + 4, y + 18, TEXT_FAINT);
		boolean goalMet = daily.score() >= daily.scoreGoal() && daily.scoreGoal() > 0;
		bar(context, x, y + 30, COL_W, fraction(daily.score(), daily.scoreGoal()), goalMet ? WIN : PUMPKIN, 0);
		String reward = loreAfter(slots, "Daily Score Goal", "Reward:");
		if (!reward.isEmpty()) {
			text(context, font, fit(font, "→ " + reward, COL_W), x, y + 38, TEXT_FAINT);
		}
		leave(context);

		y += 58;
		enter(context, 1);
		String draws = loreValue(slots, "Daily Raffle", "Drawing in:");
		caption(context, font, x, y, COL_W, "RAFFLE", draws.isEmpty() ? "" : "draws in " + TimeFormat.hms(Durations.parseMs(draws)));
		pips(context, x, y + 11, daily.maxTickets(), daily.tickets(), 11, 6, PUMPKIN);
		if (daily.tickets() < daily.maxTickets() && daily.ticketCost() > 0) {
			text(context, font, "next ticket " + number(daily.score() % daily.ticketCost()) + " / "
					+ number(daily.ticketCost()), x, y + 21, TEXT_FAINT);
		} else {
			text(context, font, "all tickets earned", x, y + 21, WIN);
		}
		leave(context);

		y += 40;
		enter(context, 2);
		caption(context, font, x, y, COL_W, "GOAL STREAK", daily.goalsDone() + " / " + daily.goalsTotal());
		pips(context, x, y + 11, daily.goalsTotal(), daily.goalsDone(), 6, 6, WIN);
		text(context, font, "bonus rewards at " + daily.goalsTotal(), x, y + 21, TEXT_FAINT);
		leave(context);

		missions(context, font, daily.missions());

		enter(context, 5);
		SlotView clock = SlotView.at(slots, CarnageMenu.COUNTDOWN);
		String dayEnds = clock == null ? "" : valueAfter(clock.lore(), "Current day ends in:");
		if (!dayEnds.isEmpty()) {
			text(context, font, "day resets in " + TimeFormat.hms(Durations.parseMs(dayEnds)), PAD, FOOT_Y + 3, TEXT_FAINT);
		}
		claimButton(context, font, CarnageMenu.CLAIM_SOULS);
		leave(context);
	}

	private void missions(GuiGraphicsExtractor context, Font font, List<Mission> missions) {
		int x = RIGHT_X;
		int y = BODY_Y;
		long done = missions.stream().filter(Mission::done).count();
		enter(context, 1);
		caption(context, font, x, y, COL_W, "MISSIONS", done + " / " + missions.size());
		leave(context);
		y += 11;
		for (int i = 0; i < missions.size(); i++) {
			Mission mission = missions.get(i);
			enter(context, 2 + i);
			int cardH = 40;
			HudObject.drawRoundedRect(context, x, y, COL_W, cardH, c(CARD));
			int accent = mission.done() ? WIN : PUMPKIN;
			String count = mission.current() + "/" + mission.target();
			String mark = mission.done() ? "✔ " : "";
			text(context, font, fit(font, mark + mission.name(), COL_W - 12 - font.width(count)),
					x + 5, y + 5, mission.done() ? WIN : TEXT);
			text(context, font, count, x + COL_W - 5 - font.width(count), y + 5, mission.done() ? WIN : TEXT_DIM);
			bar(context, x + 5, y + 17, COL_W - 10, fraction(mission.current(), mission.target()), accent, 2 + i);
			if (!mission.reward().isEmpty()) {
				text(context, font, fit(font, "→ " + mission.reward(), COL_W - 10), x + 5, y + 27, TEXT_FAINT);
			}
			leave(context);
			y += cardH + 5;
		}
	}

	// --- Hunt ---

	private void hunt(GuiGraphicsExtractor context, Font font, List<SlotView> slots) {
		CarnageTracker.Hunt hunt = CarnageReader.hunt(slots);
		int y = BODY_Y;
		if (hunt != null) {
			enter(context, 0);
			caption(context, font, PAD, y, BODY_W, "MASTER HUNTER", hunt.found() + " / " + hunt.total());
			bar(context, PAD, y + 11, BODY_W, fraction(hunt.found(), hunt.total()), hunt.found() >= hunt.total() ? WIN : GHOST, 0);
			leave(context);
		}
		y += 22;
		int cardW = (BODY_W - 6) / 2;
		int cardH = 24;
		int index = 0;
		for (SlotView slot : slots) {
			HuntSet set = CarnageReader.huntSet(slot.name());
			if (set == null || set.name().equalsIgnoreCase("Master Hunter")) {
				continue;
			}
			int cx = PAD + (index % 2) * (cardW + 6);
			int cy = y + (index / 2) * (cardH + 3);
			enter(context, 1 + index / 2);
			boolean complete = set.found() >= set.total();
			boolean hot = hovered(cx, cy, cardW, cardH);
			HudObject.drawRoundedRect(context, cx, cy, cardW, cardH, c(hot ? CARD_HOT : CARD));
			icon(context, stackAt(slot.index()), cx + 3, cy + 3);
			String count = set.found() + "/" + set.total();
			text(context, font, fit(font, set.name(), cardW - 36 - font.width(count)), cx + 23, cy + 4,
					complete ? WIN : TEXT);
			text(context, font, count, cx + cardW - 5 - font.width(count), cy + 4, complete ? WIN : TEXT_DIM);
			bar(context, cx + 23, cy + 15, cardW - 28, fraction(set.found(), set.total()), complete ? WIN : GHOST, 1 + index / 2);
			leave(context);
			if (hot) {
				tooltip(context, font, stackAt(slot.index()));
			}
			index++;
		}
		enter(context, 7);
		text(context, font, "hidden around Spawn · turn in at the purple pumpkin", PAD, FOOT_Y + 3, TEXT_FAINT);
		leave(context);
	}

	// --- Shop ---

	private void shop(GuiGraphicsExtractor context, Font font, List<SlotView> slots) {
		List<CarnageMenu.ShopItem> items = CarnageMenu.shop(slots);
		int cols = 2;
		int cardW = (BODY_W - 6) / cols;
		int cardH = 23;
		for (int i = 0; i < items.size(); i++) {
			CarnageMenu.ShopItem item = items.get(i);
			int x = PAD + (i % cols) * (cardW + 6);
			int y = BODY_Y + (i / cols) * (cardH + 3);
			if (y + cardH > FOOT_Y - 3) {
				break;
			}
			enter(context, i / cols + i % cols);
			boolean hot = hovered(x, y, cardW, cardH);
			float press = pressAmount(item.slot());
			HudObject.drawRoundedRect(context, x, y, cardW, cardH,
					press > 0 ? shade(PUMPKIN, (int) (0x50 * press)) : c(hot ? CARD_HOT : CARD));
			if (hot) {
				EditorPainter.outline(context, x, y, cardW, cardH, shade(PUMPKIN, 0x90));
			}
			icon(context, stackAt(item.slot()), x + 4, y + 4);
			String cost = item.cost() + (item.cost() == 1 ? " soul" : " souls");
			text(context, font, fit(font, item.name(), cardW - 30), x + 24, y + 3, hot ? 0xFFFFFFFF : TEXT);
			text(context, font, cost, x + 24, y + 13, PUMPKIN);
			clickable(x, y, cardW, cardH, item.slot());
			leave(context);
			if (hot) {
				tooltip(context, font, stackAt(item.slot()));
			}
		}
		enter(context, 5);
		text(context, font, "new stock each stage · click to buy", PAD, FOOT_Y + 3, TEXT_FAINT);
		claimButton(context, font, CarnageMenu.has(slots, CarnageMenu.SHOP_CLAIM, "Carnage Shop")
				? CarnageMenu.SHOP_CLAIM : CarnageMenu.CLAIM_SOULS);
		leave(context);
	}

	// --- Leaders ---

	private void leaderboard(GuiGraphicsExtractor context, Font font, List<SlotView> slots, String title) {
		List<CarnageMenu.Leader> leaders = CarnageMenu.leaders(slots);
		String scope = SlotView.nameAt(slots, CarnageMenu.LEADERBOARD_SCOPE).trim();
		enter(context, 0);
		caption(context, font, PAD, BODY_Y, BODY_W, scope.isEmpty() ? "LEADERBOARD" : scope.toUpperCase(Locale.ROOT),
				"top 3 win daily");
		leave(context);
		String self = Minecraft.getInstance().getUser().getName();
		int rowH = 13;
		for (int i = 0; i < leaders.size(); i++) {
			CarnageMenu.Leader leader = leaders.get(i);
			int y = BODY_Y + 11 + i * rowH;
			enter(context, 1 + i);
			boolean hot = hovered(PAD, y, BODY_W, rowH);
			boolean isSelf = leader.player().equalsIgnoreCase(self);
			if (isSelf || hot) {
				HudObject.drawRoundedRect(context, PAD, y, BODY_W, rowH - 1, isSelf ? c(shade(PUMPKIN, 0x30)) : c(CARD_HOT));
			}
			int rankColor = switch (leader.rank()) {
				case 1 -> GOLD;
				case 2 -> SILVER;
				case 3 -> BRONZE;
				default -> TEXT_FAINT;
			};
			String rank = "#" + leader.rank();
			text(context, font, rank, PAD + 4, y + 2, rankColor);
			if (alpha > ICON_CUTOFF) {
				PlayerFaceExtractor.extractRenderState(context, PlayerSkinCache.skin(leader.player()), PAD + 26, y + 1, 9);
			}
			text(context, font, leader.player(), PAD + 40, y + 2, leader.rank() <= 3 ? TEXT : TEXT_DIM);
			String points = leader.points() + " pts";
			text(context, font, points, PANEL_W - PAD - 4 - font.width(points), y + 2, leader.rank() <= 3 ? rankColor : TEXT_DIM);
			leave(context);
			if (hot) {
				tooltip(context, font, stackAt(leader.slot()));
			}
		}
		enter(context, 10);
		int[] pages = CarnageMenu.pageOf(title);
		pager(context, font, slots, pages);
		String toggle = scope.toLowerCase(Locale.ROOT).startsWith("stage") ? "OVERALL" : "THIS STAGE";
		button(context, font, PAD, FOOT_Y, 64, 13, toggle,
				SlotView.at(slots, CarnageMenu.LEADERBOARD_SCOPE) == null ? -1 : CarnageMenu.LEADERBOARD_SCOPE, PUMPKIN);
		leave(context);
	}

	// --- Bestiary ---

	private void bestiary(GuiGraphicsExtractor context, Font font, List<SlotView> slots) {
		List<CarnageMenu.Monster> monsters = CarnageMenu.bestiary(slots);
		long found = monsters.stream().filter(CarnageMenu.Monster::discovered).count();
		enter(context, 0);
		caption(context, font, PAD, BODY_Y, BODY_W, "DISCOVERED", found + " / " + monsters.size());
		leave(context);
		int tile = 26;
		int gap = 4;
		int cols = 9;
		int left = PAD + (BODY_W - (cols * tile + (cols - 1) * gap)) / 2;
		for (int i = 0; i < monsters.size(); i++) {
			CarnageMenu.Monster monster = monsters.get(i);
			int x = left + (i % cols) * (tile + gap);
			int y = BODY_Y + 12 + (i / cols) * (tile + gap);
			enter(context, 1 + i / cols + i % cols / 3);
			boolean hot = hovered(x, y, tile, tile);
			boolean locked = !monster.stage().isEmpty();
			int bg = monster.discovered() ? shade(BLOOD, 0x38) : locked ? 0x0AFFFFFF : CARD;
			HudObject.drawRoundedRect(context, x, y, tile, tile, c(hot ? CARD_HOT : bg));
			if (hot) {
				EditorPainter.outline(context, x, y, tile, tile, shade(BLOOD, 0xB0));
			}
			if (monster.discovered()) {
				icon(context, stackAt(monster.slot()), x + 5, y + 5);
			} else {
				String mark = locked ? monster.stage() : "?";
				text(context, font, mark, x + (tile - font.width(mark)) / 2, y + 9, locked ? TEXT_FAINT : TEXT_DIM);
			}
			leave(context);
			if (hot) {
				tooltip(context, font, stackAt(monster.slot()));
			}
		}
		enter(context, 6);
		text(context, font, "? can be hunted now · II, III, IV arrive in later stages", PAD, FOOT_Y + 3, TEXT_FAINT);
		leave(context);
	}

	// --- Tags ---

	private void tags(GuiGraphicsExtractor context, Font font, List<SlotView> slots, String title) {
		List<CarnageMenu.Tag> tags = CarnageMenu.tags(slots);
		enter(context, 0);
		caption(context, font, PAD, BODY_Y, BODY_W, "HALLOWEEN TAGS", "from daily goals and missions");
		leave(context);
		int cols = 3;
		int cardW = (BODY_W - (cols - 1) * 4) / cols;
		int cardH = 34;
		for (int i = 0; i < tags.size(); i++) {
			CarnageMenu.Tag tag = tags.get(i);
			int x = PAD + (i % cols) * (cardW + 4);
			int y = BODY_Y + 12 + (i / cols) * (cardH + 4);
			enter(context, 1 + i / cols + i % cols);
			boolean hot = hovered(x, y, cardW, cardH);
			HudObject.drawRoundedRect(context, x, y, cardW, cardH,
					c(tag.unlocked() ? shade(WIN, 0x22) : hot ? CARD_HOT : CARD));
			String name = fit(font, tag.name(), cardW - 8);
			text(context, font, name, x + (cardW - font.width(name)) / 2, y + 8, tag.unlocked() ? 0xFFFFFFFF : TEXT_DIM);
			String status = tag.unlocked() ? "✔ unlocked" : tag.status();
			text(context, font, status, x + (cardW - font.width(status)) / 2, y + 20, tag.unlocked() ? WIN : TEXT_FAINT);
			leave(context);
			if (hot) {
				tooltip(context, font, stackAt(tag.slot()));
			}
		}
		enter(context, 5);
		pager(context, font, slots, CarnageMenu.pageOf(title));
		leave(context);
	}

	// --- shared pieces ---

	/** ◀ page n/m ▶, centred in the footer; a missing arrow is drawn dead rather than hidden. */
	private void pager(GuiGraphicsExtractor context, Font font, List<SlotView> slots, int[] pages) {
		int arrowW = 18;
		String label = pages == null ? "" : pages[0] + " / " + pages[1];
		int labelW = Math.max(36, font.width(label) + 10);
		int x = (PANEL_W - labelW - arrowW * 2) / 2;
		boolean hasPrev = CarnageMenu.has(slots, CarnageMenu.PREVIOUS_PAGE, "Previous Page");
		boolean hasNext = CarnageMenu.has(slots, CarnageMenu.NEXT_PAGE, "Next Page");
		button(context, font, x, FOOT_Y, arrowW, 13, "◀", hasPrev ? CarnageMenu.PREVIOUS_PAGE : -1, PUMPKIN);
		text(context, font, label, x + arrowW + (labelW - font.width(label)) / 2, FOOT_Y + 3, TEXT_DIM);
		button(context, font, x + arrowW + labelW, FOOT_Y, arrowW, 13, "▶", hasNext ? CarnageMenu.NEXT_PAGE : -1, PUMPKIN);
	}

	private void claimButton(GuiGraphicsExtractor context, Font font, int slot) {
		int w = 78;
		button(context, font, PANEL_W - PAD - w, FOOT_Y, w, 13, "FREE SOULS", slot, PUMPKIN);
	}

	/** A small section label with a note right-aligned at {@code width}. */
	private void caption(GuiGraphicsExtractor context, Font font, int x, int y, int width, String label, String note) {
		text(context, font, label, x, y, TEXT_FAINT);
		if (note != null && !note.isEmpty()) {
			text(context, font, note, x + width - font.width(note), y, TEXT_DIM);
		}
	}

	/** A bar whose fill grows in with its element, {@code order} staggered like the rest. */
	private void bar(GuiGraphicsExtractor context, int x, int y, int w, double fraction, int color, int order) {
		EditorPainter.pill(context, x, y, w, BAR_H, c(RAIL));
		float grow = ease(progress(enterMs + order * STAGGER_MS, BAR_MS));
		int filled = (int) Math.round(w * Math.clamp(fraction, 0.0, 1.0) * grow);
		if (filled >= BAR_H) {
			EditorPainter.pill(context, x, y, filled, BAR_H, c(color));
		} else if (filled > 0) {
			context.fill(x, y, x + filled, y + BAR_H, c(color));
		}
	}

	/** A row of {@code total} cells, the first {@code filled} lit. */
	private void pips(GuiGraphicsExtractor context, int x, int y, int total, int filled, int w, int h, int color) {
		if (total <= 0) {
			return;
		}
		int gap = Math.max(1, Math.min(3, (COL_W - total * w) / Math.max(1, total - 1)));
		for (int i = 0; i < total; i++) {
			HudObject.drawRoundedRect(context, x + i * (w + gap), y, w, h, c(i < filled ? color : RAIL));
		}
	}

	/** Trimmed to {@code width} with an ellipsis, so a cut name reads as cut on purpose. */
	private static String fit(Font font, String value, int width) {
		if (font.width(value) <= width) {
			return value;
		}
		return font.plainSubstrByWidth(value, width - font.width("…")).stripTrailing() + "…";
	}

	private void icon(GuiGraphicsExtractor context, ItemStack stack, int x, int y) {
		if (alpha > ICON_CUTOFF && stack != null && !stack.isEmpty()) {
			context.item(stack, x, y);
		}
	}

	private void text(GuiGraphicsExtractor context, Font font, String value, int x, int y, int color) {
		int faded = c(color);
		if ((faded >>> 24) >= 8) {
			context.text(font, value, x, y, faded, false);
		}
	}

	// --- motion ---

	/** Starts element {@code order}: it rises into place and fades in, a stagger step behind the one before. */
	private void enter(GuiGraphicsExtractor context, int order) {
		alpha = ease(progress(enterMs + order * STAGGER_MS, ENTER_MS));
		context.pose().pushMatrix();
		context.pose().translate(0, (1f - alpha) * RISE_PX);
	}

	private void leave(GuiGraphicsExtractor context) {
		context.pose().popMatrix();
		alpha = 1f;
	}

	/** A colour at the current element's entrance. */
	private int c(int argb) {
		int a = Math.round((argb >>> 24) * alpha);
		return (a << 24) | (argb & 0x00FFFFFF);
	}

	private float pressAmount(int slot) {
		return slot == pressedSlot ? 1f - progress(pressedMs, PRESS_MS) : 0f;
	}

	private float progress(long startMs, long durationMs) {
		return Math.clamp((now - startMs) / (float) durationMs, 0f, 1f);
	}

	/** Strong ease-out (quart): moves at once, settles gently. */
	static float ease(float t) {
		float inverse = 1f - t;
		return 1f - inverse * inverse * inverse * inverse;
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	// --- reading ---

	private static String loreAfter(List<SlotView> slots, String itemName, String marker) {
		for (SlotView slot : slots) {
			if (!slot.name().trim().equals(itemName)) {
				continue;
			}
			List<String> lore = slot.lore();
			for (int i = 0; i < lore.size() - 1; i++) {
				if (lore.get(i).trim().equals(marker)) {
					return lore.get(i + 1).trim().replaceFirst("^•\\s*", "");
				}
			}
		}
		return "";
	}

	private static String loreValue(List<SlotView> slots, String itemName, String prefix) {
		for (SlotView slot : slots) {
			if (slot.name().trim().equals(itemName)) {
				return valueAfter(slot.lore(), prefix);
			}
		}
		return "";
	}

	private static String valueAfter(List<String> lore, String prefix) {
		for (String line : lore) {
			String trimmed = line.trim();
			if (trimmed.startsWith(prefix)) {
				return trimmed.substring(prefix.length()).trim();
			}
		}
		return "";
	}

	private static double fraction(double have, double of) {
		return of <= 0 ? 0 : have / of;
	}

	private static String percent(double have, double of) {
		return of <= 0 ? "" : Math.min(100, (int) Math.floor(have * 100 / of)) + "%";
	}

	private static String number(double value) {
		return String.format(Locale.ROOT, "%,d", (long) Math.floor(value));
	}

	private static String nameOf(AbstractContainerMenu handler, int slot) {
		if (slot < 0 || slot >= handler.slots.size()) {
			return "";
		}
		ItemStack stack = handler.slots.get(slot).getItem();
		return stack.isEmpty() ? "" : stack.getHoverName().getString();
	}
}
