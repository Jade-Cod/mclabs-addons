package dev.jade.labsaddons;

import dev.jade.labsaddons.booster.BoosterHudObject;
import dev.jade.labsaddons.event.MiniEventHudObject;
import dev.jade.labsaddons.event.MiniEventTracker;
import dev.jade.labsaddons.event.PitHudObject;
import dev.jade.labsaddons.event.PitTracker;
import dev.jade.labsaddons.labwars.LabWarsHudObject;
import dev.jade.labsaddons.labwars.LabWarsRatesReader;
import dev.jade.labsaddons.labwars.LabWarsTracker;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import dev.jade.labsaddons.mount.RentalMountHudObject;
import dev.jade.labsaddons.mount.RentalMountTimer;
import dev.jade.labsaddons.personal.PersonalBoosterHudObject;
import dev.jade.labsaddons.personal.PersonalBoosters;
import dev.jade.labsaddons.chum.ChumTimer;
import dev.jade.labsaddons.booster.BoosterRatesReader;
import dev.jade.labsaddons.booster.BoosterTracker;
import dev.jade.labsaddons.bounty.BountyHudObject;
import dev.jade.labsaddons.bounty.BountyTracker;
import dev.jade.labsaddons.daily.DailyReminderHudObject;
import dev.jade.labsaddons.daily.DailyTracker;
import dev.jade.labsaddons.daily.VoteReminderHudObject;
import dev.jade.labsaddons.daily.VoteTracker;
import dev.jade.labsaddons.chem.ChemItems;
import dev.jade.labsaddons.chem.ChemtainerDepositCapture;
import dev.jade.labsaddons.chem.ChemtainerHudObject;
import dev.jade.labsaddons.chem.ChemtainerReader;
import dev.jade.labsaddons.chem.ChemtainerTracker;
import dev.jade.labsaddons.chem.SmugglerSatchel;
import dev.jade.labsaddons.chum.ChumDetector;
import dev.jade.labsaddons.chum.ChumHudObject;
import dev.jade.labsaddons.cooldown.CooldownHudObject;
import dev.jade.labsaddons.mcmmo.McmmoAbility;
import dev.jade.labsaddons.mcmmo.McmmoCooldownTracker;
import dev.jade.labsaddons.pititem.PitItemCooldownTracker;
import dev.jade.labsaddons.mastery.MasteryChatTracker;
import dev.jade.labsaddons.mastery.MasteryHudObject;
import dev.jade.labsaddons.mastery.MasteryCatchTracker;
import dev.jade.labsaddons.mastery.MasterySellTracker;
import dev.jade.labsaddons.mastery.MasteryKillTracker;
import dev.jade.labsaddons.mastery.MasteryReader;
import dev.jade.labsaddons.mastery.MasteryStore;
import dev.jade.labsaddons.runner.RunnerAlarm;
import dev.jade.labsaddons.runner.RunnerHudObject;
import dev.jade.labsaddons.runner.RunnerTracker;
import dev.jade.labsaddons.runner.SupplierJobsReader;
import dev.jade.labsaddons.hud.HudEditScreen;
import dev.jade.labsaddons.hud.HudObjects;
import dev.jade.labsaddons.config.LabsAddonsConfig;
import dev.jade.labsaddons.server.McLabsSession;
import dev.jade.labsaddons.update.ModrinthUpdateChecker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import dev.jade.labsaddons.mixin.KeyBindingCategoryAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

/**
 * Client entrypoint. Wires up the bite marker (HUD-projected, see
 * {@link BiteMarkerHud}) and the Chum Bucket timer (detection + HUD + editor).
 */
public class LabsAddonsClient implements ClientModInitializer {
	/** The mod's own keybind category, moved near the top of the Controls screen. */
	public static final KeyBinding.Category MCLAB_CATEGORY = registerCategory();

	private static KeyBinding chumEditorKey;
	private static KeyBinding chemDepositKey;
	private static KeyBinding chemWithdrawKey;
	private static net.minecraft.client.gui.screen.Screen lastRatesScreen;

	/** Create the "McLab Addons" category and hoist it above the vanilla ones
	 *  (mod categories are otherwise appended last). Best-effort: if the registry
	 *  list can't be reordered the category simply stays at the bottom. */
	private static KeyBinding.Category registerCategory() {
		KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("labsaddons", "main"));
		try {
			List<KeyBinding.Category> categories = KeyBindingCategoryAccessor.getCategories();
			if (categories.remove(category)) {
				categories.add(0, category);
			}
		} catch (Throwable ignored) {
			// Accessor unavailable — leave the category where create() put it.
		}
		return category;
	}

	@Override
	public void onInitializeClient() {
		LabsAddonsConfig.get();
		// One-time (v1.14.0): stash pre-rename rebinds from options.txt before
		// the boot-time options save drops them; applied at CLIENT_STARTED below.
		KeybindMigration.capture();

		// Bite marker: capture frame matrices; the projected "!" is drawn by
		// HudRenderDispatcher (InGameHudMixin tail hook) alongside the widgets.
		WorldRenderEvents.END_EXTRACTION.register(BiteMarkerHud::onEndExtraction);

		// HUD objects (each gains dragging, snapping, resize, background).
		HudObjects.register(new ChumHudObject());
		HudObjects.register(new BoosterHudObject());
		HudObjects.register(new MiniEventHudObject());
		HudObjects.register(new PitHudObject());
		HudObjects.register(new LabWarsHudObject());
		HudObjects.register(new RentalMountHudObject());
		HudObjects.register(new PersonalBoosterHudObject());
		HudObjects.register(new BountyHudObject());
		HudObjects.register(new DailyReminderHudObject());
		HudObjects.register(new VoteReminderHudObject());
		HudObjects.register(new ChemtainerHudObject());
		HudObjects.register(new MasteryHudObject());
		MasteryChatTracker.setSelfNameSupplier(LabsAddonsClient::selfName);
		HudObjects.register(new RunnerHudObject());
		HudObjects.register(new CooldownHudObject());
		CooldownHudObject.addSource(McmmoCooldownTracker.source());
		McmmoCooldownTracker.setHeldToolResolver(LabsAddonsClient::heldTool);
		CooldownHudObject.addSource(PitItemCooldownTracker.source());

		// Track boosters, mini-events, and the Pit from chat/system announcements.
		// Actionbar (overlay) text is captured in InGameHudMixin instead: servers
		// that send it via the Set Action Bar Text packet never reach this event.
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) {
				dispatchChat(message.getString());
			}
		});
		ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) ->
				dispatchChat(message.getString()));

		// The MCLabs-only HUD widgets and update check key off this per-connection
		// session flag; reset it on every fresh connection so a stale "yes" can't
		// leak into a different server (or singleplayer).
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> McLabsSession.reset());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			McLabsSession.reset();
			// Entity ids are per-server; drop kill-tracking state so none leaks forward.
			MasteryKillTracker.reset();
			// Likewise the inventory baseline: rejoining must not read as a haul.
			MasteryCatchTracker.reset();
			// An in-flight sale can't settle across a disconnect, and the satchel we
			// remember belongs to the world we just left.
			MasterySellTracker.reset();
			SmugglerSatchel.reset();
		});

		// Mark the SM daily claimed the moment the player sends "/sm claim",
		// without waiting for the server confirmation line. Fires on the main
		// client thread (sendCommand), same as the receive listeners above.
		ClientSendMessageEvents.COMMAND.register(command -> {
			String sent = command.trim().toLowerCase(Locale.ROOT);
			// Exact match (or with trailing args) so "/sm claimsomething" can't false-trigger.
			if (sent.equals("sm claim") || sent.startsWith("sm claim ")) {
				DailyTracker.markSmClaimed();
			}
			// Any quick-deposit ("/ch qd" or "/c qd"), whether typed, macro'd, or sent
			// by our keybind, arms the inventory diff that learns what was deposited.
			if (isQuickDeposit(sent)) {
				armDepositCapture();
			}
		});

		// Remember the dealer the player touched, so the next sale can be attributed to
		// "Sell to <Dealer>". Only a hint — the sale itself is armed by the server's own
		// confirmation line, so a missed interaction costs attribution, not tracking.
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (player == MinecraftClient.getInstance().player && McLabsSession.isActive()) {
				MasterySellTracker.onInteract(dealerLabel(entity));
			}
			return ActionResult.PASS;
		});

		// Detect Chum Bucket activation (right-click with the item in hand).
		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (player == MinecraftClient.getInstance().player) {
				var stack = player.getStackInHand(hand);
				if (ChumDetector.isChumBucket(stack)) {
					ChumDetector.tryActivate(player.getInventory().getSelectedSlot());
				} else if (McmmoCooldownTracker.isSmellingSalts(stack)) {
					McmmoCooldownTracker.clear();
				} else {
					RentalMountTimer.tryCoupon(stack);
				}
			}
			return ActionResult.PASS;
		});

		// Keybind to open the draggable chum HUD editor (unbound by default).
		chumEditorKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.labsaddons.chum_editor", InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_SEMICOLON, MCLAB_CATEGORY));
		// Chemtainer deposit (default B): send "/ch qd" and track what gets banked.
		chemDepositKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.labsaddons.chem_deposit", InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_B, MCLAB_CATEGORY));
		// Chemtainer withdraw (default N): pull back the largest chem you have.
		chemWithdrawKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.labsaddons.chem_withdraw", InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_N, MCLAB_CATEGORY));
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			KeybindMigration.apply(client, chumEditorKey, chemDepositKey, chemWithdrawKey);
			// Restore the saved board here rather than at init: resolving the quest
			// icons needs the item registry, which is only populated once the client
			// has finished starting.
			MasteryStore.load();
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (McLabsSession.tick(client)) {
				ModrinthUpdateChecker.checkAndNotify();
			}
			RunnerAlarm.tick();
			// Live "Kill <mob>" progress from the solo Pit: each mob death is your kill,
			// read straight off the world since no chat line announces it.
			if (client.world != null && McLabsSession.isActive() && MasteryKillTracker.tick(client.world)) {
				MasteryStore.save();
			}
			// Live "Catch <fish>" progress: the catch is read off the inventory, which
			// sees it whether the server drops it at the bobber or hands it over directly.
			if (client.player != null && McLabsSession.isActive() && MasteryCatchTracker.tick(client.player)) {
				MasteryStore.save();
			}
			// Live "Sell <Chem>" / "Sell to <Dealer>" progress: chat gives only a grand
			// total, so the inventory diff decides which chems left and at what purity.
			if (client.player != null && McLabsSession.isActive()
					&& MasterySellTracker.tick(client.player.getInventory())) {
				MasteryStore.save();
			}
			while (chumEditorKey.wasPressed()) {
				client.setScreen(new HudEditScreen(client.currentScreen));
			}
			while (chemDepositKey.wasPressed()) {
				armDepositCapture();
				sendChatCommand("ch qd");
			}
			while (chemWithdrawKey.wasPressed()) {
				ChemItems.ChemKey target = ChemtainerTracker.largestChem();
				if (target != null) {
					sendChatCommand("ch withdraw " + ChemItems.withdrawArg(target));
				}
			}
			ChemtainerDepositCapture.tick();
			// Passive: scrape the /lw rates and /chems booster GUIs ONCE per open
			// (their lore is a static snapshot; re-reading every tick would freeze
			// the countdown).
			net.minecraft.client.gui.screen.Screen current = client.currentScreen;
			if (current instanceof HandledScreen<?> handledScreen) {
				if (current != lastRatesScreen) {
					lastRatesScreen = current;
					// Ahead of the chain rather than in it: a satchel read only needs the
					// title to disagree to bail, and keeping it out avoids another
					// nesting level in an already-deep fall-through.
					SmugglerSatchel.tryRead(handledScreen);
					if (!LabWarsRatesReader.tryRead(handledScreen)) {
						if (!BoosterRatesReader.tryRead(handledScreen)) {
							if (!ChemtainerReader.tryRead(handledScreen)) {
								if (!SupplierJobsReader.tryRead(handledScreen)
										&& MasteryReader.tryRead(handledScreen)) {
									MasteryStore.save();
								}
							}
						}
					}
				}
			} else {
				lastRatesScreen = null;
			}
		});
	}


	/** Whether a sent command is a chem quick-deposit ("ch qd"/"c qd", slash-stripped). */
	private static boolean isQuickDeposit(String sent) {
		return sent.equals("ch qd") || sent.startsWith("ch qd ")
				|| sent.equals("c qd") || sent.startsWith("c qd ");
	}

	/** Snapshot the current inventory and arm the deposit diff (idempotent). */
	private static void armDepositCapture() {
		var player = MinecraftClient.getInstance().player;
		if (player != null) {
			ChemtainerDepositCapture.arm(ChemItems.snapshot(player.getInventory()));
		}
	}

	/**
	 * The name a dealer actually shows. The coloured dealers are Citizens NPCs whose
	 * entity name is a placeholder like "CIT-ea4idb53b38b"; the "Green Dealer" a player
	 * reads is a separate hologram entity floating above them. So the entity's own name
	 * is tried first — the Traveling Dealer really is named that — and only failing that
	 * do we look just above it for the label it wears.
	 */
	private static String dealerLabel(Entity entity) {
		String own = entityLabel(entity);
		if (MasterySellTracker.dealerName(own) != null) {
			return own;
		}
		var world = MinecraftClient.getInstance().world;
		if (world == null) {
			return own;
		}
		Box above = entity.getBoundingBox().expand(1.5, 3.0, 1.5);
		for (Entity nearby : world.getOtherEntities(entity, above)) {
			String label = entityLabel(nearby);
			if (MasterySellTracker.dealerName(label) != null) {
				return label;
			}
		}
		// Returned rather than dropped so the calibration echo can report what we saw.
		return own;
	}

	private static String entityLabel(Entity entity) {
		Text custom = entity.getCustomName();
		return custom != null ? custom.getString() : entity.getName().getString();
	}

	/** The mcMMO tool kind the player is holding (FISTS for an empty hand). */
	private static McmmoAbility.Tool heldTool() {
		var player = MinecraftClient.getInstance().player;
		if (player == null) {
			return null;
		}
		var held = player.getMainHandStack();
		if (held.isEmpty()) {
			return McmmoAbility.Tool.FISTS;
		}
		return McmmoAbility.Tool.fromItemId(
				net.minecraft.registry.Registries.ITEM.getId(held.getItem()).toString());
	}

	/** The local player's name, used to tell whether a chat-reaction win was ours. */
	private static String selfName() {
		var player = MinecraftClient.getInstance().player;
		return player != null ? player.getName().getString() : null;
	}

	/** Send a chat command (no leading slash); no-op when not connected. */
	private static void sendChatCommand(String command) {
		var network = MinecraftClient.getInstance().getNetworkHandler();
		if (network != null) {
			network.sendChatCommand(command);
		}
	}

	private static void dispatchChat(String text) {
		BoosterTracker.onMessage(text);
		MiniEventTracker.onMessage(text);
		PitTracker.onMessage(text);
		LabWarsTracker.onMessage(text);
		ChumTimer.onMessage(text);
		RentalMountTimer.onMessage(text);
		PersonalBoosters.onMessage(text);
		BountyTracker.onMessage(text);
		DailyTracker.onMessage(text);
		VoteTracker.onMessage(text);
		ChemtainerTracker.onMessage(text);
		// Before the sell tracker: a satchel load can land in the same batch as a sale,
		// and its identity has to be known by the time that sale flushes.
		SmugglerSatchel.onMessage(text);
		MasterySellTracker.onMessage(text);
		RunnerTracker.onMessage(text);
		McmmoCooldownTracker.onMessage(text);
		PitItemCooldownTracker.onMessage(text);
		if (MasteryChatTracker.onMessage(text)) {
			// A chat reaction moved an active challenge; keep it across a restart.
			MasteryStore.save();
		}
	}
}
