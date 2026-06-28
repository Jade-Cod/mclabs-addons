package dev.jade.fishbite;

import dev.jade.fishbite.booster.BoosterHudObject;
import dev.jade.fishbite.event.MiniEventHudObject;
import dev.jade.fishbite.event.MiniEventTracker;
import dev.jade.fishbite.event.PitHudObject;
import dev.jade.fishbite.event.PitTracker;
import dev.jade.fishbite.labwars.LabWarsHudObject;
import dev.jade.fishbite.labwars.LabWarsRatesReader;
import dev.jade.fishbite.labwars.LabWarsTracker;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import dev.jade.fishbite.mount.RentalMountHudObject;
import dev.jade.fishbite.mount.RentalMountTimer;
import dev.jade.fishbite.personal.PersonalBoosterHudObject;
import dev.jade.fishbite.personal.PersonalBoosters;
import dev.jade.fishbite.chum.ChumTimer;
import dev.jade.fishbite.booster.BoosterRatesReader;
import dev.jade.fishbite.booster.BoosterTracker;
import dev.jade.fishbite.bounty.BountyHudObject;
import dev.jade.fishbite.bounty.BountyTracker;
import dev.jade.fishbite.daily.DailyReminderHudObject;
import dev.jade.fishbite.daily.DailyTracker;
import dev.jade.fishbite.daily.VoteReminderHudObject;
import dev.jade.fishbite.daily.VoteTracker;
import dev.jade.fishbite.chem.ChemItems;
import dev.jade.fishbite.chem.ChemtainerDepositCapture;
import dev.jade.fishbite.chem.ChemtainerHudObject;
import dev.jade.fishbite.chem.ChemtainerReader;
import dev.jade.fishbite.chem.ChemtainerTracker;
import dev.jade.fishbite.chum.ChumDetector;
import dev.jade.fishbite.chum.ChumHudObject;
import dev.jade.fishbite.hud.HudEditScreen;
import dev.jade.fishbite.hud.HudObjects;
import dev.jade.fishbite.config.FishBiteConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import dev.jade.fishbite.mixin.KeyBindingCategoryAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

/**
 * Client entrypoint. Wires up the bite marker (HUD-projected, see
 * {@link BiteMarkerHud}) and the Chum Bucket timer (detection + HUD + editor).
 */
public class FishBiteClient implements ClientModInitializer {
	/** The mod's own keybind category, moved near the top of the Controls screen. */
	public static final KeyBinding.Category MCLAB_CATEGORY = registerCategory();

	private static KeyBinding chumEditorKey;
	private static KeyBinding chemDepositKey;
	private static KeyBinding chemWithdrawKey;
	private static Object lastRatesScreen;

	/** Create the "McLab Addons" category and hoist it above the vanilla ones
	 *  (mod categories are otherwise appended last). Best-effort: if the registry
	 *  list can't be reordered the category simply stays at the bottom. */
	private static KeyBinding.Category registerCategory() {
		KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("fishbite", "main"));
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
		FishBiteConfig.get();

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

		// Track boosters, mini-events, and the Pit from chat/system announcements.
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) {
				dispatchChat(message.getString());
			}
		});
		ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) ->
				dispatchChat(message.getString()));

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

		// Detect Chum Bucket activation (right-click with the item in hand).
		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (player == MinecraftClient.getInstance().player) {
				var stack = player.getStackInHand(hand);
				if (ChumDetector.isChumBucket(stack)) {
					ChumDetector.tryActivate(player.getInventory().getSelectedSlot());
				} else {
					RentalMountTimer.tryCoupon(stack);
				}
			}
			return ActionResult.PASS;
		});

		// Keybind to open the draggable chum HUD editor (unbound by default).
		chumEditorKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.fishbite.chum_editor", InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_SEMICOLON, MCLAB_CATEGORY));
		// Chemtainer deposit (default B): send "/ch qd" and track what gets banked.
		chemDepositKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.fishbite.chem_deposit", InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_B, MCLAB_CATEGORY));
		// Chemtainer withdraw (default N): pull back the largest chem you have.
		chemWithdrawKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.fishbite.chem_withdraw", InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_N, MCLAB_CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
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
				if (current != lastRatesScreen
						&& (LabWarsRatesReader.tryRead(handledScreen)
								|| BoosterRatesReader.tryRead(handledScreen)
								|| ChemtainerReader.tryRead(handledScreen))) {
					lastRatesScreen = current;
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
	}
}
