package dev.timerin.timerinsaddons.tracker;

import java.util.Objects;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

import dev.timerin.timerinsaddons.skyblock.ItemKeyMatcher;
import dev.timerin.timerinsaddons.skyblock.SkyBlockItemIds;
import dev.timerin.timerinsaddons.util.ItemStackCodecUtil;

public final class TrackerEntry {
	private String itemKey;
	private int targetAmount;
	/** JSON from {@link ItemStack#CODEC}; used for the row icon when you have zero in inventory. */
	private String displayStackJson;

	public TrackerEntry() {
		this("", 1);
	}

	public TrackerEntry(String itemKey, int targetAmount) {
		this.itemKey = Objects.requireNonNull(itemKey);
		this.targetAmount = Math.max(0, targetAmount);
	}

	public String getItemKey() {
		return itemKey;
	}

	public void setItemKey(String itemKey) {
		this.itemKey = Objects.requireNonNull(itemKey);
	}

	public int getTargetAmount() {
		return targetAmount;
	}

	public void setTargetAmount(int targetAmount) {
		this.targetAmount = Math.max(0, targetAmount);
	}

	public String getDisplayStackJson() {
		return displayStackJson;
	}

	public void setDisplayStackJson(String displayStackJson) {
		this.displayStackJson = displayStackJson;
	}

	/**
	 * Whether a {@code [Sacks]} chat line applies to this tracked item (display name vs SkyBlock id).
	 */
	public boolean matchesSackItemLine(String displayNorm, String guessedKey, String itemDisplayRaw) {
		String k = itemKey.trim();
		if (k.isEmpty()) {
			return false;
		}
		if (ItemKeyMatcher.looseKeysMatch(guessedKey, k)) {
			return true;
		}
		if (ItemKeyMatcher.looseKeysMatch(displayNorm, k)) {
			return true;
		}
		if (itemDisplayRaw != null && !itemDisplayRaw.isEmpty()) {
			String plain = itemDisplayRaw.replaceAll("§.", "").trim();
			if (!plain.isEmpty() && ItemKeyMatcher.looseKeysMatch(plain, k)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Icon for this row: persisted stack, else first matching stack in the open menu, else fallback.
	 */
	public ItemStack resolveDisplayIcon(LocalPlayer player) {
		if (displayStackJson != null && !displayStackJson.isBlank()) {
			ItemStack decoded = ItemStackCodecUtil.decode(displayStackJson);
			if (!decoded.isEmpty()) {
				return decoded;
			}
		}
		ItemStack rep = InventoryCounter.findRepresentativeStack(player, itemKey);
		if (!rep.isEmpty()) {
			return rep;
		}
		return SkyBlockItemIds.createDisplayStack(itemKey, ItemStack.EMPTY);
	}
}
