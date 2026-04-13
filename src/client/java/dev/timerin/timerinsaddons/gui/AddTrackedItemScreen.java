package dev.timerin.timerinsaddons.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import dev.timerin.timerinsaddons.tracker.TrackerEntry;
import dev.timerin.timerinsaddons.tracker.TrackerStore;
import dev.timerin.timerinsaddons.util.ItemStackCodecUtil;

public class AddTrackedItemScreen extends Screen {
	private final Screen parent;
	private final TrackerStore store;
	private final String itemKey;
	private final ItemStack previewStack;
	private final int defaultAmount;
	private EditBox amountBox;

	public AddTrackedItemScreen(Screen parent, TrackerStore store, String itemKey, ItemStack previewStack, int defaultAmount) {
		super(Component.translatable("screen.timerins_addons.add_target.title"));
		this.parent = parent;
		this.store = store;
		this.itemKey = itemKey;
		this.previewStack = previewStack.isEmpty() ? ItemStack.EMPTY : previewStack;
		this.defaultAmount = Mth.clamp(defaultAmount, 1, 1_000_000_000);
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int cy = this.height / 2;
		this.amountBox = new EditBox(this.font, cx - 80, cy - 6, 160, 20, Component.translatable("screen.timerins_addons.add_target.amount"));
		this.amountBox.setMaxLength(12);
		this.amountBox.setValue(Integer.toString(this.defaultAmount));
		this.amountBox.setResponder(s -> {
		});
		this.addRenderableWidget(this.amountBox);
		this.setInitialFocus(this.amountBox);

		this.addRenderableWidget(Button.builder(Component.translatable("screen.timerins_addons.add_target.confirm"), b -> this.confirm())
				.bounds(cx - 85, cy + 22, 80, 20)
				.build());
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> this.minecraft.setScreen(this.parent))
				.bounds(cx + 5, cy + 22, 80, 20)
				.build());
	}

	private void confirm() {
		int target;
		try {
			target = Integer.parseInt(this.amountBox.getValue().trim());
		} catch (NumberFormatException e) {
			target = this.defaultAmount;
		}
		target = Mth.clamp(target, 1, 1_000_000_000);
		TrackerEntry entry = new TrackerEntry(this.itemKey, target);
		ItemStackCodecUtil.encode(this.previewStack).ifPresent(entry::setDisplayStackJson);
		this.store.putOrReplace(entry);
		this.store.save();
		this.minecraft.setScreen(this.parent);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		int cx = this.width / 2;
		int cy = this.height / 2;
		graphics.drawCenteredString(this.font, this.title, cx, cy - 48, 0xFFFFFF);
		graphics.drawString(this.font, Component.translatable("screen.timerins_addons.add_target.id", this.itemKey), cx - 120, cy - 32, 0xA0A0A0, false);
		if (!this.previewStack.isEmpty()) {
			graphics.renderItem(this.previewStack, cx - 8, cy - 72);
		}
		graphics.drawString(this.font, Component.translatable("screen.timerins_addons.add_target.amount_label"), cx - 80, cy - 22, 0xE0E0E0, false);
	}

	public static void open(Minecraft client, TrackerStore store, String itemKey, ItemStack previewStack, int defaultAmount) {
		Screen parent = client.screen;
		client.setScreen(new AddTrackedItemScreen(parent, store, itemKey, previewStack, defaultAmount));
	}
}
