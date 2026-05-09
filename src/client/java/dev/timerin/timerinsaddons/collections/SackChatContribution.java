package dev.timerin.timerinsaddons.collections;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;

import dev.timerin.timerinsaddons.skyblock.NeuDisplayNameIndex;
import dev.timerin.timerinsaddons.tracker.TrackerEntry;
import dev.timerin.timerinsaddons.tracker.TrackerStore;

/**
 * SkyBlock {@code [Sacks]} lines: Hypixel often puts the real {@code [+-]n Item (sacks)} lines only inside
 * {@link HoverEvent.ShowText} tooltips — the visible chat line may be minimal until you hover. We collect every
 * ShowText body in the message tree (same data the client shows on hover) and parse sack deltas from that text.
 */
public final class SackChatContribution {
	/**
	 * Same pattern as SkyHanni {@code sackChangeRegex}: delta, display name, sack list in parentheses.
	 */
	private static final Pattern SACK_CHANGE_LINE = Pattern.compile("([+-][\\d,]+) (.+) \\((.+)\\)");
	private static final Pattern GEM_TIER_AMOUNT = Pattern.compile("(?i)\\b(rough|flawed|fine|flawless|perfect)\\s*:\\s*([+-]?[\\d,]+)");

	private static volatile CollectionStore boundStore;
	private static volatile TrackerStore boundTrackerStore;
	private static String lastDedupeKey;
	private static long lastDedupeMs;

	private SackChatContribution() {
	}

	public static void register(CollectionStore store, TrackerStore trackerStore) {
		boundStore = store;
		boundTrackerStore = trackerStore;
	}

	public static void onRawChatMessage(Component message) {
		CollectionStore store = boundStore;
		TrackerStore trackerStore = boundTrackerStore;
		if (store == null && trackerStore == null) {
			return;
		}
		List<String> hoverBodies = new ArrayList<>();
		collectAllShowTextBodies(message, hoverBodies);
		String hoverJoined = String.join("\n", hoverBodies);
		String dedupeKey = stripSectionCodes(message.getString()) + "\n---\n" + stripSectionCodes(hoverJoined);
		long now = System.currentTimeMillis();
		if (dedupeKey.equals(lastDedupeKey) && now - lastDedupeMs < 500L) {
			return;
		}
		lastDedupeKey = dedupeKey;
		lastDedupeMs = now;
		processSackMessage(store, trackerStore, message, hoverJoined);
	}

	private static void processSackMessage(CollectionStore store, TrackerStore trackerStore, Component message, String hoverJoined) {
		boolean wantCollections = store != null && !store.getGoals().isEmpty();
		boolean wantTracker = trackerStore != null && !trackerStore.getEntries().isEmpty();
		if (!wantCollections && !wantTracker) {
			return;
		}
		String visible = stripSectionCodes(message.getString()).trim();
		String hoverNorm = stripSectionCodes(hoverJoined).trim();
		if (!isSacksRelated(visible, hoverNorm)) {
			return;
		}
		// Hover is authoritative for [+-] lines; visible often repeats the same lines → parsing both doubled deltas.
		String sackDetailText = !hoverNorm.isEmpty() ? hoverNorm : visible;
		sackDetailText = dedupeTextLines(sackDetailText);
		Matcher lineMatcher = SACK_CHANGE_LINE.matcher(sackDetailText);
		LinkedHashSet<String> seenMatches = new LinkedHashSet<>();
		while (lineMatcher.find()) {
			String full = stripSectionCodes(lineMatcher.group(0)).trim();
			if (!full.isEmpty() && !seenMatches.add(full)) {
				continue;
			}
			long delta;
			try {
				delta = Long.parseLong(lineMatcher.group(1).replace(",", ""));
			} catch (NumberFormatException e) {
				continue;
			}
			if (delta == 0L) {
				continue;
			}
			String itemDisplay = lineMatcher.group(2).trim();
			String details = lineMatcher.group(3).trim();
			List<SackDeltaLine> expanded = expandGemstoneRarityLines(itemDisplay, details, delta);
			for (SackDeltaLine line : expanded) {
				String guessedKey = guessSkyBlockKeyFromDisplayName(line.itemDisplay());
				if (wantCollections) {
					creditMatchingGoals(store, guessedKey, line.itemDisplay(), line.delta());
				}
				if (wantTracker) {
					creditMatchingTrackers(trackerStore, guessedKey, line.itemDisplay(), line.delta());
				}
			}
		}
	}

	private record SackDeltaLine(String itemDisplay, long delta) {
	}

	/**
	 * Gemstone sack lines may aggregate all tiers under one gemstone family (e.g. "Ruby Gemstone" with details like
	 * "Rough: 2, Flawed: 1"). Split these into per-tier lines so goals/trackers can match ROUGH_/FLAWED_/... keys.
	 */
	private static List<SackDeltaLine> expandGemstoneRarityLines(String itemDisplay, String details, long delta) {
		if (itemDisplay == null || details == null) {
			return List.of(new SackDeltaLine(itemDisplay == null ? "" : itemDisplay, delta));
		}
		String itemLower = itemDisplay.toLowerCase(Locale.ROOT);
		if (!itemLower.contains("gemstone")) {
			return List.of(new SackDeltaLine(itemDisplay, delta));
		}
		Matcher m = GEM_TIER_AMOUNT.matcher(details);
		List<SackDeltaLine> out = new ArrayList<>();
		String base = itemDisplay.replaceFirst("(?i)^\\s*(rough|flawed|fine|flawless|perfect)\\s+", "").trim();
		while (m.find()) {
			String tier = m.group(1).trim().toLowerCase(Locale.ROOT);
			String rawAmount = m.group(2).replace(",", "").trim();
			long tierAmount;
			try {
				tierAmount = Long.parseLong(rawAmount);
			} catch (NumberFormatException ex) {
				continue;
			}
			long signed = tierAmount;
			if (signed > 0 && delta < 0) {
				signed = -signed;
			}
			if (signed == 0L) {
				continue;
			}
			String tieredDisplay = Character.toUpperCase(tier.charAt(0)) + tier.substring(1) + " " + base;
			out.add(new SackDeltaLine(tieredDisplay, signed));
		}
		if (out.isEmpty()) {
			return List.of(new SackDeltaLine(itemDisplay, delta));
		}
		return out;
	}

	/** Same logical line twice (e.g. duplicated hovers) would otherwise double-apply deltas. */
	private static String dedupeTextLines(String text) {
		if (text == null || text.isEmpty()) {
			return "";
		}
		LinkedHashSet<String> seen = new LinkedHashSet<>();
		StringBuilder sb = new StringBuilder();
		for (String raw : text.split("\\R")) {
			String t = raw.trim();
			if (t.isEmpty()) {
				continue;
			}
			if (seen.add(t)) {
				if (sb.length() > 0) {
					sb.append('\n');
				}
				sb.append(raw.trim());
			}
		}
		return sb.toString();
	}

	private static boolean isSacksRelated(String visible, String hoverNorm) {
		if (visible.contains("[Sacks]") || hoverNorm.contains("[Sacks]")) {
			return true;
		}
		return SACK_CHANGE_LINE.matcher(visible).find() || SACK_CHANGE_LINE.matcher(hoverNorm).find();
	}

	/**
	 * Prefer NotEnoughUpdates-REPO display→internal mapping (bundled), then {@code UPPER_SNAKE} from the visible name.
	 */
	private static String guessSkyBlockKeyFromDisplayName(String itemDisplay) {
		String t = stripSectionCodes(itemDisplay).trim();
		if (t.isEmpty()) {
			return "";
		}
		return NeuDisplayNameIndex.resolveInternalIdFromDisplayName(t)
				.orElseGet(() -> t.toUpperCase(Locale.ROOT).replace(' ', '_'));
	}

	private static void creditMatchingGoals(CollectionStore store, String guessedKey, String itemDisplay, long delta) {
		String displayNorm = stripSectionCodes(itemDisplay).trim();
		for (CollectionGoal goal : store.getGoals()) {
			String itemKey = goal.getItemKey().isBlank() ? goal.getResourceId() : goal.getItemKey().trim();
			String rid = goal.getResourceId().trim();
			if (rid.isEmpty()) {
				continue;
			}
			if (!goal.matchesSackChatName(displayNorm, guessedKey, itemDisplay)) {
				continue;
			}
			String sackKey = itemKey;
			Long beforeObj = store.getSackStoredAmount(sackKey);
			long before = beforeObj != null ? beforeObj : 0L;
			long after = Math.max(0L, before + delta);
			store.putSackStoredAmount(sackKey, after);
			if (delta > 0L) {
				store.addCollectionDelta(rid, delta);
			}
			CollectionDeltaTracker.skipNextPickupDeltaBecauseSackChat(rid);
			CollectionDeltaTracker.resyncSnapshotAfterSackChat(store, rid, itemKey);
			break;
		}
	}

	private static void creditMatchingTrackers(TrackerStore trackerStore, String guessedKey, String itemDisplay, long delta) {
		String displayNorm = stripSectionCodes(itemDisplay).trim();
		for (TrackerEntry entry : trackerStore.getEntries()) {
			if (!entry.matchesSackItemLine(displayNorm, guessedKey, itemDisplay)) {
				continue;
			}
			String sackKey = entry.getItemKey().trim();
			Long beforeObj = trackerStore.getSackStoredAmount(sackKey);
			long before = beforeObj != null ? beforeObj : 0L;
			long after = Math.max(0L, before + delta);
			trackerStore.putSackStoredAmount(sackKey, after);
			trackerStore.save();
			break;
		}
	}

	/**
	 * Every {@link HoverEvent.ShowText} in the tree (including nested tooltips inside a hover body), so we mirror
	 * what the client can show when the player hovers the chat line.
	 */
	private static void collectAllShowTextBodies(Component c, List<String> out) {
		if (c == null) {
			return;
		}
		Style st = c.getStyle();
		if (st != null) {
			HoverEvent hover = st.getHoverEvent();
			if (hover instanceof HoverEvent.ShowText showText) {
				Component inner = showText.value();
				String body = stripSectionCodes(inner.getString());
				if (!body.isEmpty()) {
					out.add(body);
				}
				// Do not recurse into inner: nested ShowText/tooltips repeat the same +n lines and double credits.
			}
		}
		ComponentContents contents = c.getContents();
		if (contents instanceof TranslatableContents tc) {
			for (Object arg : tc.getArgs()) {
				if (arg instanceof Component nested) {
					collectAllShowTextBodies(nested, out);
				}
			}
		}
		for (Component sibling : c.getSiblings()) {
			collectAllShowTextBodies(sibling, out);
		}
	}

	private static String stripSectionCodes(String s) {
		if (s == null || s.isEmpty()) {
			return "";
		}
		return s.replaceAll("§.", "");
	}

}
