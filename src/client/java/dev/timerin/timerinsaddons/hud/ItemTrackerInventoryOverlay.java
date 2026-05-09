package dev.timerin.timerinsaddons.hud;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import dev.timerin.timerinsaddons.collections.CollectionStore;
import dev.timerin.timerinsaddons.config.ModConfig;
import dev.timerin.timerinsaddons.tracker.TrackerStore;

public final class ItemTrackerInventoryOverlay {
	private ItemTrackerInventoryOverlay() {
	}

	public static void register(TrackerStore store, CollectionStore collectionStore) {
		ScreenEvents.AFTER_INIT.register((Minecraft client, Screen screen, int scaledWidth, int scaledHeight) -> {
			if (!(screen instanceof AbstractContainerScreen)) {
				return;
			}
			ScreenEvents.afterRender(screen).register((scr, graphics, mouseX, mouseY, tickDelta) -> {
				if (!(scr instanceof AbstractContainerScreen)) {
					return;
				}
				if (client.player == null || client.options.hideGui) {
					return;
				}
				ModConfig cfg = store.getConfig();
				if (cfg.isOverlayHidden()) {
					return;
				}
				if (cfg.getDisplayMode() == ModConfig.TrackerDisplayMode.HUD) {
					return;
				}
				if (!store.getEntries().isEmpty()) {
					TrackerOverlayRenderer.render(
							graphics,
							client,
							client.player,
							store,
							cfg,
							cfg.getInventoryPanelX(),
							cfg.getInventoryPanelY());
				}
				CollectionOverlayRenderer.render(
						graphics,
						client,
						client.player,
						collectionStore,
						cfg,
						collectionStore.getSettings().getInventoryPanelX(),
						collectionStore.getSettings().getInventoryPanelY());
			});
		});
	}
}
