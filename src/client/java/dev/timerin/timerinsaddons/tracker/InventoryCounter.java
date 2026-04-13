package dev.timerin.timerinsaddons.tracker;

import java.util.Optional;

import dev.timerin.timerinsaddons.skyblock.ItemKeyMatcher;
import dev.timerin.timerinsaddons.skyblock.SkyBlockItemIds;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class InventoryCounter {
	private InventoryCounter() {
	}

	public static int countMatching(Player player, String itemKey) {
		int total = 0;
		AbstractContainerMenu menu = player.containerMenu;
		for (Slot slot : menu.slots) {
			ItemStack stack = slot.getItem();
			if (stackKeyMatches(stack, itemKey)) {
				total += stack.getCount();
			}
		}
		ItemStack carried = menu.getCarried();
		if (!carried.isEmpty() && stackKeyMatches(carried, itemKey)) {
			total += carried.getCount();
		}
		return total;
	}

	public static ItemStack findRepresentativeStack(Player player, String itemKey) {
		AbstractContainerMenu menu = player.containerMenu;
		for (Slot slot : menu.slots) {
			ItemStack stack = slot.getItem();
			if (!stack.isEmpty() && stackKeyMatches(stack, itemKey)) {
				return stack;
			}
		}
		ItemStack carried = menu.getCarried();
		if (!carried.isEmpty() && stackKeyMatches(carried, itemKey)) {
			return carried;
		}
		return ItemStack.EMPTY;
	}

	private static boolean stackKeyMatches(ItemStack stack, String itemKey) {
		Optional<String> resolved = SkyBlockItemIds.resolveKey(stack);
		if (resolved.filter(k -> ItemKeyMatcher.looseKeysMatch(k, itemKey)).isPresent()) {
			return true;
		}
		return SkyBlockItemIds.getSkyBlockId(stack).filter(id -> ItemKeyMatcher.looseKeysMatch(id, itemKey)).isPresent();
	}
}
