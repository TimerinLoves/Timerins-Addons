package dev.timerin.timerinsaddons.mixin.client;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
	@Shadow
	@Nullable
	protected Slot hoveredSlot;

	@Unique
	public Slot timerinsaddons$getLastHoveredSlot() {
		return hoveredSlot;
	}
}
