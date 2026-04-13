package dev.timerin.timerinsaddons.collections;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

/**
 * Tracks increases in {@link CollectionStorageCounter} totals between Hypixel API updates that actually change a
 * resource's reported total. Stale API repeats preserve {@link CollectionStore}'s local delta.
 */
public final class CollectionDeltaTracker {
	private static final Map<String, Long> lastObservedTotals = new ConcurrentHashMap<>();
	/**
	 * After a [Sacks] line credits {@link CollectionStore#addCollectionDelta}, the next inventory tick can still see a
	 * jump in {@link CollectionStorageCounter} (e.g. 0→1 as sack storage syncs) and would double-count. Skip one
	 * pickup delta for that goal and only refresh the snapshot.
	 */
	private static final Set<String> skipNextPickupDeltaForResource = ConcurrentHashMap.newKeySet();

	private CollectionDeltaTracker() {
	}

	public static void skipNextPickupDeltaBecauseSackChat(String resourceId) {
		if (resourceId != null && !resourceId.isBlank()) {
			skipNextPickupDeltaForResource.add(resourceId.trim().toLowerCase(Locale.ROOT));
		}
	}

	/** Clears all snapshots (e.g. empty API response or full reset). */
	public static void resetAfterApiSync() {
		lastObservedTotals.clear();
	}

	/**
	 * After {@link SackChatContribution} applies a [Sacks] delta, refresh the pickup baseline so the next tick does not
	 * treat inv+sack total changes as extra pickups on top of the chat credit.
	 */
	public static void resyncSnapshotAfterSackChat(CollectionStore store, String resourceId, String itemKey) {
		if (resourceId == null || resourceId.isBlank()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		if (player == null) {
			return;
		}
		String rid = resourceId.trim();
		String ikey = itemKey == null || itemKey.isBlank() ? rid : itemKey.trim();
		long t = CollectionStorageCounter.countVisibleStorage(player, ikey, store);
		lastObservedTotals.put(rid, t);
	}

	/** When Hypixel reports a new total for {@code hypixelKey}, resync pickup observation for matching goals. */
	public static void resetSnapshotsForHypixelCollectionKey(CollectionStore store, String hypixelKey) {
		if (hypixelKey == null || hypixelKey.isEmpty()) {
			return;
		}
		for (CollectionGoal g : store.getGoals()) {
			String rid = g.getResourceId().trim();
			if (!rid.isEmpty() && store.resourceIdMatchesHypixelCollectionKey(rid, hypixelKey)) {
				lastObservedTotals.remove(rid);
			}
		}
	}

	public static void tick(Minecraft client, CollectionStore store) {
		if (client.player == null) {
			return;
		}
		if (store.getGoals().isEmpty()) {
			return;
		}
		if (store.getSettings().getHypixelApiKey().isEmpty()) {
			return;
		}
		boolean allowDelta = allowInventoryDeltaForScreen(client.screen);
		for (CollectionGoal goal : store.getGoals()) {
			String rid = goal.getResourceId().trim();
			if (rid.isEmpty()) {
				continue;
			}
			String ikey = goal.getItemKey().isBlank() ? rid : goal.getItemKey().trim();
			long total = CollectionStorageCounter.countVisibleStorage(client.player, ikey, store);
			String ridKey = rid.toLowerCase(Locale.ROOT);
			Long prev = lastObservedTotals.get(rid);
			lastObservedTotals.put(rid, total);
			boolean suppressPickupDelta = skipNextPickupDeltaForResource.remove(ridKey);
			if (!allowDelta) {
				continue;
			}
			if (suppressPickupDelta) {
				continue;
			}
			if (prev != null && total > prev) {
				store.addCollectionDelta(rid, total - prev);
			}
		}
	}

	/**
	 * While chests, SkyBlock sack menus, etc. are open, visible slot totals jump — only treat changes as collection
	 * progress in-world or in the plain survival inventory screen.
	 */
	private static boolean allowInventoryDeltaForScreen(Screen screen) {
		if (screen == null) {
			return true;
		}
		return screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen;
	}
}
