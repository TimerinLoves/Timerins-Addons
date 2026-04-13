# Timerin's Addons

Client-side Fabric mod for **Minecraft 1.21.11** with Hypixel SkyBlock utilities:
- **Item Tracker** goals (HUD + inventory overlays)
- **Collection goals** with profile-sync + live estimate support

**Author:** Timerin

## Requirements

- Java 21
- Fabric Loader (0.18.6+)
- [Fabric API](https://modrinth.com/mod/fabric-api) for 1.21.11
- [Cloth Config API](https://modrinth.com/mod/cloth-config) (Fabric, 1.21.11)

Optional:
- [Firmament](https://modrinth.com/mod/firmament) (not required; used when present to improve raw SkyBlock item-id resolution)

## Installation

1. Install Fabric for 1.21.11.
2. Put `fabric-api`, `cloth-config`, and `timerins-addons` (from releases tab) into your `.minecraft/mods` folder.

## Controls

Defaults (change in **Options → Controls**, category **Timerin's Addons**):

- **Add hovered item to tracker** (default **B**) — with a container/inventory screen open and the cursor over a slot, opens a small dialog to set the **target amount**, then saves the item id from that stack.
- **Open item tracker config** (default **O**) — opens Cloth Config (HUD, item tracker rows, collection rows, and Hypixel API key field).

## Display modes

In config, **Tracker display** chooses where the list is drawn:

- **World HUD only** — overlay while playing (not inside container UIs as a second copy; inventory UIs can still be open with vanilla HUD visible).
- **Inventory screens only** — list while any `AbstractContainerScreen` is open (chest, player inventory, etc.).
- **Both** — draws in the world HUD and again on container screens (inventory overlay uses separate X/Y offsets).

## Commands (client)

- `/timerin config` — open config.
- `/timerin add item <item id ...> <target>` — add or replace an item goal.
- `/timerin add collection <resource id ...> <target>` — add or replace a collection goal.
- `/timerin remove item <id fragment>` — remove matching item rows.
- `/timerin remove collection <resource id fragment>` — remove matching collection rows.
- `/timerin clear` (or `/timerin clear all`) — clear items + collections.
- `/timerin clear item` — clear only item rows.
- `/timerin clear collection` — clear only collection rows.

Note: `add item` and `add collection` use a greedy id + final amount, so ids like `raw_fish:1` work as long as there is a space before the final number.

## Config files

- `%minecraft%/config/timerins_addons/item_tracker.json` — item goals + HUD settings.
- `%minecraft%/config/timerins_addons/collections.json` — collection goals, collection HUD settings, and the Hypixel API key field.

## SkyBlock item ids

Items are matched using **`ExtraAttributes.id`** from item component data when present; otherwise the mod falls back to the vanilla item registry id (useful for testing outside SkyBlock).

## Firmament (optional)

If [Firmament](https://modrinth.com/mod/firmament) is installed, the mod can prefer Firmament-provided raw SkyBlock ids when available. There is no hard dependency.

## Hypixel / fair use

This mod only **reads** your inventory and **displays** information. It does not automate gameplay. Follow Hypixel's current rules for allowed modifications.

## Building

```bash
./gradlew build
```

Output: `build/libs/timerins-addons-<version>.jar`.
