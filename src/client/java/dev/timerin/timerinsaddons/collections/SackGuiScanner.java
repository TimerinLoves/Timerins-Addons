package dev.timerin.timerinsaddons.collections;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import dev.timerin.timerinsaddons.skyblock.SkyBlockItemIds;
import dev.timerin.timerinsaddons.tracker.TrackerStore;

/**
 * When a SkyBlock material sack screen opens, reads each row's {@code Stored:} lore once and updates
 * {@link CollectionStore}'s and {@link TrackerStore}'s last known in-sack amounts. We intentionally do not scan every
 * tick while the GUI stays open — that avoids repeated NBT/lore reads conflicting with other mods' screen overlays.
 */
public final class SackGuiScanner {
	/**
	 * After we've finished scanning for the current sack session, stays false until the player leaves sack-like screens
	 * (any non-sack or non-container screen resets this).
	 */
	private static boolean sackSnapshotAllowed = true;
	/** Empty snapshots on the first tick are common before slots sync; retry a few times then stop (no per-frame work). */
	private static int sackEmptySnapshotTicks;

	private SackGuiScanner() {
	}

	public static void tick(Minecraft client, CollectionStore store, TrackerStore trackerStore) {
		if (client.player == null || client.player.level() == null) {
			return;
		}
		if (!(client.screen instanceof AbstractContainerScreen<?> acs)) {
			sackSnapshotAllowed = true;
			sackEmptySnapshotTicks = 0;
			return;
		}
		String title = acs.getTitle().getString();
		if (!screenLooksLikeSkyBlockSack(title)) {
			sackSnapshotAllowed = true;
			sackEmptySnapshotTicks = 0;
			return;
		}
		if (!sackSnapshotAllowed) {
			return;
		}
		Map<String, Long> snapshot = new HashMap<>();
		for (Slot slot : acs.getMenu().slots) {
			var stack = slot.getItem();
			if (stack.isEmpty()) {
				continue;
			}
			var keyOpt = SkyBlockItemIds.resolveKey(stack);
			if (keyOpt.isEmpty()) {
				continue;
			}
			String key = normalizeSackKey(keyOpt.get());
			if (key.isEmpty()) {
				continue;
			}
			SackLoreParser.parseStoredAmount(stack, client.player).ifPresent(stored -> snapshot.merge(key, stored, Math::max));
		}
		if (snapshot.isEmpty()) {
			sackEmptySnapshotTicks++;
			if (sackEmptySnapshotTicks < 5) {
				return;
			}
		} else {
			store.applySackGuiSnapshot(snapshot);
			if (trackerStore != null && !trackerStore.getEntries().isEmpty()) {
				trackerStore.applySackGuiSnapshot(snapshot);
				trackerStore.save();
			}
		}
		sackSnapshotAllowed = false;
		sackEmptySnapshotTicks = 0;
	}

	private static boolean screenLooksLikeSkyBlockSack(String title) {
		if (title == null || title.isEmpty()) {
			return false;
		}
		String t = title.toLowerCase(Locale.ROOT);
		if (!t.contains("sack")) {
			return false;
		}
		// Not a material sack: listing nested sacks confuses our snapshot and clashes with overlays (e.g. SkyHanni).
		if (t.contains("sack of sack")) {
			return false;
		}
		return true;
	}

	static String normalizeSackKey(String raw) {
		if (raw == null) {
			return "";
		}
		return raw.trim();
	}
}
