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
import dev.jade.fishbite.booster.BoosterTracker;
import dev.jade.fishbite.bounty.BountyHudObject;
import dev.jade.fishbite.bounty.BountyTracker;
import dev.jade.fishbite.daily.DailyReminderHudObject;
import dev.jade.fishbite.daily.DailyTracker;
import dev.jade.fishbite.daily.VoteReminderHudObject;
import dev.jade.fishbite.daily.VoteTracker;
import dev.jade.fishbite.chum.ChumDetector;
import dev.jade.fishbite.chum.ChumHudObject;
import dev.jade.fishbite.hud.HudEditScreen;
import dev.jade.fishbite.hud.HudObjects;
import dev.jade.fishbite.config.FishBiteConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.ActionResult;
import org.lwjgl.glfw.GLFW;

/**
 * Client entrypoint. Wires up the bite marker (HUD-projected, see
 * {@link BiteMarkerHud}) and the Chum Bucket timer (detection + HUD + editor).
 */
public class FishBiteClient implements ClientModInitializer {
	private static KeyBinding chumEditorKey;
	private static Object lastRatesScreen;

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

		// Track boosters, mini-events, and the Pit from chat/system announcements.
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) {
				dispatchChat(message.getString());
			}
		});
		ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) ->
				dispatchChat(message.getString()));

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
				GLFW.GLFW_KEY_SEMICOLON, KeyBinding.Category.GAMEPLAY));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (chumEditorKey.wasPressed()) {
				client.setScreen(new HudEditScreen(client.currentScreen));
			}
			// Passive: scrape the /lw rates GUI ONCE per open (its lore is a static
			// snapshot; re-reading every tick would freeze the countdown).
			net.minecraft.client.gui.screen.Screen current = client.currentScreen;
			if (current instanceof HandledScreen<?> handledScreen) {
				if (current != lastRatesScreen && LabWarsRatesReader.tryRead(handledScreen)) {
					lastRatesScreen = current;
				}
			} else {
				lastRatesScreen = null;
			}
		});
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
	}
}
