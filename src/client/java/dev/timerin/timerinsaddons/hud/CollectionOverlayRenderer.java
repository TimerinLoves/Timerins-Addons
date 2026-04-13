package dev.timerin.timerinsaddons.hud;

import org.joml.Matrix3x2fStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import dev.timerin.timerinsaddons.collections.CollectionGoal;
import dev.timerin.timerinsaddons.collections.CollectionStore;
import dev.timerin.timerinsaddons.config.ModConfig;

/**
 * HUD overlay for SkyBlock collection goals (same pose-stack scaling as {@link TrackerOverlayRenderer}).
 */
public final class CollectionOverlayRenderer {
	private static final int COLOR_RED = 0xFFFF5555;
	private static final int COLOR_YELLOW = 0xFFFFAA00;
	private static final int COLOR_GREEN = 0xFF55FF55;
	private static final int COLOR_SEP = 0xFFA0A0A0;
	private static final int COLOR_TARGET = 0xFFFFFFFF;
	private static final int COLOR_LABEL = 0xFFA0A0A0;

	private CollectionOverlayRenderer() {
	}

	public static void render(
			GuiGraphics graphics,
			Minecraft client,
			LocalPlayer player,
			CollectionStore collectionStore,
			ModConfig cfg,
			int panelX,
			int panelY) {
		if (player == null || !collectionStore.getSettings().isHudEnabled() || collectionStore.getGoals().isEmpty()) {
			return;
		}
		float es = TrackerPanelGeometry.effectiveScale(cfg, client);
		Font font = client.font;

		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		try {
			pose.translate(panelX, panelY);
			pose.scale(es, es);
			float y = 0f;
			for (CollectionGoal goal : collectionStore.getGoals()) {
				int have = goal.resolveCurrent(player, collectionStore);
				int want = goal.getTargetAmount();
				ItemStack icon = goal.resolveDisplayIcon(player).copy();
				icon.setCount(1);

				graphics.renderItem(icon, 0, Mth.floor(y));
				int textY = Mth.floor(y) + 4;
				int haveColor = haveColor(have, want);
				String haveStr = Integer.toString(have);
				String wantStr = Integer.toString(want);
				String sep = " / ";
				graphics.drawString(font, haveStr, TrackerOverlayRenderer.LOCAL_TEXT_DX, textY, haveColor, true);
				int wHave = font.width(haveStr);
				graphics.drawString(font, sep, TrackerOverlayRenderer.LOCAL_TEXT_DX + wHave, textY, COLOR_SEP, true);
				int wSep = font.width(sep);
				graphics.drawString(font, wantStr, TrackerOverlayRenderer.LOCAL_TEXT_DX + wHave + wSep, textY, COLOR_TARGET, true);

				String label = goal.getDisplayLabel().isBlank() ? goal.getResourceId() : goal.getDisplayLabel();
				label = shorten(label, 18);
				graphics.drawString(font, label, TrackerOverlayRenderer.LOCAL_TEXT_DX, textY + 10, COLOR_LABEL, false);

				y += TrackerOverlayRenderer.LOCAL_ROW_HEIGHT;
			}
		} finally {
			pose.popMatrix();
		}
	}

	private static int haveColor(int have, int want) {
		if (want <= 0) {
			return COLOR_GREEN;
		}
		if (have <= 0) {
			return COLOR_RED;
		}
		if (have >= want) {
			return COLOR_GREEN;
		}
		return COLOR_YELLOW;
	}

	private static String shorten(String s, int max) {
		if (s.length() <= max) {
			return s;
		}
		return s.substring(0, max - 1) + "\u2026";
	}
}
