package dev.timerin.timerinsaddons.mixin.client;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

/**
 * Calls vanilla's private {@code getHoveredSlot(mouseX, mouseY)} so hit-testing matches the game
 * (the protected {@code isHovering(Slot,...)} overload we tried to shadow is private in 1.21.11).
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenInvoker {
	@Invoker("getHoveredSlot")
	@Nullable
	Slot timerins$invokeGetHoveredSlot(double mouseX, double mouseY);
}
