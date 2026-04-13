package dev.timerin.timerinsaddons.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

/**
 * Exposes {@link Screen#addRenderableWidget} for screens we do not subclass (e.g. Cloth Config).
 * Reflection on {@link Class#getDeclaredMethod} is unreliable across loader/remap edges on 1.21.11+.
 */
@Mixin(Screen.class)
public interface ScreenInvoker {
	@Invoker("addRenderableWidget")
	GuiEventListener timerins$invokeAddRenderableWidget(GuiEventListener widget);
}
