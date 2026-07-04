package dev.jade.fishbite;

import org.joml.Matrix4f;

/**
 * Holds the exact projection matrix {@link dev.jade.fishbite.mixin.GameRendererProjectionMixin}
 * captures from {@code GameRenderer.renderWorld} each frame — the same matrix instance the game
 * actually renders with, including whatever a third-party zoom mod did to it. Recomputing FOV
 * ourselves (calling {@code getFov} a second time) was fragile: it matched vanilla's arguments
 * exactly but still drifted from the real value during an active zoom transition, since some
 * zoom mods key their eased FOV off render-order-sensitive state. Capturing the real matrix by
 * reference instead of replaying its computation removes that class of mismatch entirely.
 */
public final class RenderFrameState {
	public static final Matrix4f PROJECTION = new Matrix4f();

	private RenderFrameState() {
	}
}
