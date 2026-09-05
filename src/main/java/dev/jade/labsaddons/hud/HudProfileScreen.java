package dev.jade.labsaddons.hud;

import dev.jade.labsaddons.config.ConfigStore;
import dev.jade.labsaddons.config.LabsAddonsConfig;
import dev.jade.labsaddons.hud.editor.EditorPainter;
import dev.jade.labsaddons.hud.editor.EditorTheme;
import dev.jade.labsaddons.server.McLabsWorld;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

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

	private EditBox nameField;
	private Button createButton;
	private Button renameButton;

	private int cardX;
	private int cardY;
	private int cardH;
	/** Y of each section's first row, shared by init() and the background painter. */
	private int worldsTop;
	private int profilesTop;

	public HudProfileScreen(Screen parent) {
		super(Component.translatable("labsaddons.hud.profiles.title"));
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
			this.addRenderableWidget(Button.builder(bindingLabel(config, world),
							b -> cycleBinding(world, b))
					.bounds(x + WORLD_LABEL_W, y, BIND_W, ROW_H)
					.tooltip(Tooltip.create(Component.translatable("labsaddons.hud.profiles.bind.tooltip",
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
			Button load = Button.builder(
							active ? Component.translatable("labsaddons.hud.profiles.entry.active", id)
									: Component.literal(id),
							b -> activate(id))
					.bounds(x, y, PROFILE_W, ROW_H)
					.tooltip(Tooltip.create(Component.translatable("labsaddons.hud.profiles.activate.tooltip")))
					.build();
			load.active = !active;
			this.addRenderableWidget(load);

			Button delete = Button.builder(
							Component.literal(id.equals(pendingDelete) ? "?" : "x"), b -> delete(id))
					.bounds(x + PROFILE_W + GAP, y, DELETE_W, ROW_H)
					.tooltip(Tooltip.create(Component.translatable(id.equals(pendingDelete)
							? "labsaddons.hud.profiles.delete.confirm"
							: "labsaddons.hud.profiles.delete.tooltip")))
					.build();
			// The active profile and the fallback both have to keep existing.
			delete.active = !active && !ConfigStore.DEFAULT_PROFILE.equals(id);
			this.addRenderableWidget(delete);
			y += ROW_H + GAP;
		}

		y += SECTION_GAP;
		nameField = new EditBox(this.font, x, y, INNER_W, ROW_H,
				Component.translatable("labsaddons.hud.profiles.name"));
		nameField.setMaxLength(32);
		nameField.setHint(Component.translatable("labsaddons.hud.profiles.name")
				.withStyle(style -> style.withColor(EditorTheme.TEXT_DIM)));
		nameField.setValue(typedName);
		// Sanitise through the responder rather than an input predicate: the field shows
		// the id it will actually save, and there is no filter hook to rely on. Setting
		// the text re-enters this once with an already-clean value, which then stops.
		nameField.setResponder(text -> {
			String cleaned = ConfigStore.toProfileId(text);
			if (!cleaned.equals(text)) {
				nameField.setValue(cleaned);
				return;
			}
			typedName = cleaned;
			updateNameButtons();
		});
		this.addRenderableWidget(nameField);

		y += ROW_H + GAP;
		createButton = this.addRenderableWidget(Button.builder(
						Component.translatable("labsaddons.hud.profiles.create"), b -> createProfile())
				.bounds(x, y, CREATE_W, ROW_H)
				.tooltip(Tooltip.create(Component.translatable("labsaddons.hud.profiles.create.tooltip")))
				.build());
		renameButton = this.addRenderableWidget(Button.builder(
						Component.translatable("labsaddons.hud.profiles.rename"), b -> renameProfile())
				.bounds(x + CREATE_W + GAP, y, RENAME_W, ROW_H)
				.tooltip(Tooltip.create(Component.translatable(
						ConfigStore.DEFAULT_PROFILE.equals(config.activeProfile)
								? "labsaddons.hud.profiles.rename.default"
								: "labsaddons.hud.profiles.rename.tooltip")))
				.build());
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
				.bounds(x + INNER_W - DONE_W, y, DONE_W, ROW_H).build());
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
		this.rebuildWidgets();
	}

	private static Component bindingLabel(LabsAddonsConfig config, McLabsWorld world) {
		String bound = config.worldProfiles.get(world.id());
		return bound == null
				? Component.translatable("labsaddons.hud.profiles.none")
				: Component.literal(bound);
	}

	/** Steps a world through (none) and every existing profile, wrapping around. */
	private void cycleBinding(McLabsWorld world, Button button) {
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
		this.rebuildWidgets();
	}

	/** Two clicks to delete: the first arms it, so a stray click cannot drop a layout. */
	private void delete(String id) {
		if (!id.equals(pendingDelete)) {
			pendingDelete = id;
			this.rebuildWidgets();
			return;
		}
		pendingDelete = null;
		LabsAddonsConfig config = LabsAddonsConfig.get();
		LabsAddonsConfig.storage().deleteProfile(id);
		config.worldProfiles.values().removeIf(id::equals);
		config.save();
		this.rebuildWidgets();
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
		this.rebuildWidgets();
	}

	/** The editor behind this screen built its rail against the old profile. */
	private void rebuildEditor() {
		if (parent instanceof HudEditScreen editor) {
			editor.refreshForProfileChange();
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		super.extractBackground(context, mouseX, mouseY, delta);
		LabsAddonsConfig config = LabsAddonsConfig.get();

		EditorPainter.panel(context, new int[] {cardX, cardY, CARD_W, cardH},
				EditorTheme.PANEL_BG, EditorTheme.PANEL_BORDER);

		int x = cardX + PAD;
		int y = cardY + PAD;
		context.text(this.font, this.title, x, y, EditorTheme.TITLE, false);
		y += TITLE_H;
		context.text(this.font,
				Component.translatable("labsaddons.hud.profiles.active", config.activeProfile),
				x, y, EditorTheme.TEXT_ACCENT, false);

		context.text(this.font, Component.translatable("labsaddons.hud.profiles.worlds"),
				x, worldsTop - HEADER_H, EditorTheme.TEXT_DIM, false);

		int rowY = worldsTop;
		for (McLabsWorld world : McLabsWorld.values()) {
			context.text(this.font, world.displayName(),
					x, rowY + (ROW_H - this.font.lineHeight) / 2, EditorTheme.TEXT, false);
			rowY += ROW_H + GAP;
		}

		context.text(this.font, Component.translatable("labsaddons.hud.profiles.list"),
				x, profilesTop - HEADER_H, EditorTheme.TEXT_DIM, false);
	}

	@Override
	public void onClose() {
		LabsAddonsConfig.get().saveNow();
		if (this.minecraft != null) {
			this.minecraft.setScreenAndShow(parent);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
