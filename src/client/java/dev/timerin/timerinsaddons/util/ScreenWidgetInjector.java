package dev.timerin.timerinsaddons.util;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;

import dev.timerin.timerinsaddons.mixin.client.ScreenInvoker;

/**
 * Cloth config screens are not our subclasses; {@link Screen#addRenderableWidget} is protected.
 */
public final class ScreenWidgetInjector {
	private ScreenWidgetInjector() {
	}

	public static void addRenderableWidget(Screen screen, AbstractWidget widget) {
		((ScreenInvoker) screen).timerins$invokeAddRenderableWidget(widget);
	}
}
