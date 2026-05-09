# Timerin's Addons

**Minecraft 1.21.11 Fabric mod for Hypixel SkyBlock** that helps you track progress toward item and collection goals.

## What this mod does

- Track custom goals for items (example: `ENCHANTED_DIAMOND 64`).
- Track collection goals with live progress updates.
- Show clean overlays in-game, in inventory, or both.
- Let you hide/show overlays quickly with a keybind.
- Handle SkyBlock-style ids like `raw_fish:1` correctly in commands.

## Main features

### 1) Item Tracker
- Add item goals with commands or from hovered items.
- See `current / target` progress in overlay panels.
- Keep separate panel positions for HUD and inventory screens.

### 2) Collection Tracker
- Add collection goals with commands.
- Uses API-backed totals + local pickup/sack updates for smoother progress.
- Better sack parsing for mixed gemstone rarity lines (Rough/Flawed/Fine/Flawless/Perfect).

### 3) Overlay Controls
- Display mode: `World HUD`, `Inventory`, or `Both`.
- Toggle overlays on/off with a keybind while keeping your selected mode remembered.
- Drag panels and scale them in-game.

## Installation

1. Install Fabric for **Minecraft 1.21.11**.
2. Put these files in your `.minecraft/mods` folder:
   - [Fabric API](https://modrinth.com/mod/fabric-api)
   - [Cloth Config API](https://modrinth.com/mod/cloth-config)
   - `timerins-addons-<version>.jar` (from the repo releases page or your local `build/libs`)

Optional:
- [Firmament](https://modrinth.com/mod/firmament) (extra item-id compatibility, not required).

## Default keybinds

You can change all keybinds in **Options -> Controls -> Timerin's Addons**.

- `B` — Add hovered item to tracker
- `O` — Open config
- `H` — Toggle overlays (show/hide)

## Commands

- `/timerin config`
- `/timerin add item <item id ...> <target>`
- `/timerin add collection <resource id ...> <target>`
- `/timerin remove item <id fragment>`
- `/timerin remove collection <resource id fragment>`
- `/timerin clear`
- `/timerin clear all | item | collection`

Tip: keep a space before the number (example: `raw_fish:1 100000`).

## Worker setup (required for collection API sync)

This project uses a Cloudflare Worker so API secrets stay server-side.

Worker files in this repo:
- `worker/wrangler.jsonc`
- `worker/src/index.js`

Cloudflare steps:
1. Deploy a **Worker** from this GitHub repo.
2. Set **Root directory** to `worker`.
3. Add Worker secret: `HYPIXEL_API_KEY`.
4. Verify: `https://<your-worker>.workers.dev/health` returns `ok`.

## Config files

- `%minecraft%/config/timerins_addons/item_tracker.json`
- `%minecraft%/config/timerins_addons/collections.json`

## Build from source

```bash
./gradlew build
```

Output jar:
- `build/libs/timerins-addons-<version>.jar`
