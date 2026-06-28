package dev.jade.fishbite.personal;

import dev.jade.fishbite.config.FishBiteConfig;
import dev.jade.fishbite.hud.Durations;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Personal boosts: a 10% chem-price boost and a 10% prestige-progress boost.
 * Founder and normal prestige are the same 10% boost and mutually exclusive, so
 * they share one timer. Seeded from redeem chat and synced exactly from
 * /checkboost output (which the player runs — passive, never auto-sent).
 */
public final class PersonalBoosters {
	private static final Pattern REDEEM_CHEM = Pattern.compile(
			"redeemed\\s+(.+?)\\s+of personal 10% drug price boost", Pattern.CASE_INSENSITIVE);
	private static final Pattern REDEEM_PRESTIGE = Pattern.compile(
			"redeemed\\s+(.+?)\\s+of personal 10% (?:Founder )?prestige progress boost",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern CHECK_CHEM = Pattern.compile(
			"personal chem price boost:\\s*(.+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern CHECK_PRESTIGE = Pattern.compile(
			"personal (?:Founder )?prestige progress boost:\\s*(.+)", Pattern.CASE_INSENSITIVE);

	private PersonalBoosters() {
	}

	public static void onMessage(String text) {
		Matcher redeem = REDEEM_CHEM.matcher(text);
		if (redeem.find()) {
			setChem(Durations.parseMs(redeem.group(1)));
		}
		Matcher redeemPrestige = REDEEM_PRESTIGE.matcher(text);
		if (redeemPrestige.find()) {
			setPrestige(Durations.parseMs(redeemPrestige.group(1)));
		}
		Matcher chem = CHECK_CHEM.matcher(text);
		if (chem.find()) {
			setChem(Durations.parseMs(chem.group(1)));
		}
		Matcher prestige = CHECK_PRESTIGE.matcher(text);
		if (prestige.find()) {
			setPrestige(Durations.parseMs(prestige.group(1)));
		}
	}

	private static void setChem(long ms) {
		if (ms > 0) {
			FishBiteConfig.get().personalChemPriceExpiryMs = System.currentTimeMillis() + ms;
			FishBiteConfig.get().save();
		}
	}

	private static void setPrestige(long ms) {
		if (ms > 0) {
			FishBiteConfig.get().personalPrestigeExpiryMs = System.currentTimeMillis() + ms;
			FishBiteConfig.get().save();
		}
	}

	public static long chemRemainingMs() {
		return Math.max(0L, FishBiteConfig.get().personalChemPriceExpiryMs - System.currentTimeMillis());
	}

	public static long prestigeRemainingMs() {
		return Math.max(0L, FishBiteConfig.get().personalPrestigeExpiryMs - System.currentTimeMillis());
	}

	public static boolean anyActive() {
		return chemRemainingMs() > 0 || prestigeRemainingMs() > 0;
	}

	public static void clear() {
		FishBiteConfig config = FishBiteConfig.get();
		config.personalChemPriceExpiryMs = 0L;
		config.personalPrestigeExpiryMs = 0L;
		config.save();
	}
}
