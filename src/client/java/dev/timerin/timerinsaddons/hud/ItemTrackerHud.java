package dev.timerin.timerinsaddons.hud;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;

import dev.timerin.timerinsaddons.collections.CollectionStore;
import dev.timerin.timerinsaddons.config.ModConfig;
import dev.timerin.timerinsaddons.tracker.TrackerStore;

public final class ItemTrackerHud {
	private ItemTrackerHud() {
	}

	public static void register(TrackerStore store, CollectionStore collectionStore) {
		HudRenderCallback.EVENT.register((GuiGraphics graphics, net.minecraft.client.DeltaTracker tickCounter) -> {
			Minecraft client = Minecraft.getInstance();
			LocalPlayer player = client.player;
			if (player == null || client.options.hideGui) {
				return;
			}
			// Hide world HUD while most GUIs are open (inventory overlay shows there). Chat stays an exception so panels
			// can still be dragged/scaled with chat open.
			if (client.screen != null && !(client.screen instanceof ChatScreen)) {
				return;
			}
			ModConfig cfg = store.getConfig();
			if (cfg.isOverlayHidden()) {
				return;
			}
			if (cfg.getDisplayMode() == ModConfig.TrackerDisplayMode.INVENTORY) {
				return;
			}
			if (!store.getEntries().isEmpty()) {
				TrackerOverlayRenderer.render(
						graphics,
						client,
						player,
						store,
						cfg,
						cfg.getHudPanelX(),
						cfg.getHudPanelY());
			}
			CollectionOverlayRenderer.render(
					graphics,
					client,
					player,
					collectionStore,
					cfg,
					collectionStore.getSettings().getHudPanelX(),
					collectionStore.getSettings().getHudPanelY());
		});
	}
}
