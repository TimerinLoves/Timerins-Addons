package dev.timerin.timerinsaddons.skyblock;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * Maps normalized item display text (as in NotEnoughUpdates) to SkyBlock {@code ExtraAttributes.id}-style strings.
 * <p>
 * Data is generated from the public
 * <a href="https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO">NotEnoughUpdates-REPO</a> {@code items/*.json}
 * files ({@code displayname} + {@code internalname}) via {@code scripts/build_neu_display_index.py}. Same idea as
 * SkyHanni/NEU {@code NeuInternalName.fromItemName}.
 */
public final class NeuDisplayNameIndex {
	private static final Map<String, String> NORMALIZED_DISPLAY_TO_INTERNAL;

	static {
		Map<String, String> m = load();
		NORMALIZED_DISPLAY_TO_INTERNAL = m;
	}

	private NeuDisplayNameIndex() {
	}

	private static Map<String, String> load() {
		try (InputStream in = openBundledJson()) {
			if (in == null) {
				return Map.of();
			}
			Gson gson = new Gson();
			Type type = new TypeToken<Map<String, String>>() {
			}.getType();
			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
				Map<String, String> raw = gson.fromJson(reader, type);
				if (raw == null || raw.isEmpty()) {
					return Map.of();
				}
				return Collections.unmodifiableMap(new HashMap<>(raw));
			}
		} catch (IOException e) {
			return Map.of();
		}
	}

	private static InputStream openBundledJson() {
		InputStream in = NeuDisplayNameIndex.class.getClassLoader()
				.getResourceAsStream("assets/timerins_addons/neu_display_to_internal.json");
		if (in != null) {
			return in;
		}
		return NeuDisplayNameIndex.class.getResourceAsStream("/assets/timerins_addons/neu_display_to_internal.json");
	}

	/**
	 * @param displayWithOrWithoutSectionCodes visible item name from sack chat (one line)
	 */
	public static Optional<String> resolveInternalIdFromDisplayName(String displayWithOrWithoutSectionCodes) {
		String key = normalizeDisplayLookupKey(displayWithOrWithoutSectionCodes);
		if (key.isEmpty()) {
			return Optional.empty();
		}
		String id = NORMALIZED_DISPLAY_TO_INTERNAL.get(key);
		if (id != null && !id.isEmpty()) {
			return Optional.of(id);
		}
		return Optional.empty();
	}

	/** Aligns with {@code scripts/build_neu_display_index.py} ({@code normalize_key}). */
	static String normalizeDisplayLookupKey(String s) {
		if (s == null || s.isEmpty()) {
			return "";
		}
		String t = stripSectionCodes(s).trim();
		if (t.isEmpty()) {
			return "";
		}
		String collapsed = String.join(" ", t.split("\\s+"));
		return collapsed.toLowerCase(Locale.ROOT);
	}

	private static String stripSectionCodes(String s) {
		return s.replaceAll("§.", "");
	}
}
