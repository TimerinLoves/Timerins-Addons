package dev.timerin.timerinsaddons.collections;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import dev.timerin.timerinsaddons.skyblock.ItemKeyMatcher;
import dev.timerin.timerinsaddons.skyblock.NeuInternalItemIcons;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Persists SkyBlock collection goals in {@code config/timerins_addons/collections.json}.
 */
public final class CollectionStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path file;
	private CollectionHudSettings settings = new CollectionHudSettings();
	private final Map<String, CollectionGoal> goalsByResourceId = new LinkedHashMap<>();
	/** Latest Hypixel profile collection totals (runtime; from Public API). */
	private final Map<String, Long> apiCollectionAmounts = new ConcurrentHashMap<>();
	/**
	 * Increments observed from {@link CollectionDeltaTracker} since the last time Hypixel reported a new total for
	 * that resource; added on top of {@link #apiCollectionAmounts} for display.
	 */
	private final Map<String, Long> collectionDeltaSinceApi = new ConcurrentHashMap<>();
	/** Per-resource totals from the previous successful profile fetch — used to spot stale repeats from Hypixel. */
	private final Map<String, Long> lastHypixelPollSnapshot = new ConcurrentHashMap<>();
	/**
	 * Last known amount stored in sacks for a SkyBlock item key (from open sack GUI lore and from {@code [Sacks]} chat
	 * deltas). Used with inventory counts so pickup tracking has explicit before/after for sack totals.
	 */
	private final Map<String, Long> sackStoredAmounts = new ConcurrentHashMap<>();

	public CollectionStore() {
		this.file = FabricLoader.getInstance().getConfigDir().resolve("timerins_addons").resolve("collections.json");
	}

	public void load() {
		goalsByResourceId.clear();
		apiCollectionAmounts.clear();
		lastHypixelPollSnapshot.clear();
		sackStoredAmounts.clear();
		clearCollectionDeltas();
		CollectionDeltaTracker.resetAfterApiSync();
		settings = new CollectionHudSettings();
		if (!Files.isRegularFile(file)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(file)) {
			PersistedRoot root = GSON.fromJson(reader, PersistedRoot.class);
			if (root == null) {
				return;
			}
			if (root.settings != null) {
				settings = root.settings.normalize();
			}
			if (root.goals != null) {
				for (CollectionGoal g : root.goals) {
					if (g == null || g.getResourceId() == null || g.getResourceId().isBlank()) {
						continue;
					}
					String id = g.getResourceId().trim();
					CollectionGoal copy = new CollectionGoal(id, g.getItemKey() == null ? id : g.getItemKey().trim(), g.getTargetAmount());
					copy.setDisplayLabel(g.getDisplayLabel());
					copy.setDisplayStackJson(g.getDisplayStackJson());
					copy.setSackDisplayAliases(g.getSackDisplayAliases());
					goalsByResourceId.put(id, copy);
				}
			}
		} catch (IOException | JsonParseException ignored) {
		}
	}

	public void save() {
		try {
			Files.createDirectories(file.getParent());
			PersistedRoot root = new PersistedRoot();
			root.settings = settings.normalize();
			root.goals = new ArrayList<>(goalsByResourceId.values());
			try (Writer writer = Files.newBufferedWriter(file)) {
				GSON.toJson(root, writer);
			}
		} catch (IOException ignored) {
		}
	}

	public CollectionHudSettings getSettings() {
		return settings;
	}

	public void setSettings(CollectionHudSettings settings) {
		this.settings = Objects.requireNonNull(settings).normalize();
	}

	public List<CollectionGoal> getGoals() {
		return Collections.unmodifiableList(new ArrayList<>(goalsByResourceId.values()));
	}

	public void putOrReplace(CollectionGoal goal) {
		String id = goal.getResourceId().trim();
		CollectionGoal copy = new CollectionGoal(id, goal.getItemKey().isBlank() ? id : goal.getItemKey().trim(), goal.getTargetAmount());
		copy.setDisplayLabel(goal.getDisplayLabel());
		copy.setDisplayStackJson(goal.getDisplayStackJson());
		copy.setSackDisplayAliases(goal.getSackDisplayAliases());
		goalsByResourceId.put(id, copy);
	}

	public void remove(String resourceId) {
		if (resourceId != null) {
			goalsByResourceId.remove(resourceId.trim());
		}
	}

	public void clearGoals() {
		goalsByResourceId.clear();
	}

	/** Clears goals and client-side estimates (pickup deltas, sack GUI snapshots); does not clear cached Hypixel API totals. */
	public void clearGoalsAndLocalProgressEstimates() {
		goalsByResourceId.clear();
		clearCollectionDeltas();
		sackStoredAmounts.clear();
	}

	public Long getApiCollectionAmount(String resourceId) {
		if (resourceId == null) {
			return null;
		}
		String t = resourceId.trim();
		for (String candidate : skyBlockCollectionKeyLookupVariants(t)) {
			Long v = apiCollectionAmounts.get(candidate);
			if (v != null) {
				return v;
			}
		}
		for (String candidate : skyBlockCollectionKeyLookupVariants(t)) {
			for (Map.Entry<String, Long> e : apiCollectionAmounts.entrySet()) {
				if (e.getKey().equalsIgnoreCase(candidate)) {
					return e.getValue();
				}
			}
		}
		return null;
	}

	public String resolveCanonicalCollectionId(String requested) {
		if (requested == null || requested.isBlank()) {
			return null;
		}
		String t = requested.trim();
		for (String candidate : skyBlockCollectionKeyLookupVariants(t)) {
			if (apiCollectionAmounts.containsKey(candidate)) {
				return candidate;
			}
		}
		for (String candidate : skyBlockCollectionKeyLookupVariants(t)) {
			for (String k : apiCollectionAmounts.keySet()) {
				if (k.equalsIgnoreCase(candidate)) {
					return k;
				}
			}
		}
		return null;
	}

	/** True if a goal's {@code resourceId} refers to the same Hypixel collection entry as {@code hypixelKeyFromApi}. */
	public boolean resourceIdMatchesHypixelCollectionKey(String resourceId, String hypixelKeyFromApi) {
		if (resourceId == null || resourceId.isBlank() || hypixelKeyFromApi == null || hypixelKeyFromApi.isBlank()) {
			return false;
		}
		for (String rv : skyBlockCollectionKeyLookupVariants(resourceId.trim())) {
			for (String hv : skyBlockCollectionKeyLookupVariants(hypixelKeyFromApi.trim())) {
				if (ItemKeyMatcher.looseKeysMatch(rv, hv)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Hypixel collection keys sometimes use {@code :} (e.g. fish tiers) while NEU uses {@code RAW_FISH-1}; also
	 * {@code ;} vs {@code :}. Merge all plausible spellings when matching profile API keys.
	 */
	static List<String> skyBlockCollectionKeyLookupVariants(String t) {
		LinkedHashSet<String> set = new LinkedHashSet<>();
		if (t == null || t.isBlank()) {
			return List.of();
		}
		String trim = t.trim();
		set.add(trim);
		for (String v : NeuInternalItemIcons.internalNameVariants(trim)) {
			set.add(v);
		}
		if (trim.indexOf(':') >= 0) {
			set.add(trim.replace(':', ';'));
		}
		if (trim.indexOf(';') >= 0) {
			set.add(trim.replace(';', ':'));
		}
		return new ArrayList<>(set);
	}

	public void setApiCollectionAmounts(Map<String, Long> amounts) {
		if (amounts == null || amounts.isEmpty()) {
			apiCollectionAmounts.clear();
			lastHypixelPollSnapshot.clear();
			clearCollectionDeltas();
			CollectionDeltaTracker.resetAfterApiSync();
			return;
		}
		for (Map.Entry<String, Long> e : amounts.entrySet()) {
			String key = e.getKey();
			Long newVal = e.getValue();
			Long prevPoll = lastHypixelPollSnapshot.get(key);
			boolean sameAsLastPoll = prevPoll != null && newVal != null && prevPoll.longValue() == newVal.longValue();
			if (!sameAsLastPoll) {
				wipeEstimateForHypixelCollectionKey(key);
			}
		}
		apiCollectionAmounts.clear();
		apiCollectionAmounts.putAll(amounts);
		lastHypixelPollSnapshot.clear();
		lastHypixelPollSnapshot.putAll(amounts);
	}

	/**
	 * Hypixel reported a new total for this collection key (or first time we see it): drop local delta and resync
	 * inventory snapshots for matching goals.
	 */
	private void wipeEstimateForHypixelCollectionKey(String hypixelKey) {
		if (hypixelKey == null || hypixelKey.isEmpty()) {
			return;
		}
		for (CollectionGoal g : goalsByResourceId.values()) {
			String rid = g.getResourceId().trim();
			if (rid.isEmpty()) {
				continue;
			}
			if (!resourceIdMatchesHypixelCollectionKey(rid, hypixelKey)) {
				continue;
			}
			collectionDeltaSinceApi.remove(rid);
			for (String dk : new ArrayList<>(collectionDeltaSinceApi.keySet())) {
				if (ItemKeyMatcher.looseKeysMatch(dk, rid)) {
					collectionDeltaSinceApi.remove(dk);
				}
			}
		}
		CollectionDeltaTracker.resetSnapshotsForHypixelCollectionKey(this, hypixelKey);
	}

	public void addCollectionDelta(String resourceId, long amount) {
		if (resourceId == null || amount <= 0L) {
			return;
		}
		String t = resourceId.trim();
		if (t.isEmpty()) {
			return;
		}
		collectionDeltaSinceApi.merge(t, amount, Long::sum);
	}

	public long getCollectionDelta(String resourceId) {
		if (resourceId == null) {
			return 0L;
		}
		String t = resourceId.trim();
		for (String candidate : skyBlockCollectionKeyLookupVariants(t)) {
			Long v = collectionDeltaSinceApi.get(candidate);
			if (v != null) {
				return v;
			}
		}
		for (String candidate : skyBlockCollectionKeyLookupVariants(t)) {
			for (Map.Entry<String, Long> e : collectionDeltaSinceApi.entrySet()) {
				if (e.getKey().equalsIgnoreCase(candidate)) {
					return e.getValue();
				}
			}
		}
		return 0L;
	}

	public void clearCollectionDeltas() {
		collectionDeltaSinceApi.clear();
	}

	/** {@code null} if we have not established a sack total for this key yet (GUI or chat). */
	public Long getSackStoredAmount(String itemOrResourceKey) {
		if (itemOrResourceKey == null || itemOrResourceKey.isBlank()) {
			return null;
		}
		String t = itemOrResourceKey.trim();
		Long v = sackStoredAmounts.get(t);
		if (v != null) {
			return v;
		}
		for (Map.Entry<String, Long> e : sackStoredAmounts.entrySet()) {
			if (e.getKey().equalsIgnoreCase(t)) {
				return e.getValue();
			}
		}
		return null;
	}

	public void putSackStoredAmount(String itemOrResourceKey, long amount) {
		if (itemOrResourceKey == null || itemOrResourceKey.isBlank()) {
			return;
		}
		String t = itemOrResourceKey.trim();
		long a = Math.max(0L, amount);
		for (String k : new ArrayList<>(sackStoredAmounts.keySet())) {
			if (k.equalsIgnoreCase(t)) {
				sackStoredAmounts.put(k, a);
				return;
			}
		}
		sackStoredAmounts.put(t, a);
	}

	/**
	 * Full replace of known amounts from an open sack screen (keys are SkyBlock item ids from row stacks).
	 */
	public void applySackGuiSnapshot(Map<String, Long> snapshot) {
		if (snapshot == null || snapshot.isEmpty()) {
			return;
		}
		for (Map.Entry<String, Long> e : snapshot.entrySet()) {
			if (e.getKey() != null && !e.getKey().isBlank()) {
				putSackStoredAmount(e.getKey(), e.getValue());
			}
		}
	}

	private static final class PersistedRoot {
		CollectionHudSettings settings;
		List<CollectionGoal> goals;
	}
}
