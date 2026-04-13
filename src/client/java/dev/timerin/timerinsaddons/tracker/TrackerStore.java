package dev.timerin.timerinsaddons.tracker;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import dev.timerin.timerinsaddons.config.ModConfig;
import dev.timerin.timerinsaddons.skyblock.ItemKeyMatcher;
import net.fabricmc.loader.api.FabricLoader;

public final class TrackerStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path configFile;
	private ModConfig config = ModConfig.createDefault();
	private final Map<String, TrackerEntry> entriesByKey = new LinkedHashMap<>();
	/**
	 * Last known amount in sacks for each tracked {@link TrackerEntry#getItemKey()} (from {@code [Sacks]} chat deltas
	 * and open sack GUI lore). Persisted so totals survive relog.
	 */
	private final Map<String, Long> trackerSackAmounts = new LinkedHashMap<>();

	public TrackerStore() {
		this.configFile = FabricLoader.getInstance().getConfigDir().resolve("timerins_addons").resolve("item_tracker.json");
	}

	public void load() {
		entriesByKey.clear();
		trackerSackAmounts.clear();
		config = ModConfig.createDefault();
		if (!Files.isRegularFile(configFile)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(configFile)) {
			PersistedRoot root = GSON.fromJson(reader, PersistedRoot.class);
			if (root == null) {
				return;
			}
			if (root.config != null) {
				config = root.config.normalize();
			}
			if (root.entries != null) {
				for (TrackerEntry e : root.entries) {
					if (e == null || e.getItemKey() == null || e.getItemKey().isBlank()) {
						continue;
					}
					TrackerEntry copy = new TrackerEntry(e.getItemKey().trim(), e.getTargetAmount());
					copy.setDisplayStackJson(e.getDisplayStackJson());
					entriesByKey.put(copy.getItemKey(), copy);
				}
			}
			if (root.trackerSackAmounts != null) {
				for (Map.Entry<String, Long> e : root.trackerSackAmounts.entrySet()) {
					if (e.getKey() == null || e.getKey().isBlank() || e.getValue() == null) {
						continue;
					}
					trackerSackAmounts.put(e.getKey().trim(), Math.max(0L, e.getValue()));
				}
			}
		} catch (IOException | JsonParseException e) {
			// Keep defaults; avoid crashing client
		}
	}

	public void save() {
		try {
			Files.createDirectories(configFile.getParent());
			PersistedRoot root = new PersistedRoot();
			root.config = config;
			root.entries = new ArrayList<>(entriesByKey.values());
			root.trackerSackAmounts = new HashMap<>(trackerSackAmounts);
			try (Writer writer = Files.newBufferedWriter(configFile)) {
				GSON.toJson(root, writer);
			}
		} catch (IOException ignored) {
		}
	}

	public ModConfig getConfig() {
		return config;
	}

	public void setConfig(ModConfig config) {
		this.config = Objects.requireNonNull(config).normalize();
	}

	public List<TrackerEntry> getEntries() {
		return Collections.unmodifiableList(new ArrayList<>(entriesByKey.values()));
	}

	public void putOrReplace(TrackerEntry entry) {
		TrackerEntry copy = new TrackerEntry(entry.getItemKey(), entry.getTargetAmount());
		copy.setDisplayStackJson(entry.getDisplayStackJson());
		entriesByKey.put(copy.getItemKey(), copy);
	}

	public void remove(String itemKey) {
		entriesByKey.remove(itemKey);
		if (itemKey != null) {
			String t = itemKey.trim();
			trackerSackAmounts.remove(t);
			for (String k : new ArrayList<>(trackerSackAmounts.keySet())) {
				if (k.equalsIgnoreCase(t)) {
					trackerSackAmounts.remove(k);
					break;
				}
			}
		}
	}

	public void clear() {
		entriesByKey.clear();
		trackerSackAmounts.clear();
	}

	/** {@code null} if no sack total has been established yet (GUI or chat). */
	public Long getSackStoredAmount(String itemKey) {
		if (itemKey == null || itemKey.isBlank()) {
			return null;
		}
		String t = itemKey.trim();
		Long v = trackerSackAmounts.get(t);
		if (v != null) {
			return v;
		}
		for (Map.Entry<String, Long> e : trackerSackAmounts.entrySet()) {
			if (e.getKey().equalsIgnoreCase(t)) {
				return e.getValue();
			}
		}
		return null;
	}

	public void putSackStoredAmount(String itemKey, long amount) {
		if (itemKey == null || itemKey.isBlank()) {
			return;
		}
		String t = itemKey.trim();
		long a = Math.max(0L, amount);
		for (String k : new ArrayList<>(trackerSackAmounts.keySet())) {
			if (k.equalsIgnoreCase(t)) {
				trackerSackAmounts.put(k, a);
				return;
			}
		}
		trackerSackAmounts.put(t, a);
	}

	/**
	 * Updates sack amounts for tracked keys from an open sack screen (keys from row item ids).
	 */
	public void applySackGuiSnapshot(Map<String, Long> snapshot) {
		if (snapshot == null || snapshot.isEmpty() || entriesByKey.isEmpty()) {
			return;
		}
		for (Map.Entry<String, Long> e : snapshot.entrySet()) {
			if (e.getKey() == null || e.getKey().isBlank()) {
				continue;
			}
			String snapKey = e.getKey().trim();
			for (String tracked : entriesByKey.keySet()) {
				if (ItemKeyMatcher.looseKeysMatch(snapKey, tracked)) {
					putSackStoredAmount(tracked, e.getValue());
					break;
				}
			}
		}
	}

	public void addOrUpdateTarget(String itemKey, int target) {
		TrackerEntry existing = entriesByKey.get(itemKey);
		if (existing != null) {
			existing.setTargetAmount(target);
		} else {
			putOrReplace(new TrackerEntry(itemKey, target));
		}
	}

	private static final class PersistedRoot {
		ModConfig config;
		List<TrackerEntry> entries;
		Map<String, Long> trackerSackAmounts;
	}
}
