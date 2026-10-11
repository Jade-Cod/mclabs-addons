package dev.jade.labsaddons.crate;

import dev.jade.labsaddons.config.LabsAddonsConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What you've taken from the voter crates and the daily spin, reward by reward.
 *
 * <p>Counted from the server's own line rather than from the click: it names the crate, which
 * the roll and the choice never do, and it only arrives once the reward is really yours. Its
 * wording is the chat's ("20 mcMMO Credits"), not the reel's ("mcMMO Credit x20"), and the
 * tally keeps it as said.
 */
public final class RollsTaken {
	public static final String DAILY = "Daily Spin";

	private static final Pattern CRATE = Pattern.compile(
			"^MCLabs\\s*»\\s*You opened an? (.+?) and unboxed (.+?)!?$", Pattern.CASE_INSENSITIVE);
	private static final Pattern SPIN = Pattern.compile(
			"^MCLabs\\s*»\\s*You rolled your Daily Spin and chose (.+?)!?$", Pattern.CASE_INSENSITIVE);

	public record Taken(String crate, String reward) {
	}

	private RollsTaken() {
	}

	/** The crate and reward a line says you took, or null for any other line, other crates included. */
	public static Taken parse(String text) {
		String line = text == null ? "" : text.strip();
		Matcher spin = SPIN.matcher(line);
		if (spin.matches()) {
			return new Taken(DAILY, spin.group(1).strip());
		}
		Matcher crate = CRATE.matcher(line);
		if (crate.matches() && VoteOdds.isVoterCrate(crate.group(1))) {
			return new Taken(crate.group(1).strip(), crate.group(2).strip());
		}
		return null;
	}

	/** {@code taken} with one more of this reward, leaving the original alone. */
	static Map<String, Map<String, Integer>> counted(Map<String, Map<String, Integer>> taken, Taken one) {
		Map<String, Map<String, Integer>> out = new LinkedHashMap<>();
		if (taken != null) {
			taken.forEach((crate, rewards) -> out.put(crate, new LinkedHashMap<>(rewards)));
		}
		out.computeIfAbsent(one.crate(), crate -> new LinkedHashMap<>()).merge(one.reward(), 1, Integer::sum);
		return out;
	}

	/** Most-taken first; a tie goes to the name, so the order doesn't shuffle between frames. */
	public static List<Map.Entry<String, Integer>> mostTaken(Map<String, Integer> rewards) {
		List<Map.Entry<String, Integer>> sorted = new ArrayList<>(rewards.entrySet());
		sorted.sort(Map.Entry.<String, Integer>comparingByValue().reversed()
				.thenComparing(Map.Entry.comparingByKey(Comparator.comparing(String::toLowerCase))));
		return sorted;
	}

	public static Map<String, Integer> forCrate(String crate) {
		Map<String, Integer> rewards = LabsAddonsConfig.get().rollsTaken.get(crate);
		return rewards == null ? Map.of() : rewards;
	}

	public static void onMessage(String text) {
		Taken one = parse(text);
		if (one == null) {
			return;
		}
		LabsAddonsConfig config = LabsAddonsConfig.get();
		config.rollsTaken = counted(config.rollsTaken, one);
		config.save();
	}
}
