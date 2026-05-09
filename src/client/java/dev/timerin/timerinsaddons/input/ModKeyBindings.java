package dev.timerin.timerinsaddons.input;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class ModKeyBindings {
	public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(
			Identifier.fromNamespaceAndPath("timerins_addons", "main"));

	public static KeyMapping OPEN_TRACKER_CONFIG;
	public static KeyMapping TOGGLE_OVERLAY_VISIBILITY;

	private ModKeyBindings() {
	}

	public static void register() {
		OPEN_TRACKER_CONFIG = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.timerins_addons.open_config",
				GLFW.GLFW_KEY_O,
				CATEGORY));
		TOGGLE_OVERLAY_VISIBILITY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.timerins_addons.toggle_overlay_visibility",
				GLFW.GLFW_KEY_H,
				CATEGORY));
	}
}
