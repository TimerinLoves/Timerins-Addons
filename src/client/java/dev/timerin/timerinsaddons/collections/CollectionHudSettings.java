package dev.timerin.timerinsaddons.collections;

/**
 * HUD positions for collection goals (stored in {@code collections.json}).
 */
public final class CollectionHudSettings {
	private boolean hudEnabled;
	/** World HUD panel (drag with chat open). */
	private int hudPanelX = 4;
	private int hudPanelY = 120;
	/** Container overlay panel. */
	private int inventoryPanelX = 8;
	private int inventoryPanelY = 100;
	/** Hypixel Public API key (developer.hypixel.net); stored in collections.json only. */
	private String hypixelApiKey = "";

	public boolean isHudEnabled() {
		return hudEnabled;
	}

	public void setHudEnabled(boolean hudEnabled) {
		this.hudEnabled = hudEnabled;
	}

	public int getHudPanelX() {
		return hudPanelX;
	}

	public void setHudPanelX(int hudPanelX) {
		this.hudPanelX = hudPanelX;
	}

	public int getHudPanelY() {
		return hudPanelY;
	}

	public void setHudPanelY(int hudPanelY) {
		this.hudPanelY = hudPanelY;
	}

	public int getInventoryPanelX() {
		return inventoryPanelX;
	}

	public void setInventoryPanelX(int inventoryPanelX) {
		this.inventoryPanelX = inventoryPanelX;
	}

	public int getInventoryPanelY() {
		return inventoryPanelY;
	}

	public void setInventoryPanelY(int inventoryPanelY) {
		this.inventoryPanelY = inventoryPanelY;
	}

	public String getHypixelApiKey() {
		return hypixelApiKey;
	}

	public void setHypixelApiKey(String hypixelApiKey) {
		this.hypixelApiKey = hypixelApiKey == null ? "" : hypixelApiKey.trim();
	}

	public CollectionHudSettings normalize() {
		hudPanelX = Math.max(-5000, Math.min(5000, hudPanelX));
		hudPanelY = Math.max(-5000, Math.min(5000, hudPanelY));
		inventoryPanelX = Math.max(-5000, Math.min(5000, inventoryPanelX));
		inventoryPanelY = Math.max(-5000, Math.min(5000, inventoryPanelY));
		return this;
	}
}
