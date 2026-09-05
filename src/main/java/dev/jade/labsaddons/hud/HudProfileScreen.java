package dev.jade.labsaddons.hud;

import dev.jade.labsaddons.config.ConfigStore;
import dev.jade.labsaddons.config.LabsAddonsConfig;
import dev.jade.labsaddons.hud.editor.EditorPainter;
import dev.jade.labsaddons.hud.editor.EditorTheme;
import dev.jade.labsaddons.server.McLabsWorld;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages HUD profiles: which layout is active, and which MCLabs world auto-loads
 * which profile.
 *
 * <p>Profiles are created by duplicating the active one and named after the world you
 * are standing in, which avoids a free-text field for what is only ever a short id.
 *
 * <p>The card is painted in {@link #renderBackground} rather than {@link #render} so
 * the buttons land on top of it instead of underneath.
 */
public class HudProfileScreen extends Screen {
	private static final int CARD_W = 300;
	private static final int PAD = 12;
	private static final int INNER_W = CARD_W - 2 * PAD;
	private static final int ROW_H = 20;
	private static final int GAP = 3;
	private static final int SECTION_GAP = 9;
	private static final int HEADER_H = 12;
	private static final int TITLE_H = 12;
	private static final int ACTIVE_H = 13;

	/** World name column; the binding button fills the rest of the row. */
	private static final int WORLD_LABEL_W = 104;
	private static final int BIND_W = INNER_W - WORLD_LABEL_W;
	private static final int DELETE_W = 20;
	private static final int PROFILE_W = INNER_W - DELETE_W - GAP;
	private static final int CREATE_W = 86;
	private static final int RENAME_W = 86;
	private static final int DONE_W = INNER_W - CREATE_W - RENAME_W - 2 * GAP;
	/** Upper bound on the profile list regardless of window size. */
	private static final int MAX_PROFILE_ROWS = 8;
	/** Everything in the card that is not a world or profile row. */
	private static final int CHROME_H = PAD + TITLE_H + ACTIVE_H
			+ SECTION_GAP + HEADER_H + SECTION_GAP + HEADER_H
			+ SECTION_GAP + ROW_H + GAP + ROW_H + PAD;

	private final Screen parent;
	/** Profile awaiting a second click on its delete button, or null. */
	private String pendingDelete;
	/** Typed name, kept across rebuilds so activating a profile does not clear it. */
	private String typedName = "";

	private TextFieldWidget nameField;
	private ButtonWidget createButton;
	private ButtonWidget renameButton;

	private int cardX;
	private int cardY;
	private int cardH;
	/** Y of each section's first row, shared by init() and the background painter. */
	private int worldsTop;
	private int profilesTop;

	public HudProfileScreen(Screen parent) {
		super(Text.translatable("labsaddons.hud.profiles.title"));
		this.parent = parent;
	}

	private static List<String> profiles() {
		List<String> ids = LabsAddonsConfig.storage().listProfiles();
		// A fresh install has no file until the first save; never show an empty list.
		if (!ids.contains(ConfigStore.DEFAULT_PROFILE)) {
			ids.add(0, ConfigStore.DEFAULT_PROFILE);
		}
		return ids;
	}

	@Override
	protected void init() {
		LabsAddonsConfig config = LabsAddonsConfig.get();
		List<String> profileIds = profiles();
		int worldRows = McLabsWorld.values().length;
		// At a large GUI scale the window is short; drop profile rows rather than let
		// the card (and its Done button) run off the bottom of the screen.
		int roomForRows = (this.height - 12 - CHROME_H) / (ROW_H + GAP) - worldRows;
		int profileRows = Math.max(1,
				Math.min(profileIds.size(), Math.min(MAX_PROFILE_ROWS, roomForRows)));

		int rows = worldRows + profileRows;
		cardH = CHROME_H + rows * (ROW_H + GAP);
		cardX = (this.width - CARD_W) / 2;
		cardY = Math.max(6, (this.height - cardH) / 2);

		int x = cardX + PAD;
		worldsTop = cardY + PAD + TITLE_H + ACTIVE_H + SECTION_GAP + HEADER_H;

		int y = worldsTop;
		for (McLabsWorld world : McLabsWorld.values()) {
			this.addDrawableChild(ButtonWidget.builder(bindingLabel(config, world),
							b -> cycleBinding(world, b))
					.dimensions(x + WORLD_LABEL_W, y, BIND_W, ROW_H)
					.tooltip(Tooltip.of(Text.translatable("labsaddons.hud.profiles.bind.tooltip",
							world.displayName())))
					.build());
			y += ROW_H + GAP;
		}

		y += SECTION_GAP;
		profilesTop = y + HEADER_H;
		y = profilesTop;

		for (int i = 0; i < profileRows; i++) {
			String id = profileIds.get(i);
			boolean active = id.equals(config.activeProfile);
			ButtonWidget load = ButtonWidget.builder(
							active ? Text.translatable("labsaddons.hud.profiles.entry.active", id)
									: Text.literal(id),
							b -> activate(id))
					.dimensions(x, y, PROFILE_W, ROW_H)
					.tooltip(Tooltip.of(Text.translatable("labsaddons.hud.profiles.activate.tooltip")))
					.build();
			load.active = !active;
			this.addDrawableChild(load);

			ButtonWidget delete = ButtonWidget.builder(
							Text.literal(id.equals(pendingDelete) ? "?" : "x"), b -> delete(id))
					.dimensions(x + PROFILE_W + GAP, y, DELETE_W, ROW_H)
					.tooltip(Tooltip.of(Text.translatable(id.equals(pendingDelete)
							? "labsaddons.hud.profiles.delete.confirm"
							: "labsaddons.hud.profiles.delete.tooltip")))
					.build();
			// The active profile and the fallback both have to keep existing.
			delete.active = !active && !ConfigStore.DEFAULT_PROFILE.equals(id);
			this.addDrawableChild(delete);
			y += ROW_H + GAP;
		}

		y += SECTION_GAP;
		nameField = new TextFieldWidget(this.textRenderer, x, y, INNER_W, ROW_H,
				Text.translatable("labsaddons.hud.profiles.name"));
		nameField.setMaxLength(32);
		nameField.setPlaceholder(Text.translatable("labsaddons.hud.profiles.name")
				.styled(style -> style.withColor(EditorTheme.TEXT_DIM)));
		nameField.setText(typedName);
		// Sanitise through the responder rather than an input predicate: the field shows
		// the id it will actually save, and there is no filter hook to rely on. Setting
		// the text re-enters this once with an already-clean value, which then stops.
		nameField.setChangedListener(text -> {
			String cleaned = ConfigStore.toProfileId(text);
			if (!cleaned.equals(text)) {
				nameField.setText(cleaned);
				return;
			}
			typedName = cleaned;
			updateNameButtons();
		});
		this.addDrawableChild(nameField);

		y += ROW_H + GAP;
		createButton = this.addDrawableChild(ButtonWidget.builder(
						Text.translatable("labsaddons.hud.profiles.create"), b -> createProfile())
				.dimensions(x, y, CREATE_W, ROW_H)
				.tooltip(Tooltip.of(Text.translatable("labsaddons.hud.profiles.create.tooltip")))
				.build());
		renameButton = this.addDrawableChild(ButtonWidget.builder(
						Text.translatable("labsaddons.hud.profiles.rename"), b -> renameProfile())
				.dimensions(x + CREATE_W + GAP, y, RENAME_W, ROW_H)
				.tooltip(Tooltip.of(Text.translatable(
						ConfigStore.DEFAULT_PROFILE.equals(config.activeProfile)
								? "labsaddons.hud.profiles.rename.default"
								: "labsaddons.hud.profiles.rename.tooltip")))
				.build());
		this.addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> this.close())
				.dimensions(x + INNER_W - DONE_W, y, DONE_W, ROW_H).build());
		updateNameButtons();
	}

	/**
	 * Create needs a free id; rename additionally needs a profile that is allowed to be
	 * renamed. Disabling beats failing silently on click.
	 */
	private void updateNameButtons() {
		String derived = ConfigStore.toProfileId(typedName);
		boolean free = !derived.isEmpty() && !LabsAddonsConfig.storage().profileExists(derived);
		boolean renameable = !ConfigStore.DEFAULT_PROFILE.equals(LabsAddonsConfig.get().activeProfile);
		if (createButton != null) {
			createButton.active = free;
		}
		if (renameButton != null) {
			renameButton.active = free && renameable;
		}
	}

	private void renameProfile() {
		if (LabsAddonsConfig.renameActiveProfile(ConfigStore.toProfileId(typedName))) {
			typedName = "";
			rebuildEditor();
		}
		this.clearAndInit();
	}

	private static Text bindingLabel(LabsAddonsConfig config, McLabsWorld world) {
		String bound = config.worldProfiles.get(world.id());
		return bound == null
				? Text.translatable("labsaddons.hud.profiles.none")
				: Text.literal(bound);
	}

	/** Steps a world through (none) and every existing profile, wrapping around. */
	private void cycleBinding(McLabsWorld world, ButtonWidget button) {
		LabsAddonsConfig config = LabsAddonsConfig.get();
		List<String> options = new ArrayList<>();
		options.add(null);
		options.addAll(profiles());

		int next = options.indexOf(config.worldProfiles.get(world.id())) + 1;
		String chosen = options.get(next % options.size());
		if (chosen == null) {
			config.worldProfiles.remove(world.id());
		} else {
			config.worldProfiles.put(world.id(), chosen);
		}
		config.save();
		button.setMessage(bindingLabel(config, world));
	}

	private void activate(String id) {
		LabsAddonsConfig.activateProfile(id);
		pendingDelete = null;
		rebuildEditor();
		this.clearAndInit();
	}

	/** Two clicks to delete: the first arms it, so a stray click cannot drop a layout. */
	private void delete(String id) {
		if (!id.equals(pendingDelete)) {
			pendingDelete = id;
			this.clearAndInit();
			return;
		}
		pendingDelete = null;
		LabsAddonsConfig config = LabsAddonsConfig.get();
		LabsAddonsConfig.storage().deleteProfile(id);
		config.worldProfiles.values().removeIf(id::equals);
		config.save();
		this.clearAndInit();
	}

	/**
	 * Duplicates the active layout under the typed name, falling back to the world you
	 * are standing in and then to a number, so the button always does something.
	 */
	private void createProfile() {
		List<String> existing = profiles();
		String candidate = ConfigStore.toProfileId(typedName);
		if (candidate.isEmpty() || existing.contains(candidate)) {
			McLabsWorld world = McLabsWorld.current();
			candidate = world == null ? "" : world.id();
		}
		if (candidate.isEmpty() || existing.contains(candidate)) {
			int suffix = existing.size() + 1;
			candidate = "profile" + suffix;
			while (existing.contains(candidate)) {
				candidate = "profile" + (++suffix);
			}
		}
		LabsAddonsConfig.duplicateActiveProfile(candidate);
		typedName = "";
		rebuildEditor();
		this.clearAndInit();
	}

	/** The editor behind this screen built its rail against the old profile. */
	private void rebuildEditor() {
		if (parent instanceof HudEditScreen editor) {
			editor.refreshForProfileChange();
		}
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		super.renderBackground(context, mouseX, mouseY, delta);
		LabsAddonsConfig config = LabsAddonsConfig.get();

		EditorPainter.panel(context, new int[] {cardX, cardY, CARD_W, cardH},
				EditorTheme.PANEL_BG, EditorTheme.PANEL_BORDER);

		int x = cardX + PAD;
		int y = cardY + PAD;
		context.drawText(this.textRenderer, this.title, x, y, EditorTheme.TITLE, false);
		y += TITLE_H;
		context.drawText(this.textRenderer,
				Text.translatable("labsaddons.hud.profiles.active", config.activeProfile),
				x, y, EditorTheme.TEXT_ACCENT, false);

		context.drawText(this.textRenderer, Text.translatable("labsaddons.hud.profiles.worlds"),
				x, worldsTop - HEADER_H, EditorTheme.TEXT_DIM, false);

		int rowY = worldsTop;
		for (McLabsWorld world : McLabsWorld.values()) {
			context.drawText(this.textRenderer, world.displayName(),
					x, rowY + (ROW_H - this.textRenderer.fontHeight) / 2, EditorTheme.TEXT, false);
			rowY += ROW_H + GAP;
		}

		context.drawText(this.textRenderer, Text.translatable("labsaddons.hud.profiles.list"),
				x, profilesTop - HEADER_H, EditorTheme.TEXT_DIM, false);
	}

	@Override
	public void close() {
		LabsAddonsConfig.get().saveNow();
		if (this.client != null) {
			this.client.setScreen(parent);
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
