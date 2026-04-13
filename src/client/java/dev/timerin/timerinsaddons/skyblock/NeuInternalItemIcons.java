package dev.timerin.timerinsaddons.skyblock;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * NEU REPO {@code items/*.json} {@code internalname} → {@code itemid} (vanilla registry id) for HUD icons when the
 * player has no representative SkyBlock stack.
 */
public final class NeuInternalItemIcons {
	private static final Map<String, String> INTERNAL_TO_ITEMID;

	static {
		Map<String, String> m = load();
		INTERNAL_TO_ITEMID = m;
	}

	private NeuInternalItemIcons() {
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
				return Collections.unmodifiableMap(raw);
			}
		} catch (IOException e) {
			return Map.of();
		}
	}

	private static InputStream openBundledJson() {
		InputStream in = NeuInternalItemIcons.class.getClassLoader()
				.getResourceAsStream("assets/timerins_addons/neu_internal_to_itemid.json");
		if (in != null) {
			return in;
		}
		return NeuInternalItemIcons.class.getResourceAsStream("/assets/timerins_addons/neu_internal_to_itemid.json");
	}

	/**
	 * Best-effort icon for a SkyBlock-style key (NEU internal id, or Hypixel {@code raw_fish:1} / {@code RAW_FISH-1}).
	 */
	public static Optional<ItemStack> tryStackForKey(String itemKey) {
		if (itemKey == null || itemKey.isBlank()) {
			return Optional.empty();
		}
		for (String variant : internalNameVariants(itemKey.trim())) {
			Optional<ItemStack> direct = stackForNeuInternal(variant);
			if (direct.isPresent()) {
				return direct;
			}
		}
		return Optional.empty();
	}

	private static Optional<ItemStack> stackForNeuInternal(String neuInternal) {
		String itemid = INTERNAL_TO_ITEMID.get(neuInternal);
		if (itemid == null) {
			for (Map.Entry<String, String> e : INTERNAL_TO_ITEMID.entrySet()) {
				if (e.getKey().equalsIgnoreCase(neuInternal)) {
					itemid = e.getValue();
					break;
				}
			}
		}
		if (itemid == null || itemid.isEmpty()) {
			return Optional.empty();
		}
		return resolveStackWithLegacyFishSplit(neuInternal, itemid);
	}

	/**
	 * NEU still lists {@code minecraft:fish} for many SkyBlock fish items; modern Minecraft uses cod/salmon/etc.
	 */
	private static Optional<ItemStack> resolveStackWithLegacyFishSplit(String internalName, String itemid) {
		Optional<ItemStack> direct = parseVanillaItemId(itemid);
		if (direct.isPresent()) {
			return direct;
		}
		if (!"minecraft:fish".equals(itemid)) {
			return Optional.empty();
		}
		int tier = rawFishTierSuffix(internalName);
		Item replacement = switch (tier) {
			case 1 -> Items.SALMON;
			case 2 -> Items.TROPICAL_FISH;
			case 3 -> Items.PUFFERFISH;
			default -> Items.COD;
		};
		return Optional.of(new ItemStack(replacement));
	}

	private static int rawFishTierSuffix(String internalName) {
		String u = internalName.toUpperCase(Locale.ROOT);
		if (!u.startsWith("RAW_FISH")) {
			return 0;
		}
		int dash = u.lastIndexOf('-');
		if (dash < 0 || dash >= u.length() - 1) {
			return 0;
		}
		String tail = u.substring(dash + 1);
		try {
			return Integer.parseInt(tail);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static Optional<ItemStack> parseVanillaItemId(String itemid) {
		try {
			Identifier id = Identifier.parse(itemid);
			return BuiltInRegistries.ITEM.getOptional(id)
					.filter(i -> i != Items.AIR)
					.map(ItemStack::new);
		} catch (Exception e) {
			return Optional.empty();
		}
	}

	/**
	 * Hypixel uses {@code RESOURCE:variant}; NEU uses {@code RESOURCE-VARIANT} for the same tier (e.g. fish).
	 */
	public static List<String> internalNameVariants(String key) {
		LinkedHashSet<String> set = new LinkedHashSet<>();
		String t = key.trim();
		if (t.isEmpty()) {
			return List.of();
		}
		set.add(t);
		set.add(t.toUpperCase(Locale.ROOT));
		set.add(t.toLowerCase(Locale.ROOT));
		// raw_fish:1 ↔ RAW_FISH-1
		int colon = t.lastIndexOf(':');
		if (colon > 0 && colon < t.length() - 1) {
			String base = t.substring(0, colon);
			String num = t.substring(colon + 1);
			if (num.chars().allMatch(Character::isDigit)) {
				set.add(base + "-" + num);
				set.add(base.toUpperCase(Locale.ROOT) + "-" + num);
			}
		}
		int semi = t.lastIndexOf(';');
		if (semi > 0 && semi < t.length() - 1) {
			String base = t.substring(0, semi);
			String num = t.substring(semi + 1);
			if (num.chars().allMatch(Character::isDigit)) {
				set.add(base + "-" + num);
				set.add(base + ":" + num);
			}
		}
		int dash = t.lastIndexOf('-');
		if (dash > 0 && dash < t.length() - 1) {
			String tail = t.substring(dash + 1);
			if (tail.chars().allMatch(Character::isDigit)) {
				String base = t.substring(0, dash);
				set.add(base + ":" + tail);
				set.add(base + ";" + tail);
			}
		}
		return new ArrayList<>(set);
	}
}
