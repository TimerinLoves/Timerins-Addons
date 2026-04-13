package dev.timerin.timerinsaddons.hud;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.Mth;

import dev.timerin.timerinsaddons.collections.CollectionStore;
import dev.timerin.timerinsaddons.config.ModConfig;
import dev.timerin.timerinsaddons.tracker.TrackerStore;

/**
 * Drag tracker / collection panels and scroll to scale (shared {@link ModConfig#getHudScale()}).
 */
public final class TrackerPanelInteraction {
	private enum DragTarget {
		NONE,
		TRACKER_HUD,
		TRACKER_INV,
		COLLECTION_HUD,
		COLLECTION_INV
	}

	private static boolean dragging;
	private static DragTarget dragTarget = DragTarget.NONE;
	private static double grabDx;
	private static double grabDy;

	private TrackerPanelInteraction() {
	}

	public static void register(TrackerStore trackerStore, CollectionStore collectionStore) {
		ClientTickEvents.END_CLIENT_TICK.register(client -> tickDrag(client, trackerStore, collectionStore));

		ScreenEvents.AFTER_INIT.register((client, screen, sw, sh) -> {
			if (!(screen instanceof ChatScreen) && !(screen instanceof AbstractContainerScreen)) {
				return;
			}

			ScreenMouseEvents.allowMouseClick(screen).register((scr, ctx) -> allowClick(client, trackerStore, collectionStore, scr, ctx));

			ScreenMouseEvents.beforeMouseRelease(screen).register((scr, ctx) -> {
				if (ctx.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && dragging) {
					dragging = false;
					dragTarget = DragTarget.NONE;
					trackerStore.save();
					collectionStore.save();
				}
			});

			ScreenEvents.remove(screen).register(scr -> {
				if (dragging) {
					dragging = false;
					dragTarget = DragTarget.NONE;
					trackerStore.save();
					collectionStore.save();
				}
			});

			ScreenMouseEvents.allowMouseScroll(screen).register((scr, mx, my, horizontal, vertical) -> {
				if (vertical == 0.0 || client.player == null || client.options.hideGui) {
					return true;
				}
				if (Math.abs(horizontal) > Math.abs(vertical)) {
					return true;
				}
				ModConfig cfg = trackerStore.getConfig();
				boolean trackerEmpty = trackerStore.getEntries().isEmpty();
				boolean collOff = !collectionStore.getSettings().isHudEnabled() || collectionStore.getGoals().isEmpty();
				if (trackerEmpty && collOff) {
					return true;
				}
				if (scr instanceof ChatScreen) {
					if (cfg.getDisplayMode() == ModConfig.TrackerDisplayMode.INVENTORY) {
						return true;
					}
					if (!trackerEmpty && TrackerPanelGeometry.contains(mx, my, cfg.getHudPanelX(), cfg.getHudPanelY(), trackerStore, cfg, client)) {
						return applyScaleScroll(trackerStore, vertical);
					}
					if (!collOff && TrackerPanelGeometry.containsPanel(mx, my,
							collectionStore.getSettings().getHudPanelX(),
							collectionStore.getSettings().getHudPanelY(),
							TrackerPanelGeometry.collectionPanelRowCount(collectionStore), cfg, client)) {
						return applyScaleScroll(trackerStore, vertical);
					}
				} else if (scr instanceof AbstractContainerScreen) {
					if (cfg.getDisplayMode() == ModConfig.TrackerDisplayMode.HUD) {
						return true;
					}
					if (!trackerEmpty && TrackerPanelGeometry.contains(mx, my,
							cfg.getInventoryPanelX(), cfg.getInventoryPanelY(), trackerStore, cfg, client)) {
						return applyScaleScroll(trackerStore, vertical);
					}
					if (!collOff && TrackerPanelGeometry.containsPanel(mx, my,
							collectionStore.getSettings().getInventoryPanelX(),
							collectionStore.getSettings().getInventoryPanelY(),
							TrackerPanelGeometry.collectionPanelRowCount(collectionStore), cfg, client)) {
						return applyScaleScroll(trackerStore, vertical);
					}
				}
				return true;
			});
		});
	}

	private static boolean applyScaleScroll(TrackerStore trackerStore, double vertical) {
		ModConfig cfg = trackerStore.getConfig();
		float next = cfg.getHudScale() + (float) vertical * 0.08F;
		cfg.setHudScale(Mth.clamp(next, 0.25F, 4.0F));
		trackerStore.save();
		return false;
	}

	private static void tickDrag(Minecraft client, TrackerStore trackerStore, CollectionStore collectionStore) {
		if (!dragging || client.player == null) {
			return;
		}
		boolean trackerEmpty = trackerStore.getEntries().isEmpty();
		boolean collEmpty = !collectionStore.getSettings().isHudEnabled() || collectionStore.getGoals().isEmpty();
		if (trackerEmpty && collEmpty) {
			return;
		}
		ModConfig cfg = trackerStore.getConfig();
		var window = client.getWindow();
		double mx = client.mouseHandler.xpos() * (double) window.getGuiScaledWidth() / (double) window.getWidth();
		double my = client.mouseHandler.ypos() * (double) window.getGuiScaledHeight() / (double) window.getHeight();
		switch (dragTarget) {
			case TRACKER_HUD -> {
				cfg.setHudPanelX((int) Math.round(mx - grabDx));
				cfg.setHudPanelY((int) Math.round(my - grabDy));
			}
			case TRACKER_INV -> {
				cfg.setInventoryPanelX((int) Math.round(mx - grabDx));
				cfg.setInventoryPanelY((int) Math.round(my - grabDy));
			}
			case COLLECTION_HUD -> {
				var s = collectionStore.getSettings();
				s.setHudPanelX((int) Math.round(mx - grabDx));
				s.setHudPanelY((int) Math.round(my - grabDy));
			}
			case COLLECTION_INV -> {
				var s = collectionStore.getSettings();
				s.setInventoryPanelX((int) Math.round(mx - grabDx));
				s.setInventoryPanelY((int) Math.round(my - grabDy));
			}
			default -> {
			}
		}
		cfg.normalize();
		collectionStore.getSettings().normalize();
	}

	private static boolean allowClick(Minecraft client, TrackerStore trackerStore, CollectionStore collectionStore, Screen scr, MouseButtonEvent ctx) {
		if (ctx.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || client.player == null || client.options.hideGui) {
			return true;
		}
		double mx = ctx.x();
		double my = ctx.y();
		ModConfig cfg = trackerStore.getConfig();
		boolean trackerEmpty = trackerStore.getEntries().isEmpty();
		boolean collOff = !collectionStore.getSettings().isHudEnabled() || collectionStore.getGoals().isEmpty();

		if (scr instanceof ChatScreen && cfg.getDisplayMode() != ModConfig.TrackerDisplayMode.INVENTORY) {
			if (!trackerEmpty && TrackerPanelGeometry.contains(mx, my, cfg.getHudPanelX(), cfg.getHudPanelY(), trackerStore, cfg, client)) {
				dragging = true;
				dragTarget = DragTarget.TRACKER_HUD;
				grabDx = mx - cfg.getHudPanelX();
				grabDy = my - cfg.getHudPanelY();
				return false;
			}
			if (!collOff && TrackerPanelGeometry.containsPanel(mx, my,
					collectionStore.getSettings().getHudPanelX(),
					collectionStore.getSettings().getHudPanelY(),
					TrackerPanelGeometry.collectionPanelRowCount(collectionStore), cfg, client)) {
				dragging = true;
				dragTarget = DragTarget.COLLECTION_HUD;
				grabDx = mx - collectionStore.getSettings().getHudPanelX();
				grabDy = my - collectionStore.getSettings().getHudPanelY();
				return false;
			}
		} else if (scr instanceof AbstractContainerScreen && cfg.getDisplayMode() != ModConfig.TrackerDisplayMode.HUD) {
			if (!trackerEmpty && TrackerPanelGeometry.contains(mx, my, cfg.getInventoryPanelX(), cfg.getInventoryPanelY(), trackerStore, cfg, client)) {
				dragging = true;
				dragTarget = DragTarget.TRACKER_INV;
				grabDx = mx - cfg.getInventoryPanelX();
				grabDy = my - cfg.getInventoryPanelY();
				return false;
			}
			if (!collOff && TrackerPanelGeometry.containsPanel(mx, my,
					collectionStore.getSettings().getInventoryPanelX(),
					collectionStore.getSettings().getInventoryPanelY(),
					TrackerPanelGeometry.collectionPanelRowCount(collectionStore), cfg, client)) {
				dragging = true;
				dragTarget = DragTarget.COLLECTION_INV;
				grabDx = mx - collectionStore.getSettings().getInventoryPanelX();
				grabDy = my - collectionStore.getSettings().getInventoryPanelY();
				return false;
			}
		}
		return true;
	}
}
