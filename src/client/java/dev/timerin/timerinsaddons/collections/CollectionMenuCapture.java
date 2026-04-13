package dev.timerin.timerinsaddons.collections;

import java.util.Locale;
import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import dev.timerin.timerinsaddons.config.ModConfig;
import dev.timerin.timerinsaddons.hypixel.HypixelProfileService;
import dev.timerin.timerinsaddons.skyblock.SkyBlockItemIds;
import dev.timerin.timerinsaddons.tracker.TrackerStore;
import dev.timerin.timerinsaddons.util.ContainerScreenHover;
import dev.timerin.timerinsaddons.util.ItemStackCodecUtil;

/**
 * Saves a collection goal from the SkyBlock collections menu (hovered slot + lore).
 */
public final class CollectionMenuCapture {
	private CollectionMenuCapture() {
	}

	public static boolean tryCaptureHovered(Minecraft client, TrackerStore trackerStore, CollectionStore collectionStore) {
		if (client.player == null || !(client.screen instanceof AbstractContainerScreen<?> acs)) {
			return false;
		}
		if (!screenTitleLooksLikeCollections(acs.getTitle())) {
			return false;
		}
		Slot slot = ContainerScreenHover.resolveHoveredSlot(client, acs);
		if (slot == null || slot.getItem().isEmpty()) {
			return false;
		}
		ItemStack stack = slot.getItem();
		Optional<String> keyOpt = SkyBlockItemIds.resolveKey(stack);
		if (keyOpt.isEmpty()) {
			return false;
		}
		String itemKey = keyOpt.get();
		ModConfig cfg = trackerStore.getConfig();
		int target = cfg.getDefaultTargetAmount();
		Optional<CollectionLoreParser.Parsed> parsed = CollectionLoreParser.parse(stack, client.player);
		if (parsed.isPresent()) {
			CollectionLoreParser.Parsed p = parsed.get();
			if (p.target() > 0) {
				target = p.target();
			} else if (p.current() > 0) {
				target = Math.max(p.current(), cfg.getDefaultTargetAmount());
			}
		}
		String resourceId = itemKey;
		CollectionGoal goal = new CollectionGoal(resourceId, itemKey, target);
		goal.setDisplayLabel("");
		ItemStackCodecUtil.encode(stack).ifPresent(goal::setDisplayStackJson);
		collectionStore.putOrReplace(goal);
		collectionStore.save();
		if (!collectionStore.getSettings().getHypixelApiKey().isEmpty()) {
			HypixelProfileService.forceAllowNextFetch();
			HypixelProfileService.refreshCollectionsAsync(client, collectionStore);
		}
		return true;
	}

	private static boolean screenTitleLooksLikeCollections(Component title) {
		if (title == null) {
			return false;
		}
		String s = title.getString().toLowerCase(Locale.ROOT);
		return s.contains("collection") || s.contains("coll");
	}
}
