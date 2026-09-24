package dev.jade.labsaddons.casino;

import dev.jade.labsaddons.blackjack.BjBoard;
import dev.jade.labsaddons.coinflip.CfFlipBoard;
import dev.jade.labsaddons.coinflip.CfLobbyBoard;
import dev.jade.labsaddons.crate.CrateSpinBoard;
import dev.jade.labsaddons.crate.VoteRollBoard;
import dev.jade.labsaddons.double2.D2Screen;
import dev.jade.labsaddons.mines.MinesBoard;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.List;

/**
 * The boards the screen hooks ask, in order.
 *
 * <p>Six boards is well past the point at which the mixins should name one of them. Each
 * board recognises its own menu by a couple of slot names, so the order here only decides
 * who is asked first, never who wins.
 */
public final class CasinoBoards {
	private static final List<CasinoPanel> BOARDS = List.of(
			D2Screen.INSTANCE,
			MinesBoard.INSTANCE,
			BjBoard.INSTANCE,
			BetBoard.INSTANCE,
			CfLobbyBoard.INSTANCE,
			CfFlipBoard.INSTANCE,
			CrateSpinBoard.INSTANCE,
			VoteRollBoard.INSTANCE);

	/** The board currently standing in for a menu, or null. */
	private static CasinoPanel current;

	private CasinoBoards() {
	}

	public static boolean render(AbstractContainerScreen<?> screen, GuiGraphicsExtractor context,
			int mouseX, int mouseY) {
		for (CasinoPanel board : BOARDS) {
			if (board.render(screen, context, mouseX, mouseY)) {
				// Whoever held this container before does not any more. Without this a
				// loser keeps its syncId and would go on claiming to recognise a menu it
				// never draws — which would suppress that menu's tooltips.
				for (CasinoPanel other : BOARDS) {
					if (other != board && other.owns(screen)) {
						other.release();
					}
				}
				current = board;
				return true;
			}
		}
		current = null;
		return false;
	}

	/** Whether any board is standing in for this menu. */
	public static boolean recognises(AbstractContainerScreen<?> screen) {
		if (current != null && current.owns(screen)) {
			return true;
		}
		for (CasinoPanel board : BOARDS) {
			if (board.recognises(screen)) {
				return true;
			}
		}
		return false;
	}

	public static boolean mouseClicked(AbstractContainerScreen<?> screen, double mouseX, double mouseY,
			int button) {
		for (CasinoPanel board : BOARDS) {
			if (board.mouseClicked(screen, mouseX, mouseY, button)) {
				return true;
			}
		}
		return false;
	}

	/** The coinflip lobby scrolls; every other board ignores the wheel. */
	public static boolean mouseScrolled(AbstractContainerScreen<?> screen, double amount) {
		for (CasinoPanel board : BOARDS) {
			if (board.mouseScrolled(screen, amount)) {
				return true;
			}
		}
		return false;
	}
}
