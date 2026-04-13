package dev.timerin.timerinsaddons.collections;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

import dev.timerin.timerinsaddons.skyblock.ItemKeyMatcher;
import dev.timerin.timerinsaddons.skyblock.SkyBlockItemIds;
import dev.timerin.timerinsaddons.tracker.InventoryCounter;
import dev.timerin.timerinsaddons.util.ItemStackCodecUtil;

/**
 * A SkyBlock collection resource goal. {@link #resourceId} labels the row (often a Hypixel-style resource name).
 * {@link #itemKey} is used for inventory counting (often the same as SkyBlock item id for that resource).
 * <p>
 * Sack chat and some UIs show a <em>different</em> display name than the SkyBlock id (e.g. {@code Raw Cod} vs
 * {@code RAW_FISH}); use {@link #sackDisplayAliases} so {@code [Sacks]} lines can match this goal.
 */
public final class CollectionGoal {
	private String resourceId = "";
	private String itemKey = "";
	private int targetAmount;
	private String displayLabel = "";
	private String displayStackJson = "";
	/** Extra names as shown in sack chat / GUIs (persisted as a JSON array). */
	private ArrayList<String> sackDisplayAliases = new ArrayList<>();

	public CollectionGoal() {
	}

	public CollectionGoal(String resourceId, String itemKey, int targetAmount) {
		this.resourceId = Objects.requireNonNull(resourceId);
		this.itemKey = Objects.requireNonNull(itemKey);
		this.targetAmount = Math.max(0, targetAmount);
	}

	public String getResourceId() {
		return resourceId;
	}

	public void setResourceId(String resourceId) {
		this.resourceId = resourceId == null ? "" : resourceId;
	}

	public String getItemKey() {
		return itemKey;
	}

	public void setItemKey(String itemKey) {
		this.itemKey = itemKey == null ? "" : itemKey;
	}

	public int getTargetAmount() {
		return targetAmount;
	}

	public void setTargetAmount(int targetAmount) {
		this.targetAmount = Math.max(0, targetAmount);
	}

	public String getDisplayLabel() {
		return displayLabel;
	}

	public void setDisplayLabel(String displayLabel) {
		this.displayLabel = displayLabel == null ? "" : displayLabel;
	}

	public String getDisplayStackJson() {
		return displayStackJson;
	}

	public void setDisplayStackJson(String displayStackJson) {
		this.displayStackJson = displayStackJson == null ? "" : displayStackJson;
	}

	public List<String> getSackDisplayAliases() {
		if (sackDisplayAliases == null) {
			sackDisplayAliases = new ArrayList<>();
		}
		return Collections.unmodifiableList(sackDisplayAliases);
	}

	public void setSackDisplayAliases(List<String> aliases) {
		sackDisplayAliases = new ArrayList<>();
		if (aliases == null) {
			return;
		}
		for (String s : aliases) {
			if (s != null && !s.isBlank()) {
				sackDisplayAliases.add(s.trim());
			}
		}
	}

	/**
	 * Whether a sack chat line naming {@code itemDisplay} / {@code guessedKeyFromDisplay} should apply to this goal.
	 */
	public boolean matchesSackChatName(String displayNorm, String guessedKeyFromDisplay, String itemDisplayRaw) {
		String itemKey = this.itemKey.isBlank() ? resourceId : this.itemKey.trim();
		String rid = resourceId.trim();
		if (rid.isEmpty()) {
			return false;
		}
		String label = stripCodes(displayLabel).trim();
		boolean displayLabelMatch = !label.isEmpty() && displayNorm.equalsIgnoreCase(label);
		if (displayLabelMatch) {
			return true;
		}
		List<String> aliases = sackDisplayAliases != null ? sackDisplayAliases : Collections.emptyList();
		for (String alias : aliases) {
			if (aliasMatchesSackAlias(displayNorm, guessedKeyFromDisplay, alias)) {
				return true;
			}
		}
		return ItemKeyMatcher.looseKeysMatch(guessedKeyFromDisplay, itemKey)
				|| ItemKeyMatcher.looseKeysMatch(itemDisplayRaw, itemKey)
				|| ItemKeyMatcher.looseKeysMatch(guessedKeyFromDisplay, rid)
				|| ItemKeyMatcher.looseKeysMatch(itemDisplayRaw, rid)
				|| ItemKeyMatcher.looseKeysMatch(guessedKeyFromDisplay, label)
				|| ItemKeyMatcher.looseKeysMatch(displayNorm, itemKey);
	}

	private static boolean aliasMatchesSackAlias(String displayNorm, String guessedKey, String aliasRaw) {
		String a = stripCodes(aliasRaw).trim();
		if (a.isEmpty()) {
			return false;
		}
		if (displayNorm.equalsIgnoreCase(a)) {
			return true;
		}
		if (ItemKeyMatcher.looseKeysMatch(guessedKey, a) || ItemKeyMatcher.looseKeysMatch(displayNorm, a)) {
			return true;
		}
		String guessedFromAlias = a.toUpperCase(Locale.ROOT).replace(' ', '_');
		return guessedKey.equalsIgnoreCase(guessedFromAlias);
	}

	private static String stripCodes(String s) {
		if (s == null || s.isEmpty()) {
			return "";
		}
		return s.replaceAll("§.", "");
	}

	/**
	 * With a Hypixel Public API key: last profile fetch for {@link #resourceId} plus a client-side delta from visible
	 * inventory changes (and sack NBT when Hypixel exposes counts on sack items). The delta resets whenever the profile
	 * API syncs again. Without a key: open-inventory count for {@link #itemKey} (not sacks).
	 */
	public int resolveCurrent(LocalPlayer player, CollectionStore store) {
		if (!store.getSettings().getHypixelApiKey().isEmpty()) {
			Long api = store.getApiCollectionAmount(resourceId);
			long base = api != null ? api : 0L;
			long delta = store.getCollectionDelta(resourceId);
			long sum = base + delta;
			return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, sum));
		}
		String key = itemKey.isBlank() ? resourceId : itemKey;
		return InventoryCounter.countMatching(player, key);
	}

	public ItemStack resolveDisplayIcon(LocalPlayer player) {
		if (displayStackJson != null && !displayStackJson.isBlank()) {
			ItemStack decoded = ItemStackCodecUtil.decode(displayStackJson);
			if (!decoded.isEmpty()) {
				return decoded;
			}
		}
		String key = itemKey.isBlank() ? resourceId : itemKey;
		ItemStack rep = InventoryCounter.findRepresentativeStack(player, key);
		if (!rep.isEmpty()) {
			return rep;
		}
		return SkyBlockItemIds.createDisplayStack(key, ItemStack.EMPTY);
	}
}
