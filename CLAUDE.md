# Collection Selection — CLAUDE.md

## What it is
RuneLite external plugin. Turns the Collection Log into a challenge mode — players commit to one target item per page. Getting the wrong item first locks the page. Pets award unlock points to buy back locked pages.

## Build & Run
```bash
./gradlew build          # compile + check
./gradlew runClient      # launch RuneLite with plugin loaded (test mode)
```
Push to GitHub: use `push-to-github.bat` (prompts for commit message, pushes to main).
Plugin hub submission is separate — can take days to process.

## Key Files
- `src/main/java/com/collectionselection/`
  - `CollectionSelectionPlugin.java` — main plugin, inventory diff logic, lock detection
  - `LockedOverlay.java` — `WidgetItemOverlay`, grey tint + dots on collection log items
  - `TargetInfoOverlay.java` — `ABOVE_SCENE` overlay anchored to widget 161.34 / 548.2
  - `CollectionSelectionPanel.java` — sidebar panel (points, target list, unlock button)
  - `CollectionSelectionConfig.java` — config interface
- `gradle/verification-metadata.xml` — **must be kept up to date** for plugin hub builds

## Critical Widget IDs (Group 621)
- `621.20[0]` — current page title (ground-truth page detection)
- `621.37` — items panel (dynamic children = item slots)
- `621.12/16/32/34/35` — Bosses/Raids/Clues/Other/Minigames entry lists

## Native OSRS Popup (Page Locked notification)
- Widget **660** + script **3343**
- `client.openInterface(componentId, 660, WidgetModalMode.MODAL_CLICKTHROUGH)`
- `componentId = (client.getTopLevelInterfaceId() << 16) | (client.isResized() ? 13 : 43)`
- `client.runScript(3343, title, description, -1)`
- `WidgetNode` is in `net.runelite.api` (NOT `net.runelite.api.widgets`)
- `WidgetModalMode` is in `net.runelite.api.widgets`

## Important Conventions
- Persistence: `configManager.setRSProfileConfiguration("collectionlocked", ...)` — per RS profile
- Script 2731 (ScriptPostFired) triggers page detection on collection log page change
- Inventory diff uses `prevInventoryIds` to avoid false locks on login
- Right-click on entry list names uses `event.getTarget()`, NOT `actionParam0`
- Lombok 1.18.34 — always keep `gradle/verification-metadata.xml` checksums current

## Don't Touch
- Do not change the widget IDs without verifying in-game first — they break silently
- Do not remove the `prevInventoryIds` login guard — it prevents false locks on login
