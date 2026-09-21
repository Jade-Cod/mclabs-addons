package dev.jade.labsaddons.casino;

import dev.jade.labsaddons.hud.HudObject;
import dev.jade.labsaddons.hud.editor.EditorPainter;
import dev.jade.labsaddons.hud.editor.EditorTheme;
import dev.jade.labsaddons.server.McLabsSession;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * A board drawn in place of a casino game's chest menu, clicking through to it.
 *
 * <p>The screen is never replaced. A board cancels the menu's own render, paints a fixed
 * panel on the ordinary Minecraft screen dim, and forwards clicks with
 * {@code clickSlot} to the slot a player would have hit — so the server sees an ordinary
 * click and the mod never has to know what a wager means.
 *
 * <p>Everything subtle about doing that lives here, because all three games need it
 * identically and two of them were found the hard way on the Double² board:
 *
 * <ul>
 *   <li><b>{@link #hold}</b> — these menus re-send themselves, and for a frame or two
 *       their panes are simply not there. Dropping the board on those frames lets the
 *       real chest through, which reads as a flash on every click.</li>
 *   <li><b>{@link #unpredict}</b> — the client predicts a click by emptying the clicked
 *       slot into the cursor, before the server has said anything. Left alone, that one
 *       empty slot makes the container stop looking like the game it is.</li>
 * </ul>
 *
 * <p>Mines and BondJoules go further than Double² did: they close and reopen the chest
 * with a fresh {@code syncId} on <em>every</em> click, four times over just to deal one
 * blackjack hand. Nothing here may be keyed to the screen instance, and no animation may
 * depend on the container persisting.
 */
public abstract class CasinoPanel {
	/** Base panel size in GUI pixels. Fixed, like the chest menu it stands in for. */
	protected static final int PANEL_W = 288;
	protected static final int PANEL_H = 176;
	protected static final int PAD = 6;
	protected static final int HEADER_H = 16;
	protected static final int CONTENT_Y = 22;
	protected static final int ROW_H = 12;

	protected static final int PANEL = 0xFF1A1D24;
	protected static final int DIVIDER = 0xFF23262E;
	protected static final int BUTTON_BORDER = 0xFF454C5A;
	protected static final int ROW_HOVER = 0xFF262A33;
	protected static final int TRACK = 0xFF23262E;
	protected static final int TEXT = 0xFFEAEEF3;
	protected static final int TEXT_DIM = 0xFF9AA3AD;
	protected static final int TEXT_FAINT = 0xFF6E7681;
	protected static final int WIN = 0xFF96E03F;
	protected static final int LOSS = 0xFFFF8080;
	protected static final int WARN = 0xFFF1C15E;

	/** Slots that exist in every one of these menus. */
	public static final int CONTAINER_SLOTS = 54;

	/** How long a click's local prediction may mask the slot it emptied. */
	private static final long PREDICTION_MS = 600L;
	/**
	 * How long a board holds its last reading of a container that has stopped parsing.
	 * Long enough to ride out the re-send that follows a click, short enough that a menu
	 * swapped in place behind the same syncId hands the screen back.
	 */
	private static final long STALE_MS = 1_000L;
	/**
	 * How long a board holds across a <em>new</em> container id. Shorter, because holding
	 * one menu's board over a different menu is the failure that costs, and a menu the
	 * server has already opened is only empty for a tick or two.
	 */
	private static final long REOPEN_MS = 400L;

	/**
	 * A clickable rectangle in panel coordinates, the container slot behind it, and how to
	 * click it — {@code QUICK_MOVE} is what the client sends for a shift-click, which is
	 * the only way to reach the min and max stake the betting screen hides there.
	 */
	private record Hit(int x, int y, int w, int h, int slot, SlotActionType action) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private final List<Hit> hits = new ArrayList<>();
	/** Which container this board is answering for, and whose slots it is drawing. */
	private final ContainerHold container = new ContainerHold();
	/** The stacks read this frame, before the hold has decided whether to use them. */
	private List<ItemStack> liveStacks = List.of();
	/** The stacks belonging to the slots actually being drawn. See {@link #stacks()}. */
	private List<ItemStack> shownStacks = List.of();
	private int predictedSlot = -1;
	private SlotView predictedBefore;
	private long predictedAtMs;
	private float hoverX;
	private float hoverY;
	private int panelX;
	private int panelY;
	private float panelScale = 1f;

	// --- what a game supplies -------------------------------------------------

	/** The player's toggle for this board. */
	protected abstract boolean enabled();

	/**
	 * How tall this board's panel is. The default is the chest-menu height every other board
	 * fills; a board with less to say overrides it rather than leaving dead space under its
	 * content, which reads as something failing to load.
	 */
	protected int panelHeight() {
		return PANEL_H;
	}

	/**
	 * The cheap probe, run against every container screen in the game twice a frame, so
	 * it must read a couple of slot names and nothing more. Building all fifty-four slot
	 * views here was a real performance regression on the Double² board.
	 */
	protected abstract boolean looksLike(ScreenHandler handler);

	/** The full check, once the slots have been flattened. */
	protected abstract boolean parses(List<SlotView> slots);

	/**
	 * The same check, for a menu whose only sure sign is its title. Coinflip's flip
	 * screen is forty-five panes and one player head — a shape too ordinary to latch onto
	 * on its own, while "Flipping a coin..." is unmistakable.
	 */
	protected boolean parses(List<SlotView> slots, String title) {
		return parses(slots);
	}

	/**
	 * Slots this game's menu owns, before the player's own thirty-six. A chest is
	 * {@value #CONTAINER_SLOTS}; coinflip's menus are forty-five, and reading past that
	 * would pull the player's inventory into the board's own slot list.
	 */
	protected int containerSlots() {
		return CONTAINER_SLOTS;
	}

	/**
	 * Whether the menu's title lets this board stand in for it, asked once the cheap probe
	 * has passed and before anything is read off the container.
	 *
	 * <p>Most boards recognise their menu by slot names alone and leave this alone. It
	 * exists for coinflip's flip screen, whose probe is a player head between two blank
	 * panes — a shape ordinary enough to turn up in menus that have nothing to do with us.
	 * Without this gate {@link #recognises} says yes to those, and because it is what
	 * suppresses the chest texture and the slot tooltips — both drawn outside the render we
	 * cancel — the menu loses its frame and its tooltips with no board drawn in their place.
	 */
	protected boolean titleAllows(String title) {
		return true;
	}

	/** A notch of the scroll wheel over the board, positive away from the player. */
	protected void onScroll(double amount) {
	}

	/**
	 * A click the board is about to forward. Called before the packet goes, for a board
	 * that knows something about the slot the server is not going to repeat — the coinflip
	 * lobby remembers which flip it just sent you into, because the screen that follows
	 * states neither its wager nor its id.
	 */
	protected void onClick(int slot) {
	}

	/**
	 * Draws the board's contents inside the panel, origin at its top-left corner.
	 *
	 * @param title the menu's own title, which BondJoules uses as a data source: it
	 *              carries the stake and both hand totals outright
	 */
	protected abstract void draw(DrawContext context, TextRenderer font,
			List<SlotView> slots, String title, float deviceScale);

	/** A different container opened; drop anything remembered about the last one. */
	protected void onContainerChange() {
	}

	/**
	 * What makes two menus "the same menu" across a reopen. The title by default, since
	 * these games reopen the chest constantly and it is the one thing that arrives with
	 * the container rather than after it. A board whose title carries live data overrides
	 * this to drop that part.
	 */
	protected String holdKey(String title) {
		return title == null ? "" : title;
	}

	// --- the lifecycle the mixins drive --------------------------------------

	/**
	 * Whether this board is standing in for the given menu — asked by the hooks that run
	 * before it draws, so they can suppress the chest texture and the slot tooltips.
	 */
	public final boolean recognises(HandledScreen<?> screen) {
		return owns(screen) || (mayBe(screen.getScreenHandler())
				&& titleAllows(screen.getTitle().getString()));
	}

	/**
	 * The cheap test, and the only thing allowed to run against unrelated menus: a couple
	 * of slot names off the handler, with no slot views built.
	 */
	private boolean mayBe(ScreenHandler handler) {
		return enabled() && McLabsSession.isActive()
				&& handler.slots.size() >= containerSlots() && looksLike(handler);
	}

	/** True once this board has taken a container over and not yet given it back. */
	public final boolean owns(HandledScreen<?> screen) {
		return container.pinnedTo(screen.getScreenHandler().syncId);
	}

	/**
	 * Draws the board in place of the menu's own render.
	 *
	 * @return true when the board took over, so the caller cancels the vanilla render
	 */
	public final boolean render(HandledScreen<?> screen, DrawContext context,
			int mouseX, int mouseY) {
		hits.clear();
		ScreenHandler handler = screen.getScreenHandler();
		int syncId = handler.syncId;
		if (!enabled() || !McLabsSession.isActive()) {
			if (container.holding()) {
				release();
			}
			return false;
		}
		// Flattening fifty-four slots is not free, and this runs for every board against
		// every container screen. A menu we do not already hold has to pass the cheap test
		// first; one we are holding skips it, because mid-re-send it would fail.
		boolean holding = container.holding();
		if (!holding && !mayBe(handler)) {
			return false;
		}
		String title = screen.getTitle().getString();
		// Same gate recognises() uses, and for the same reason — a loose probe would
		// otherwise rebuild every slot view on this menu on every frame, for a board that
		// is never going to draw.
		if (!holding && !titleAllows(title)) {
			return false;
		}
		List<SlotView> read = slotViews(handler);
		boolean fresh = parses(read, title);
		// Only a frame the hold accepted may update the stacks, or a board drawing held
		// slots through a re-send would pair them with the new container's icons — visible
		// for up to REOPEN_MS at exactly the moment one menu hands over to the next.
		if (fresh) {
			shownStacks = liveStacks;
		}
		ContainerHold.Result held = container.offer(read, fresh, syncId,
				holdKey(title), Util.getMeasuringTimeMs(), STALE_MS, REOPEN_MS);
		List<SlotView> slots = held.slots();
		if (slots == null) {
			release();
			return false;
		}
		if (held.containerChanged()) {
			onContainerChange();
		}

		MinecraftClient client = MinecraftClient.getInstance();
		TextRenderer font = client.textRenderer;
		int w = context.getScaledWindowWidth();
		int h = context.getScaledWindowHeight();

		// No dim of our own: Minecraft has already drawn one behind this, and a second
		// on top of it reads as a doubled tint. Only ever shrink, never magnify.
		int panelH = panelHeight();
		panelScale = Math.min(1f, Math.min((w - 16f) / PANEL_W, (h - 16f) / panelH));
		panelX = Math.round((w - PANEL_W * panelScale) / 2f);
		panelY = Math.round((h - panelH * panelScale) / 2f);
		hoverX = (mouseX - panelX) / panelScale;
		hoverY = (mouseY - panelY) / panelScale;

		context.getMatrices().pushMatrix();
		context.getMatrices().translate(panelX, panelY);
		context.getMatrices().scale(panelScale, panelScale);

		HudObject.drawRoundedRect(context, 0, 0, PANEL_W, panelH, EditorTheme.PANEL_BG);
		EditorPainter.outline(context, 0, 0, PANEL_W, panelH, EditorTheme.PANEL_BORDER);

		draw(context, font, slots, title,
				(float) client.getWindow().getScaleFactor() * panelScale);

		context.getMatrices().popMatrix();
		return true;
	}

	/** True when the click was over our board, so the hidden vanilla slots stay untouched. */
	public final boolean mouseClicked(HandledScreen<?> screen, double mouseX, double mouseY) {
		if (!owns(screen)) {
			return false;
		}
		double localX = (mouseX - panelX) / panelScale;
		double localY = (mouseY - panelY) / panelScale;
		for (Hit hit : hits) {
			if (hit.contains(localX, localY)) {
				sendClick(screen, hit.slot(), hit.action());
				return true;
			}
		}
		// Swallowed even with nothing under the cursor: the real slots are still live
		// behind the board, and a stray click on an invisible one would place a wager.
		return true;
	}

	/** True when the wheel turned over our board, so the menu does not also see it. */
	public final boolean mouseScrolled(HandledScreen<?> screen, double amount) {
		if (!owns(screen)) {
			return false;
		}
		onScroll(amount);
		return true;
	}

	/** Hands the container back, so another board (or the real chest) can have it. */
	public final void release() {
		container.release();
		onContainerChange();
	}

	private void sendClick(HandledScreen<?> screen, int slot, SlotActionType action) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.interactionManager == null || client.player == null) {
			return;
		}
		// Keep what the slot held, so unpredict can put it back until the server answers.
		List<SlotView> shown = container.heldSlots();
		if (shown != null && slot >= 0 && slot < shown.size()) {
			predictedSlot = slot;
			predictedBefore = shown.get(slot);
			predictedAtMs = Util.getMeasuringTimeMs();
		}
		onClick(slot);
		client.interactionManager.clickSlot(screen.getScreenHandler().syncId, slot, 0,
				action, client.player);
	}

	private List<SlotView> slotViews(ScreenHandler handler) {
		int count = Math.min(containerSlots(), handler.slots.size());
		List<SlotView> out = new ArrayList<>(count);
		List<ItemStack> live = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			ItemStack stack = handler.slots.get(i).getStack();
			live.add(stack);
			List<String> lore = List.of();
			String name = "";
			if (!stack.isEmpty()) {
				name = stack.getName().getString();
				LoreComponent component = stack.get(DataComponentTypes.LORE);
				if (component != null) {
					List<Text> lines = component.lines();
					lore = new ArrayList<>(lines.size());
					for (int j = 0; j < lines.size(); j++) {
						lore.add(lines.get(j).getString());
					}
				}
			}
			out.add(new SlotView(i, name, lore, stack.getCount()));
		}
		liveStacks = live;
		return unpredict(out);
	}

	/**
	 * The stacks behind the slots being drawn, parallel to {@code draw}'s slot views.
	 *
	 * <p>{@link SlotView} is deliberately Minecraft-free so the readers stay testable, which
	 * leaves no way to draw the server's own item icon. This is that way, for a board that
	 * wants one — the crate chamber draws a Geo-Generator as a Geo-Generator rather than as a
	 * stand-in a player would have to learn.
	 *
	 * <p>Not copied: these are the handler's own stacks, and the handler replaces rather than
	 * mutates them on an update, so a held reference keeps pointing at the frame it came from.
	 */
	protected final List<ItemStack> stacks() {
		return shownStacks;
	}

	/** That slot's stack, or an empty one — saves a bounds check at every call site. */
	protected final ItemStack stackAt(int index) {
		List<ItemStack> all = shownStacks;
		return index >= 0 && index < all.size() ? all.get(index) : ItemStack.EMPTY;
	}

	/**
	 * Undoes the local pickup prediction on the slot we last clicked, until the server's
	 * own contents come back or the window lapses. Only ever substitutes for a slot that
	 * reads empty, so a genuine change is never masked.
	 */
	private List<SlotView> unpredict(List<SlotView> slots) {
		if (predictedSlot < 0 || predictedSlot >= slots.size()) {
			return slots;
		}
		if (Util.getMeasuringTimeMs() - predictedAtMs > PREDICTION_MS
				|| !slots.get(predictedSlot).isEmpty()) {
			predictedSlot = -1;
			predictedBefore = null;
			return slots;
		}
		if (predictedBefore != null) {
			slots.set(predictedSlot, predictedBefore);
		}
		return slots;
	}

	// --- drawing helpers every board wants ----------------------------------

	/** Whether the cursor is over this rectangle, in panel coordinates. */
	protected final boolean hovered(int x, int y, int w, int h) {
		return hoverX >= x && hoverX < x + w && hoverY >= y && hoverY < y + h;
	}

	/** Registers a clickable rectangle that forwards an ordinary click to {@code slot}. */
	protected final void clickable(int x, int y, int w, int h, int slot) {
		hits.add(new Hit(x, y, w, h, slot, SlotActionType.PICKUP));
	}

	/** The same, but sending whatever the server expects for that control. */
	protected final void clickable(int x, int y, int w, int h, int slot,
			SlotActionType action) {
		hits.add(new Hit(x, y, w, h, slot, action));
	}

	/** The panel title bar: name on the left, a status line on the right. */
	protected final void header(DrawContext context, TextRenderer font, String title,
			String status, int statusColor) {
		context.drawText(font, title, PAD, 5, 0xFFFFFFFF, false);
		if (status != null && !status.isEmpty()) {
			String fit = font.trimToWidth(status, PANEL_W - PAD * 2 - font.getWidth(title) - 6);
			context.drawText(font, fit, PANEL_W - PAD - font.getWidth(fit), 5,
					statusColor, false);
		}
		context.fill(PAD, HEADER_H, PANEL_W - PAD, HEADER_H + 1, DIVIDER);
	}

	/**
	 * A bordered button that forwards to {@code slot}, or a dead one when {@code slot} is
	 * negative — which is how a control the server has withdrawn is shown rather than
	 * hidden, so the panel does not reflow under the cursor.
	 */
	protected final void button(DrawContext context, TextRenderer font, int x, int y,
			int w, int h, String label, int slot, int accent) {
		button(context, font, x, y, w, h, label, slot, accent, SlotActionType.PICKUP);
	}

	/** The same, for a control the server only answers to a shift-click on. */
	protected final void button(DrawContext context, TextRenderer font, int x, int y,
			int w, int h, String label, int slot, int accent, SlotActionType action) {
		boolean live = slot >= 0;
		boolean hot = live && hovered(x, y, w, h);
		context.fill(x, y, x + w, y + h, hot ? ROW_HOVER : PANEL);
		EditorPainter.outline(context, x, y, w, h,
				hot ? accent : live ? BUTTON_BORDER : DIVIDER);
		String fit = font.trimToWidth(label, w - 4);
		context.drawText(font, fit, x + (w - font.getWidth(fit)) / 2,
				y + (h - font.fontHeight) / 2 + 1,
				hot ? 0xFFFFFFFF : live ? TEXT : TEXT_FAINT, false);
		if (live) {
			clickable(x, y, w, h, slot, action);
		}
	}

	/** A label on the left and a value on the right, on one line. */
	protected final void row(DrawContext context, TextRenderer font, int x, int y,
			int width, String label, String value, int labelColor, int valueColor) {
		context.drawText(font, label, x, y, labelColor, false);
		if (value != null && !value.isEmpty()) {
			String fit = font.trimToWidth(value, width - font.getWidth(label) - 4);
			context.drawText(font, fit, x + width - font.getWidth(fit), y, valueColor, false);
		}
	}

	/** A small capitalised section label, in the editor's voice. */
	protected final void caption(DrawContext context, TextRenderer font, int x, int y,
			String text) {
		context.drawText(font, text, x, y, TEXT_FAINT, false);
	}

	protected static int accent() {
		return EditorTheme.ACCENT;
	}

	/** A colour at the weight a background fill wants. */
	protected static int shade(int argb, int alpha) {
		return (alpha << 24) | (argb & 0x00FFFFFF);
	}
}
