package dev.timerin.timerinsaddons.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

public class TrackerHelpScreen extends Screen {
	private final Screen parent;

	public TrackerHelpScreen(Screen parent) {
		super(Component.translatable("screen.timerins_addons.help.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int bw = 120;
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.minecraft.setScreen(this.parent))
				.bounds(this.width / 2 - bw / 2, this.height - 28, bw, 20)
				.build());
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		// Do not call renderBackground here: Screen.renderWithTooltipAndSubtitles already
		// blurs/draws the backdrop once per frame before this runs; a second call crashes with
		// IllegalStateException: Can only blur once per frame (1.21+).
		int maxWidth = Mth.clamp(this.width - 40, 20, 800);
		int textLeft = 24;
		int startY = 36;
		int pad = 10;
		String body = Component.translatable("screen.timerins_addons.help.body").getString();
		int lineCount = 0;
		for (String paragraph : body.split("\n", -1)) {
			Component lineComp = Component.literal(paragraph);
			for (FormattedCharSequence ignored : this.font.split(lineComp, maxWidth)) {
				lineCount++;
			}
		}
		int blockH = lineCount * this.font.lineHeight;
		int bgLeft = textLeft - pad;
		int bgTop = startY - pad;
		int bgRight = textLeft + maxWidth + pad;
		int bgBottom = startY + blockH + pad;
		graphics.fill(bgLeft, bgTop, bgRight, bgBottom, 0xE0101010);
		graphics.fill(bgLeft, bgTop, bgRight, bgTop + 1, 0xFF404040);
		graphics.fill(bgLeft, bgBottom - 1, bgRight, bgBottom, 0xFF404040);
		graphics.fill(bgLeft, bgTop, bgLeft + 1, bgBottom, 0xFF404040);
		graphics.fill(bgRight - 1, bgTop, bgRight, bgBottom, 0xFF404040);

		int y = startY;
		for (String paragraph : body.split("\n", -1)) {
			Component lineComp = Component.literal(paragraph);
			for (FormattedCharSequence line : this.font.split(lineComp, maxWidth)) {
				graphics.drawString(this.font, line, textLeft, y, 0xFFFFFFFF, true);
				y += this.font.lineHeight;
			}
		}
		super.render(graphics, mouseX, mouseY, partialTick);
	}
}
