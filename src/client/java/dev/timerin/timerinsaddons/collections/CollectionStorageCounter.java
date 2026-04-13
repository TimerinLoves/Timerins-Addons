package dev.timerin.timerinsaddons.collections;

import java.util.Locale;
import java.util.Optional;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import dev.timerin.timerinsaddons.skyblock.ItemKeyMatcher;
import dev.timerin.timerinsaddons.skyblock.SkyBlockItemIds;
import dev.timerin.timerinsaddons.tracker.InventoryCounter;

/**
 * Estimated total held for pickup tracking: inventory stacks matching the key, plus either {@link CollectionStore}'s
 * last {@code Stored:} lore snapshot from an open sack GUI, or else NBT on sack items when lore is unavailable.
 */
public final class CollectionStorageCounter {
	private static final int MAX_NBT_DEPTH = 5;
	private static final String EXTRA_ATTRIBUTES = "ExtraAttributes";

	private CollectionStorageCounter() {
	}

	/**
	 * Visible stacks matching {@code itemKey} in the current {@link Player#containerMenu}, plus last known sack totals
	 * from {@link CollectionStore} when available, otherwise sack-like NBT counts on sack items.
	 */
	public static long countVisibleStorage(Player player, String itemKey, CollectionStore store) {
		if (player == null || itemKey == null || itemKey.isBlank()) {
			return 0L;
		}
		String key = itemKey.trim();
		long inv = InventoryCounter.countMatching(player, key);
		// Do not add open-sack GUI "Stored:" totals here — that double-counts collection progress vs API totals.
		// [Sacks] chat still credits deltas via {@link CollectionStore#addCollectionDelta}; pickup deltas use inv + NBT.
		long nbt = 0L;
		for (var slot : player.containerMenu.slots) {
			ItemStack st = slot.getItem();
			if (!st.isEmpty()) {
				nbt += scanStackForEmbeddedMaterialCounts(st, key);
			}
		}
		ItemStack carried = player.containerMenu.getCarried();
		if (!carried.isEmpty()) {
			nbt += scanStackForEmbeddedMaterialCounts(carried, key);
		}
		return inv + nbt;
	}

	private static long scanStackForEmbeddedMaterialCounts(ItemStack stack, String itemKey) {
		Optional<String> sbId = SkyBlockItemIds.getSkyBlockId(stack);
		if (sbId.isEmpty() || !looksLikeSkyBlockSack(sbId.get())) {
			return 0L;
		}
		Optional<CompoundTag> extra = readExtraAttributes(stack);
		if (extra.isEmpty()) {
			return 0L;
		}
		return scanCompoundForNumericMaterialMatches(extra.get(), itemKey, 0);
	}

	private static boolean looksLikeSkyBlockSack(String skyBlockId) {
		String u = skyBlockId.toUpperCase(Locale.ROOT);
		return u.contains("SACK");
	}

	private static Optional<CompoundTag> readExtraAttributes(ItemStack stack) {
		if (stack.isEmpty()) {
			return Optional.empty();
		}
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null) {
			return Optional.empty();
		}
		CompoundTag root = customData.copyTag();
		return root.getCompound(EXTRA_ATTRIBUTES);
	}

	private static long scanCompoundForNumericMaterialMatches(CompoundTag compound, String itemKey, int depth) {
		if (depth > MAX_NBT_DEPTH) {
			return 0L;
		}
		long sum = 0L;
		for (String field : compound.keySet()) {
			Tag tag = compound.get(field);
			if (tag == null) {
				continue;
			}
			if (tag instanceof CompoundTag nested) {
				sum += scanCompoundForNumericMaterialMatches(nested, itemKey, depth + 1);
			} else if (tag instanceof NumericTag num && numericKeyMatchesTrackedMaterial(field, itemKey)) {
				sum += (long) num.doubleValue();
			}
		}
		return sum;
	}

	/**
	 * True when {@code nbtKey} looks like a SkyBlock material id matching the tracked item key (e.g. COBBLESTONE).
	 */
	private static boolean numericKeyMatchesTrackedMaterial(String nbtKey, String itemKey) {
		if (shouldIgnoreNumericExtraKey(nbtKey)) {
			return false;
		}
		if (ItemKeyMatcher.looseKeysMatch(nbtKey, itemKey)) {
			return true;
		}
		String path = ItemKeyMatcher.pathOnly(itemKey);
		if (nbtKey.equalsIgnoreCase(path)) {
			return true;
		}
		String nk = nbtKey.toUpperCase().replace(' ', '_');
		String pk = path.toUpperCase().replace(' ', '_');
		return nk.equals(pk);
	}

	private static boolean shouldIgnoreNumericExtraKey(String key) {
		return switch (key) {
			case "id", "uuid", "hideRightClick", "originTag", "timestamp", "donated_museum_slots",
					"item_durability", "dungeon_item_level", "hot_potato_count", "art_of_war_count",
					"power_ability_scroll", "ability_scroll" -> true;
			default -> false;
		};
	}
}
