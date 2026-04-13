package dev.timerin.timerinsaddons.config;

public final class ModConfig {
	/**
	 * Where the item tracker list is drawn: world HUD, inventory screens, or both.
	 */
	public enum TrackerDisplayMode {
		HUD,
		INVENTORY,
		BOTH
	}

	private TrackerDisplayMode displayMode = TrackerDisplayMode.BOTH;
	/** Top-left of the tracker panel when the world HUD is shown (drag with chat open). */
	private int hudPanelX = 4;
	private int hudPanelY = 4;
	/** Top-left when a container screen is open (drag while inventory is open). */
	private int inventoryPanelX = 8;
	private int inventoryPanelY = 8;
	private float hudScale = 1.0F;
	private int defaultTargetAmount = 1;

	public static ModConfig createDefault() {
		return new ModConfig();
	}

	public ModConfig normalize() {
		if (displayMode == null) {
			displayMode = TrackerDisplayMode.BOTH;
		}
		hudPanelX = Math.max(-5000, Math.min(5000, hudPanelX));
		hudPanelY = Math.max(-5000, Math.min(5000, hudPanelY));
		inventoryPanelX = Math.max(-5000, Math.min(5000, inventoryPanelX));
		inventoryPanelY = Math.max(-5000, Math.min(5000, inventoryPanelY));
		if (hudScale < 0.25F || hudScale > 4.0F) {
			hudScale = 1.0F;
		}
		defaultTargetAmount = Math.max(1, Math.min(1_000_000_000, defaultTargetAmount));
		return this;
	}

	public TrackerDisplayMode getDisplayMode() {
		return displayMode;
	}

	public void setDisplayMode(TrackerDisplayMode displayMode) {
		this.displayMode = displayMode;
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

	public float getHudScale() {
		return hudScale;
	}

	public void setHudScale(float hudScale) {
		this.hudScale = hudScale;
	}

	public int getDefaultTargetAmount() {
		return defaultTargetAmount;
	}

	public void setDefaultTargetAmount(int defaultTargetAmount) {
		this.defaultTargetAmount = defaultTargetAmount;
	}
}
