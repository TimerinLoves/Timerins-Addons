package dev.timerin.timerinsaddons.skyblock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import dev.timerin.timerinsaddons.tracker.TrackerStore;

/**
 * Canonicalizes user-typed ids and compares keys loosely (namespace/path, case, separators).
 */
public final class ItemKeyMatcher {
	private ItemKeyMatcher() {
	}

	public static boolean keysMatch(String a, String b) {
		if (a == null || b == null) {
			return false;
		}
		if (a.equals(b)) {
			return true;
		}
		return normalizeForCompare(a).equals(normalizeForCompare(b));
	}

	/**
	 * Like {@link #keysMatch} but also accepts path-only / case differences when tracking stacks from Hypixel.
	 */
	public static boolean looseKeysMatch(String resolvedFromStack, String trackedKey) {
		if (resolvedFromStack == null || trackedKey == null) {
			return false;
		}
		if (keysMatch(resolvedFromStack, trackedKey)) {
			return true;
		}
		String a = resolvedFromStack.trim();
		String b = trackedKey.trim();
		if (a.equalsIgnoreCase(b)) {
			return true;
		}
		String tierA = skyBlockTierSignature(a);
		String tierB = skyBlockTierSignature(b);
		if (tierA != null && tierA.equals(tierB)) {
			return true;
		}
		return pathOnly(a).equalsIgnoreCase(pathOnly(b));
	}

	/**
	 * Hypixel collections use {@code RAW_FISH:1}; NEU uses {@code RAW_FISH-1}. Same logical material.
	 */
	private static String skyBlockTierSignature(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String u = raw.trim().toUpperCase(Locale.ROOT).replace(';', ':');
		if (u.startsWith("MINECRAFT:")) {
			return null;
		}
		int colon = u.lastIndexOf(':');
		if (colon > 0 && colon < u.length() - 1) {
			String tail = u.substring(colon + 1);
			if (tail.chars().allMatch(Character::isDigit)) {
				return u.substring(0, colon) + "#" + tail;
			}
		}
		int dash = u.lastIndexOf('-');
		if (dash > 0 && dash < u.length() - 1) {
			String tail = u.substring(dash + 1);
			if (tail.chars().allMatch(Character::isDigit)) {
				return u.substring(0, dash) + "#" + tail;
			}
		}
		return null;
	}

	private static String normalizeForCompare(String s) {
		String t = s.trim().toLowerCase(Locale.ROOT);
		t = t.replace(' ', '_');
		int colon = t.indexOf(':');
		if (colon > 0) {
			String ns = t.substring(0, colon);
			String path = t.substring(colon + 1);
			if ("minecraft".equals(ns) || "mc".equals(ns)) {
				return path;
			}
			if ("skyblock".equals(ns) || "sb".equals(ns) || "hypixel".equals(ns) || "firmskyblock".equals(ns) || "fsb".equals(ns)) {
				return path;
			}
		}
		return t;
	}

	/**
	 * Resolves a user string to a single best-matching id from known candidates (vanilla items + tracker + extras).
	 */
	public static Optional<String> smartResolve(String raw, TrackerStore store, Stream<String> extraCandidates) {
		if (raw == null || raw.isBlank()) {
			return Optional.empty();
		}
		String trimmed = raw.trim();
		Set<String> pool = new HashSet<>();
		for (var item : BuiltInRegistries.ITEM) {
			pool.add(Objects.requireNonNull(BuiltInRegistries.ITEM.getKey(item)).toString());
		}
		if (store != null) {
			for (var e : store.getEntries()) {
				pool.add(e.getItemKey());
			}
		}
		extraCandidates.filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).forEach(pool::add);

		if (pool.contains(trimmed)) {
			return Optional.of(trimmed);
		}
		String nt = trimmed.toLowerCase(Locale.ROOT).replace(' ', '_');
		for (String c : pool) {
			if (c.equalsIgnoreCase(trimmed)) {
				return Optional.of(c);
			}
		}
		for (String c : pool) {
			if (normalizeForCompare(c).equals(normalizeForCompare(trimmed))) {
				return Optional.of(c);
			}
		}
		for (String c : pool) {
			if (pathOnly(c).equalsIgnoreCase(nt) || pathOnly(c).equalsIgnoreCase(pathOnly(trimmed))) {
				return Optional.of(c);
			}
		}
		String needle = pathOnly(nt);
		List<String> starts = new ArrayList<>();
		List<String> contains = new ArrayList<>();
		for (String c : pool) {
			String p = pathOnly(c.toLowerCase(Locale.ROOT));
			if (p.equals(needle)) {
				return Optional.of(c);
			}
			if (p.startsWith(needle)) {
				starts.add(c);
			} else if (needle.length() >= 4 && p.contains(needle)) {
				contains.add(c);
			}
		}
		if (starts.size() == 1) {
			return Optional.of(starts.getFirst());
		}
		if (starts.size() > 1) {
			starts.sort(Comparator.comparingInt(String::length));
			return Optional.of(starts.getFirst());
		}
		if (contains.size() == 1) {
			return Optional.of(contains.getFirst());
		}
		Optional<String> rl = tryParseResourceLocation(trimmed);
		if (rl.isPresent() && pool.contains(rl.get())) {
			return rl;
		}
		return Optional.empty();
	}

	public static String pathOnly(String id) {
		String t = id.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
		int c = t.indexOf(':');
		return c >= 0 ? t.substring(c + 1) : t;
	}

	private static Optional<String> tryParseResourceLocation(String s) {
		try {
			return Optional.of(Identifier.parse(s).toString());
		} catch (Exception e) {
			return Optional.empty();
		}
	}
}
