package dev.timerin.timerinsaddons.skyblock;

import java.util.Locale;
import java.util.Optional;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Heuristics for whether an item can use SkyBlock material sacks and whether the player is carrying any sack item.
 */
public final class SkyBlockSackRules {
	private SkyBlockSackRules() {
	}

	/**
	 * True if the player has at least one SkyBlock sack item in the open container (inventory / current GUI), used to
	 * hint when sack-based tracking may not apply yet.
	 */
	public static boolean playerHasAnySackItem(Player player) {
		if (player == null) {
			return false;
		}
		for (var slot : player.containerMenu.slots) {
			if (stackLooksLikeSack(slot.getItem())) {
				return true;
			}
		}
		return stackLooksLikeSack(player.containerMenu.getCarried());
	}

	private static boolean stackLooksLikeSack(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		Optional<String> id = SkyBlockItemIds.getSkyBlockId(stack);
		if (id.isEmpty()) {
			return false;
		}
		String u = id.get().toUpperCase(Locale.ROOT);
		return u.contains("SACK") && !u.contains("SACK_OF_SACKS");
	}

	/**
	 * Rough guess: boss drops, armor, and similar are not stored in resource sacks (still may use other storage).
	 */
	public static boolean itemLikelyUsesResourceSack(String itemKey) {
		if (itemKey == null || itemKey.isBlank()) {
			return true;
		}
		String u = itemKey.toUpperCase(Locale.ROOT);
		if (u.contains("HELMET") || u.contains("CHESTPLATE") || u.contains("LEGGINGS") || u.contains("BOOTS")) {
			return false;
		}
		if (u.contains("SWORD") || u.contains("BOW") || u.contains("WAND") || u.contains("STAFF")) {
			return false;
		}
		return true;
	}
}
