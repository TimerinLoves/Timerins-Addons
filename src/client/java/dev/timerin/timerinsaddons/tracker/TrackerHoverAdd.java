package dev.timerin.timerinsaddons.tracker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import dev.timerin.timerinsaddons.gui.AddTrackedItemScreen;
import dev.timerin.timerinsaddons.skyblock.SkyBlockItemIds;
import dev.timerin.timerinsaddons.util.ContainerScreenHover;

public final class TrackerHoverAdd {
	private TrackerHoverAdd() {
	}

	public static void tryAddHovered(Minecraft client, TrackerStore store) {
		Screen screen = client.screen;
		if (!(screen instanceof AbstractContainerScreen<?> container)) {
			feedback(client, Component.translatable("message.timerins_addons.need_container"), false);
			return;
		}
		Slot slot = ContainerScreenHover.resolveHoveredSlot(client, container);
		if (slot == null || !slot.hasItem()) {
			feedback(client, Component.translatable("message.timerins_addons.no_stack"), false);
			return;
		}
		ItemStack stack = slot.getItem();
		String key = SkyBlockItemIds.resolveKey(stack).orElse("");
		if (key.isEmpty()) {
			feedback(client, Component.translatable("message.timerins_addons.no_key"), false);
			return;
		}
		int def = store.getConfig().getDefaultTargetAmount();
		AddTrackedItemScreen.open(client, store, key, stack, def);
	}

	private static void feedback(Minecraft client, Component text, boolean actionBar) {
		if (client.player != null) {
			client.player.displayClientMessage(text, actionBar);
		}
	}
}
