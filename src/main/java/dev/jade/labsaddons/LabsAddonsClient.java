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
import dev.jade.labsaddons.mines.MinesChat;
import dev.jade.labsaddons.mount.RentalMountHudObject;
import dev.jade.labsaddons.mount.RentalMountTimer;
import dev.jade.labsaddons.rental.RentalHudObject;
import dev.jade.labsaddons.rental.RentalReader;
import dev.jade.labsaddons.rental.RentalTracker;
import dev.jade.labsaddons.personal.PersonalBoosterHudObject;
import dev.jade.labsaddons.personal.PersonalBoosters;
import dev.jade.labsaddons.chum.ChumTimer;
import dev.jade.labsaddons.booster.BoosterRatesReader;
import dev.jade.labsaddons.booster.BoosterTracker;
import dev.jade.labsaddons.bounty.BountyHudObject;
import dev.jade.labsaddons.bounty.BountyTracker;
import dev.jade.labsaddons.bounty.SunkenTreasureReader;
import dev.jade.labsaddons.bounty.SunkenTreasureTracker;
import dev.jade.labsaddons.blackjack.BjChat;
import dev.jade.labsaddons.coinflip.CfChat;
import dev.jade.labsaddons.coinflip.CfLobbyReader;
import dev.jade.labsaddons.coinflip.CfFlipBoard;
import dev.jade.labsaddons.coinflip.CfOpenHudObject;
import dev.jade.labsaddons.coinflip.CfResultScreen;
import dev.jade.labsaddons.coinflip.CfRecordHudObject;
import dev.jade.labsaddons.coinflip.CfStats;
import dev.jade.labsaddons.crate.CratePity;
import dev.jade.labsaddons.crate.DailySpin;
import dev.jade.labsaddons.crate.CratePityReader;
import dev.jade.labsaddons.crate.VoteOddsReader;
import dev.jade.labsaddons.double2.D2Chat;
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
import dev.jade.labsaddons.hud.ProgressHudObject;
import dev.jade.labsaddons.mastery.MasteryCatchTracker;
import dev.jade.labsaddons.mastery.MasterySellTracker;
import dev.jade.labsaddons.mastery.MasteryKillTracker;
import dev.jade.labsaddons.mastery.MasteryReader;
import dev.jade.labsaddons.mastery.MasteryStore;
import dev.jade.labsaddons.police.PoliceContraband;
import dev.jade.labsaddons.police.PolicePrestigeReader;
import dev.jade.labsaddons.prestige.PrestigeChat;
import dev.jade.labsaddons.raidmine.RaidMineHologramReader;
import dev.jade.labsaddons.raidmine.RaidMineHudObject;
import dev.jade.labsaddons.raidmine.RaidMineTracker;
import dev.jade.labsaddons.prestige.PrestigeStore;
import dev.jade.labsaddons.runner.RunnerAlarm;
import dev.jade.labsaddons.runner.RunnerHudObject;
import dev.jade.labsaddons.runner.RunnerTracker;
import dev.jade.labsaddons.runner.SupplierJobsReader;
import dev.jade.labsaddons.hud.HudEditScreen;
import dev.jade.labsaddons.hud.HudObjects;
import dev.jade.labsaddons.config.LabsAddonsConfig;
import dev.jade.labsaddons.server.McLabsSession;
import dev.jade.labsaddons.server.McLabsWorld;
import dev.jade.labsaddons.update.ModrinthUpdateChecker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import dev.jade.labsaddons.mixin.KeyBindingCategoryAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
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
	/** The menu being waited on, and for how long; see {@link #readyToScrape}. */
	private static net.minecraft.client.gui.screen.Screen scrapeWaitScreen;
	private static int scrapeWaits;
	/**
	 * How long to wait for a menu's contents before scraping it anyway.
	 *
	 * <p>A second, which is far longer than the gap between the packet that opens a menu and the
	 * one that fills it, and short enough that a menu which is genuinely empty is not waited on.
	 */
	private static final int SCRAPE_WAIT_TICKS = 20;

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
		// Prestige tracks need no item registry, so they restore here rather than on
		// join. Fabric swallows exceptions thrown from JOIN handlers, so a failed Mastery
		// restore there would silently leave every sale uncounted until /prestige progress.
		PrestigeStore.load();
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
		HudObjects.register(new RaidMineHudObject());
		HudObjects.register(new LabWarsHudObject());
		HudObjects.register(new RentalMountHudObject());
		HudObjects.register(new RentalHudObject());
		HudObjects.register(new PersonalBoosterHudObject());
		HudObjects.register(new BountyHudObject());
		HudObjects.register(new DailyReminderHudObject());
		HudObjects.register(new VoteReminderHudObject());
		HudObjects.register(new ChemtainerHudObject());
		// One widget for both Mastery challenges and chem prestige: same row shape,
		// same notification-then-fade behaviour, one thing to place on screen.
		HudObjects.register(new ProgressHudObject());
		MasteryChatTracker.setSelfNameSupplier(LabsAddonsClient::selfName);
		HudObjects.register(new RunnerHudObject());
		HudObjects.register(new CooldownHudObject());
		HudObjects.register(new CfOpenHudObject());
		HudObjects.register(new CfRecordHudObject());
		CooldownHudObject.addSource(McmmoCooldownTracker.source());
		McmmoCooldownTracker.setHeldToolResolver(LabsAddonsClient::heldTool);
		CooldownHudObject.addSource(PitItemCooldownTracker.source());

		// Track boosters, mini-events, and the Pit from chat/system announcements.
		// Actionbar (overlay) text is captured in InGameHudMixin instead: servers
		// that send it via the Set Action Bar Text packet never reach this event.
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) {
				dispatchChat(message);
			}
		});
		ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) ->
				dispatchChat(message));

		// The MCLabs-only HUD widgets and update check key off this per-connection
		// session flag; reset it on every fresh connection so a stale "yes" can't
		// leak into a different server (or singleplayer).
		// Raid Mine resource holograms: the mine drops no items, so the popup the
		// server spawns in their place is the only record of what was generated.
		ClientEntityEvents.ENTITY_LOAD.register(RaidMineHologramReader::onEntityLoad);

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			McLabsSession.reset();
			// Restore the saved boards on join rather than at startup. Building a quest
			// icon needs item components bound, which happens later than both
			// CLIENT_STARTED and the first client tick — both crashed with "Components
			// not bound yet". Joining a world cannot happen before the client is fully
			// loaded, and it is also the first moment the boards are of any use, since
			// they exist so chat reactions count before the session's first /mastery.
			if (!storesRestored) {
				storesRestored = true;
				MasteryStore.load();
			}
		});
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
			PrestigeChat.reset();
			// Last round's payout belongs to the server we just left.
			D2Chat.reset();
			MinesChat.reset();
			BjChat.reset();
			// Open flips and the session ledger belong to the server we just left; the
			// lifetime record does not, and stays in the config.
			CfChat.reset();
			// Entity ids are per-server; drop any hologram queued for reading.
			RaidMineHologramReader.reset();
			// The world we remember belongs to the server we just left.
			McLabsWorld.reset();
			// Likewise a daily spin left part-way through.
			DailySpin.reset();
			// The menus remembered for the once-per-open scrape belong to the world we just
			// left, and each is a live reference to a screen handler and every stack in it.
			// They are the only Minecraft objects this class holds across a disconnect.
			lastRatesScreen = null;
			scrapeWaitScreen = null;
			scrapeWaits = 0;
			// Saves are coalesced onto a background thread, so leaving a server is one
			// of the points that has to wait for them to actually land.
			LabsAddonsConfig.get().saveNow();
		});

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> LabsAddonsConfig.get().saveNow());

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
			// A typed "/cf take <id>". The clicked one never gets here — see
			// ClientPlayNetworkHandlerMixin — but both end up in the same place.
			CfChat.onCommandSent(sent, Util.getMeasuringTimeMs());
		});

		// Attribute Pit kills. The client is never told who dealt a mob its fatal
		// blow, so the player's own swings are the only attribution available: a
		// mob that dies without one of these is someone else's kill.
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (player == MinecraftClient.getInstance().player && McLabsSession.isActive()) {
				MasteryKillTracker.onPlayerHit(entity.getId());
			}
			return ActionResult.PASS;
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
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (McLabsSession.tick(client)) {
				ModrinthUpdateChecker.checkAndNotify();
			}
			RunnerAlarm.tick();
			// Rented items are read off the inventory, which is also where the return
			// reminder is checked from — once a second, not every tick.
			if (McLabsSession.isActive()) {
				RentalReader.tick(client.player == null ? null : client.player.getInventory());
			}
			RaidMineHologramReader.tick(client);
			// Live "Kill <mob>" progress: no chat line announces a Pit kill, so deaths
			// are read straight off the world and matched against the mobs we hit.
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
			// The coinflip chest closes seconds after the coin lands. If one just did,
			// put the result back up — once the container is really gone, so this never
			// fights the screen it came from.
			if (client.currentScreen == null) {
				CfFlipBoard.Landed landed = CfFlipBoard.INSTANCE.takePendingResult();
				if (landed != null) {
					client.setScreen(new CfResultScreen(landed));
				}
			}
			ChemtainerDepositCapture.tick();
			// Passive: scrape the /lw rates and /chems booster GUIs ONCE per open
			// (their lore is a static snapshot; re-reading every tick would freeze
			// the countdown).
			net.minecraft.client.gui.screen.Screen current = client.currentScreen;
			if (current instanceof HandledScreen<?> handledScreen) {
				if (current != lastRatesScreen && readyToScrape(handledScreen)) {
					lastRatesScreen = current;
					// Ahead of the chain rather than in it: a satchel read only needs the
					// title to disagree to bail, and keeping it out avoids another
					// nesting level in an already-deep fall-through.
					SmugglerSatchel.tryRead(handledScreen);
					// Seed the coinflip record the first time the lobby is ever opened,
					// and never again: the widget keeps itself up to date from the
					// result lines after that, and firing a command every time somebody
					// runs /cf would be noise.
					// shouldAsk() counts the ask, so it goes last: every other test has to
					// have passed before one is spent.
					if (!CfStats.seeded()
							&& CfLobbyReader.isLobbyTitle(handledScreen.getTitle().getString())
							&& CfStats.shouldAsk()) {
						sendChatCommand("cf stats");
					}
					// Out of the chain as well: a voter crate's odds menu is nobody
					// else's screen, and it is the only place those figures are ever
					// stated — the roll that follows shows three items and no odds.
					// Before the readers: /daily's spin and a voter crate's are the same
					// screen down to the slot, and the only thing that tells them apart is
					// the menu that handed over to it.
					DailySpin.onScreen(handledScreen.getTitle().getString(),
							net.minecraft.util.Util.getMeasuringTimeMs());
					VoteOddsReader.tryRead(handledScreen);
					// And out of it too: "Your Exceedingly Rare odds" is the only place the
					// server says which roll of the pity ladder you are on.
					CratePityReader.tryRead(handledScreen);
					// Likewise out of the chain: /fw is nobody else's screen, and the
					// chain is already as deep as it should get.
					SunkenTreasureReader.tryRead(handledScreen);
					// And /rent return: its rows carry "Expiry:", which nothing else does.
					RentalReader.tryReadReturnMenu(handledScreen);
					// Also out of the chain: the /prestige GUI is nobody else's screen.
					if (PolicePrestigeReader.tryRead(handledScreen)) {
						PrestigeStore.save();
					}
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

	/**
	 * Whether the open menu has anything in it yet, so scraping it will see what it holds.
	 *
	 * <p>A menu arrives in two packets: one opens the screen, another fills its slots. They
	 * usually land in the same tick, but nothing guarantees it — and every reader above runs
	 * exactly once per open, on the first tick the screen is seen, and then never again for it.
	 * One late slot packet therefore meant the menu was never read at all, silently, until it was
	 * opened again. The whole point of reading once is that the lore is a snapshot, so the answer
	 * is to wait for the snapshot rather than to keep re-reading it.
	 *
	 * <p>Gives up after {@link #SCRAPE_WAIT_TICKS} and reads anyway: a container that is simply
	 * empty has nothing to hand any reader, and every one of them bails on the title regardless.
	 */
	private static boolean readyToScrape(HandledScreen<?> screen) {
		// Each menu gets the whole wait to itself: a second one opening straight after the first
		// must not inherit what the first spent, or it is read early on an empty container —
		// which is the very thing being fixed.
		if (screen != scrapeWaitScreen) {
			scrapeWaitScreen = screen;
			scrapeWaits = 0;
		}
		// The trailing 36 slots are always the player's own, and are filled locally — so they
		// say nothing about whether the server has sent this menu.
		List<Slot> slots = screen.getScreenHandler().slots;
		for (int slot = 0; slot < slots.size() - 36; slot++) {
			if (!slots.get(slot).getStack().isEmpty()) {
				return true;
			}
		}
		return scrapeWaits++ >= SCRAPE_WAIT_TICKS;
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

	/**
	 * Chat entry point. Nearly every tracker wants the flattened string, but prestige
	 * figures live in the message's hover tooltips, which {@code getString()} discards —
	 * so the {@code Text} is passed along intact rather than flattened at the door.
	 */
	private static void dispatchChat(Text message) {
		dispatchChat(message.getString());
		if (PrestigeChat.onMessage(message)) {
			// A sync or a sale moved a prestige track; keep it across a restart.
			PrestigeStore.save();
		}
	}

	/** Guards the one-shot restore of the Mastery and prestige boards; see the tick handler. */
	private static boolean storesRestored;

	private static void dispatchChat(String text) {
		// First: the join banner names the world, which may swap the whole HUD profile.
		McLabsWorld.onMessage(text);
		BoosterTracker.onMessage(text);
		MiniEventTracker.onMessage(text);
		PitTracker.onMessage(text);
		RaidMineTracker.onMessage(text);
		LabWarsTracker.onMessage(text);
		ChumTimer.onMessage(text);
		RentalMountTimer.onMessage(text);
		RentalTracker.onMessage(text, System.currentTimeMillis());
		PersonalBoosters.onMessage(text);
		BountyTracker.onMessage(text);
		SunkenTreasureTracker.onMessage(text);
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
		// Only on MCLabs: this block writes the lifetime coinflip record and the pity
		// ladders into the config, and a line shaped like one of these on some other server
		// would be written there for good. The trackers above stay ungated deliberately —
		// they catch join-time announcements, which can land before the sidebar this flag
		// reads off has arrived.
		if (McLabsSession.isActive()) {
			dispatchCasinoChat(text);
		}
		if (MasteryChatTracker.onMessage(text)) {
			// A chat reaction moved an active challenge; keep it across a restart.
			MasteryStore.save();
		}
		if (PoliceContraband.onMessage(text)) {
			// A confiscation moves the police prestige tier and every patrol challenge it
			// counted toward, so both boards are written back.
			PrestigeStore.save();
			MasteryStore.save();
		}
	}

	/**
	 * The casino and crate readers, which only ever have anything to say on MCLabs.
	 *
	 * <p>Its own method rather than another nesting level in {@link #dispatchChat}, which is
	 * already a long flat list and reads best as one.
	 */
	private static void dispatchCasinoChat(String text) {
		// The casino boards show the server's own payout figures rather than computing
		// one: the menus stop stating a result the moment a game ends.
		D2Chat.onMessage(text);
		MinesChat.onMessage(text);
		BjChat.onMessage(text);
		// Coinflip is the one game the whole server hears about, so its chat feeds the
		// open-flips widget as well as our own result. Our name is needed to tell our
		// posts and results apart from everyone else's.
		CfChat.Outcome coinflip = CfChat.onMessage(text, selfName(),
				net.minecraft.util.Util.getMeasuringTimeMs());
		if (coinflip != null) {
			if (coinflip.won()) {
				CfStats.recordWin(coinflip.wagerCents(), coinflip.returnedCents(),
						coinflip.opponent());
			} else {
				CfStats.recordLoss(coinflip.wagerCents(), coinflip.opponent());
			}
		}
		CfStats.onMessage(text);
		// The server saying what you took ends the daily spin, so a voter crate rolled
		// afterwards is not mistaken for another one.
		DailySpin.onMessage(text);
		// "[⚡ Your Exceedingly Rare odds have been JACKED-UP! ⚡]" — one per crate roll, in
		// every capture, including the ones that won a Rare. It is the only signal for a crate
		// opened with the board off, so it counts the roll whenever the board has not already.
		// A Crate Roll Booster is not a roll — no key is spent and no odds-went-up line follows
		// it — but it moves every ladder forward by whatever the voucher was worth.
		int boost = CratePity.boostedRolls(text);
		if (boost > 0) {
			LabsAddonsConfig boosted = LabsAddonsConfig.get();
			java.util.Map<String, Integer> next =
					CratePity.boosted(boosted.cratePityRolls, boost);
			if (next != null) {
				boosted.cratePityRolls = next;
				boosted.save();
			}
		}
		if (CratePity.isPityTick(text)) {
			long now = net.minecraft.util.Util.getMeasuringTimeMs();
			if (CratePity.isNewRoll(now)) {
				CratePity.counted(now);
				LabsAddonsConfig config = LabsAddonsConfig.get();
				java.util.Map<String, Integer> rolled =
						CratePity.rolled(config.cratePityRolls, null);
				if (rolled != null) {
					config.cratePityRolls = rolled;
					config.save();
				}
			}
		}
	}
}
