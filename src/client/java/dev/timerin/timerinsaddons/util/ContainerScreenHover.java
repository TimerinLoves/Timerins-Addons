package dev.timerin.timerinsaddons.util;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import dev.timerin.timerinsaddons.mixin.client.AbstractContainerScreenInvoker;
import dev.timerin.timerinsaddons.mixin.client.AbstractContainerScreenMixin;

public final class ContainerScreenHover {
	private ContainerScreenHover() {
	}

/**
 * Resolves the slot under the cursor using vanilla's private {@code getHoveredSlot(mouseX, mouseY)}
 * via mixin Invoker, then the cached {@code hoveredSlot} field.
 */
	@Nullable
	public static Slot resolveHoveredSlot(Minecraft client, AbstractContainerScreen<?> screen) {
		Slot hovered = ((AbstractContainerScreenMixin) (Object) screen).timerinsaddons$getLastHoveredSlot();
		if (hovered != null) {
			return hovered;
		}
		var window = client.getWindow();
		double mx = client.mouseHandler.xpos() * (double) window.getGuiScaledWidth() / (double) window.getWidth();
		double my = client.mouseHandler.ypos() * (double) window.getGuiScaledHeight() / (double) window.getHeight();
		return ((AbstractContainerScreenInvoker) (Object) screen).timerins$invokeGetHoveredSlot(mx, my);
	}
}
