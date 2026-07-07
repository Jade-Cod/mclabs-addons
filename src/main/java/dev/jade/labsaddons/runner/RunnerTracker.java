package dev.jade.labsaddons.runner;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tracks runner job events from chat for the current session. State is in-memory
 * only — runner jobs are transient; there's nothing meaningful to persist across
 * relogs. Three message types are handled:
 * <ul>
 *   <li>Job posted: {@code MCLabs » Runner job posted: Nx Drug at P%.}</li>
 *   <li>Job completed: {@code Runner » Player has completed your runner job, you've earned $X.}</li>
 *   <li>Job failed: {@code Runner » Player failed your runner job! (Drug xN)}</li>
 * </ul>
 * Posted count increments on post, decrements on complete or fail (clamped at 0).
 */
public final class RunnerTracker {
    private static final Pattern POSTED = Pattern.compile(
            "MCLabs.*Runner job posted:", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMPLETED = Pattern.compile(
            "completed your runner job.*you've earned \\$([0-9,.]+[kmKM]?)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FAILED = Pattern.compile(
            "failed your runner job", Pattern.CASE_INSENSITIVE);

    private static volatile int postedJobs = 0;
    private static volatile int completedJobs = 0;
    private static volatile int failedJobs = 0;
    private static volatile double totalEarned = 0.0;

    private RunnerTracker() {
    }

    public static synchronized void onMessage(String text) {
        if (POSTED.matcher(text).find()) {
            postedJobs++;
            return;
        }
        Matcher completed = COMPLETED.matcher(text);
        if (completed.find()) {
            completedJobs++;
            postedJobs = Math.max(0, postedJobs - 1);
            totalEarned += parseMoney(completed.group(1));
            return;
        }
        if (FAILED.matcher(text).find()) {
            failedJobs++;
            postedJobs = Math.max(0, postedJobs - 1);
        }
    }

    private static double parseMoney(String value) {
        String clean = value.toLowerCase(Locale.ROOT).replace(",", "");
        try {
            if (clean.endsWith("k")) {
                return Double.parseDouble(clean.substring(0, clean.length() - 1)) * 1_000;
            }
            if (clean.endsWith("m")) {
                return Double.parseDouble(clean.substring(0, clean.length() - 1)) * 1_000_000;
            }
            return Double.parseDouble(clean);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /** True once any runner event has been seen this session. */
    public static synchronized boolean hasActivity() {
        return postedJobs > 0 || completedJobs > 0 || failedJobs > 0;
    }

    public static synchronized int postedJobs() {
        return postedJobs;
    }

    public static synchronized int completedJobs() {
        return completedJobs;
    }

    public static synchronized int failedJobs() {
        return failedJobs;
    }

    public static synchronized double totalEarned() {
        return totalEarned;
    }

    /** Zero all session counters. */
    public static synchronized void resetSession() {
        postedJobs = 0;
        completedJobs = 0;
        failedJobs = 0;
        totalEarned = 0.0;
    }
}
