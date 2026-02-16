# ⚔️ EcoTaleReforging

### Progressive weapon & armor upgrading system for Hytale servers

Place a **Reforge Station** block, press F — and upgrade your gear through 10 enhancement levels with rising risk, material costs, and powerful stat bonuses.

![Hytale Server Mod](https://img.shields.io/badge/Hytale-Server%20Mod-0ea5e9?style=for-the-badge)
![Version](https://img.shields.io/badge/version-1.0.2-10b981?style=for-the-badge)
![Java](https://img.shields.io/badge/Java-17+-f97316?style=for-the-badge&logo=openjdk&logoColor=white)
![License](https://img.shields.io/badge/license-MIT-a855f7?style=for-the-badge)

[**Getting Started**](#-getting-started) •
[**Features**](#-features) •
[**Commands**](#-commands) •
[**Configuration**](#%EF%B8%8F-configuration) •
[**Architecture**](#-architecture)

---

## 🌐 EcoTale Ecosystem

EcoTaleReforging is part of the **EcoTale** plugin suite — interconnected mods that together build a rich player-driven economy:

| Plugin | Description | Synergy with Reforging |
|:-------|:------------|:-----------------------|
| [**Ecotale**](https://curseforge.com/hytale/mods/ecotale) | Core economy — wallets, currency, transfers | 💰 Coin cost for each upgrade level |
| [**EcoTaleIncome**](https://curseforge.com/hytale/mods/ecotaleincome) | Earn money from mobs, mining, woodcutting, farming | 💵 Players earn → spend on reforging |
| [**EcoTaleBanking**](https://curseforge.com/hytale/mods/ecotalebanking) | Deposits, loans, credit rating | 🏦 Save up for expensive high-level reforges |
| [**EcoTaleQuests**](https://curseforge.com/hytale/mods/ecotalequests) | Daily & weekly quests | 🎯 Quest rewards fund reforging materials |
| [**EcoTaleRewards**](https://curseforge.com/hytale/mods/ecotalerewards) | Login calendar & streak bonuses | 🎁 Daily rewards include reforging materials |

> **Tip:** With all EcoTale plugins installed, players follow a natural loop: *earn* (Income) → *save* (Banking) → *upgrade gear* (Reforging) → *complete harder quests* (Quests).

---

## ✨ Features

| Feature | Description |
|:--------|:------------|
| ⚒️ **Progressive Reforging** | 10 enhancement levels with configurable success chances (90% → 5%) |
| ⚔️ **Damage & Defense Bonuses** | Weapons gain flat DMG bonus, armor gains flat DEF reduction — applied in combat via ECS damage system |
| 🪙 **Material & Coin Costs** | Each level requires specific ingot types and coin amounts |
| 💥 **Failure Penalty** | Item is destroyed on failure — configurable % of crafting materials returned |
| 🛡️ **Protection Mode** | Pay extra coins to save your item on failure (level resets to 0 instead of destruction) |
| 🔄 **Reverse Recipes** | 32 built-in item-to-material mappings for failure refunds |
| 🪨 **Reforge Station Block** | Placeable anvil-style block with custom 3D model — press F to open the reforging GUI |
| 🖥️ **Player GUI** | Insert item → see chance/cost → reforge — clean native Hytale UI |
| 🛠️ **Admin Panel** | Full settings editor — levels, allowed items, recipes, general config |
| ✏️ **Character-by-Character Editor** | Edit item patterns directly in the admin panel using arrow keys |
| 🎯 **Wildcard Patterns** | `Weapon_*`, `Armor_*` — supports custom mod items |
| 🔧 **Hot Reload** | Save/Reload from admin panel — no server restart needed |
| 🌍 **6 Languages** | Full localization: English, Русский, Deutsch, Français, Español, Português (BR) |
| 🔐 **LuckPerms** | Permission-based access: `ecotalereforging.use`, `ecotalereforging.admin` |
| 📋 **DynamicTooltipsLib** | Reforge level and stat bonuses displayed in item tooltips |
| 🖼️ **Material Icons** | Ingot icons shown in reforge GUI and admin panel |

### 🔌 Integrations

| Integration | Description |
|:------------|:------------|
| 💰 **[Ecotale](https://curseforge.com/hytale/mods/ecotale)** | Coin costs for each reforge level — deducted from the player's wallet. Without Ecotale, reforging is free. |
| 📋 **[DynamicTooltipsLib](https://curseforge.com/hytale/mods/dynamictooltipslib)** | Reforge level and stat bonuses (`+5 DMG`, `+3 DEF`) displayed directly in item tooltips. |
| 🔐 **[LuckPerms](https://curseforge.com/hytale/mods/luckperms)** | Permission-based access — control who can reforge (`ecotalereforging.use`) and who can administrate (`ecotalereforging.admin`). |

All integrations are **optional** — the plugin works standalone without any of them.

### Admin Panel Sections

| Section | Description |
|:--------|:------------|
| **General Settings** | Debug mode, language, max level, failure return rate |
| **Level Editor** | Success chance, coin cost, DMG/DEF bonus, material type & count per level |
| **Allowed Items** | Weapon & armor patterns with add/remove/character-level editing |
| **Reverse Recipes** | Browse, edit, add, remove crafting material return recipes |
| **Plugin Stats** | Plugin version, number of configured levels |

### ⚔️ How Bonuses Work

**Damage Bonus (DMG)** — flat additive damage added to every weapon attack.

- Each reforge level adds its configured `weaponDamageBonus` to the total
- The bonus is cumulative across all levels: a +3 weapon adds levels 1 + 2 + 3
- Formula: `finalDamage = baseDamage + totalDmgBonus`
- Example: Iron Sword base 10 dmg, reforged to +3 (bonus +7.5) → deals **17.5** per hit

**Defense Bonus (DEF)** — flat damage reduction applied to every incoming hit.

- Each reforged armor piece contributes its cumulative `armorDefenseBonus`
- All equipped reforged armor pieces stack together
- Minimum damage after reduction is always **1** (can't reduce to zero)
- Formula: `finalDamage = max(1, incomingDamage - totalDefBonus)`
- Example: Player wearing +2 helmet (DEF +3.5) and +1 chestplate (DEF +1.5) → total DEF reduction is **5.0**. A hit of 20 dmg → takes **15.0** damage

> [!NOTE]
> Reforge bonuses are **flat values**, not percentages. The "Defense 10%" shown in the vanilla Hytale HUD comes from the base armor item stats, not from reforging. Reforge DEF is an additional flat reduction applied on top of the base armor defense.

---

## 📦 Requirements

| Dependency | Version | Required | Description |
|:-----------|:--------|:--------:|:------------|
| [Ecotale](https://curseforge.com/hytale/mods/ecotale) | ≥ 1.0.0 | ❌ | Economy — coin costs for reforging |
| [LuckPerms](https://curseforge.com/hytale/mods/luckperms) | any | ❌ | Permission-based access control |
| [DynamicTooltipsLib](https://curseforge.com/hytale/mods/dynamictooltipslib) | any | ❌ | Reforge stats in item tooltips |

> [!TIP]
> All dependencies are optional. Without Ecotale, reforging is free. Without DynamicTooltipsLib, tooltips won't show reforge bonuses but everything else works.

---

## 🚀 Getting Started

```bash
# 1. Download the latest release
# 2. Drop into your server's mods/ folder
cp EcoTaleReforging-1.0.1.jar /server/mods/

# 3. Start the server — config and assets are extracted automatically
# 4. Place a Reforge Station block and press F to open the GUI
```

**That's it.** All 10 reforge levels are pre-configured with balanced costs and success rates.

---

## 🎮 Commands

| Command | Description | Permission |
|:--------|:------------|:-----------|
| `/reforge info` | Show reforge info for the held item | `ecotalereforging.use` |
| `/reforge help` | Show available commands | `ecotalereforging.use` |
| `/reforgeadmin` | Open the admin settings panel | `ecotalereforging.admin` |
| `/reforgeadmin reload` | Reload configuration from file | `ecotalereforging.admin` |

> **Note:** The reforge GUI opens exclusively by pressing **F** on the Reforge Station block — there is no command to open it.

---

## 🔐 Permissions

| Permission | Description | Default |
|:-----------|:------------|:--------|
| `ecotalereforging.use` | Use the Reforge Station block and view item info | All players |
| `ecotalereforging.admin` | Admin panel and reload command | OP |

---

## 🌍 Supported Languages

| Language | Code | File |
|:---------|:-----|:-----|
| 🇬🇧 English | `en` | `lang/en.json` |
| 🇷🇺 Русский | `ru` | `lang/ru.json` |
| 🇩🇪 Deutsch | `de` | `lang/de.json` |
| 🇫🇷 Français | `fr` | `lang/fr.json` |
| 🇪🇸 Español | `es` | `lang/es.json` |
| 🇧🇷 Português (BR) | `pt_br` | `lang/pt_br.json` |

Set the language in config: `"language": "ru"`. Each file contains ~140 translation keys covering all GUI text, messages, and admin panel labels.

---

## ⚙️ Configuration

Config file: `mods/com.crystalrealm_EcoTaleReforging/EcoTaleReforging.json`

<details>
<summary><b>General Settings</b></summary>

| Setting | Default | Description |
|:--------|:--------|:------------|
| `language` | `"en"` | Server language (`en`, `ru`, `de`, `fr`, `es`, `pt_br`) |
| `maxReforgeLevel` | `10` | Maximum enhancement level |
| `debugMode` | `false` | Enable debug logging |
| `failureReturnRate` | `0.30` | % of crafting materials returned on failure (30%) |
| `protectionEnabled` | `true` | Allow players to pay extra to protect items from destruction on failure |
| `protectionCostMultiplier` | `2.0` | How much extra the protection costs (×2 = double the base coin cost) |

</details>

<details>
<summary><b>⚒️ Level Configuration</b></summary>

Each level has a per-level increment for DMG and DEF. The "Total" columns show cumulative bonuses at that level.

| Level | Success | DMG (this level) | DMG (total) | DEF (this level) | DEF (total) | Coin Cost | Material |
|:-----:|:-------:|:---------:|:-----------:|:---------:|:-----------:|:---------:|:---------|
| +1 | 90% | +2.0 | 2.0 | +1.5 | 1.5 | 100 | Iron ×2 |
| +2 | 85% | +2.5 | 4.5 | +2.0 | 3.5 | 200 | Iron ×4 |
| +3 | 75% | +3.0 | 7.5 | +2.5 | 6.0 | 350 | Iron ×6 |
| +4 | 65% | +3.5 | 11.0 | +3.0 | 9.0 | 500 | Cobalt ×4 |
| +5 | 55% | +4.0 | 15.0 | +3.5 | 12.5 | 750 | Cobalt ×6 |
| +6 | 45% | +5.0 | 20.0 | +4.0 | 16.5 | 1,000 | Cobalt ×8 |
| +7 | 35% | +6.0 | 26.0 | +5.0 | 21.5 | 1,500 | Mithril ×4 |
| +8 | 25% | +7.0 | 33.0 | +6.0 | 27.5 | 2,000 | Mithril ×6 |
| +9 | 15% | +9.0 | 42.0 | +7.5 | 35.0 | 3,000 | Adamantite ×4 |
| +10 | 5% | +12.0 | 54.0 | +10.0 | 45.0 | 5,000 | Onyxium ×4 |

</details>

<details>
<summary><b>🎯 Allowed Item Patterns</b></summary>

```json
{
  "weapons": ["Weapon_*"],
  "armor": ["Armor_*"]
}
```

Add custom mod items:
```json
{
  "weapons": ["Weapon_*", "CoolMod_Blade_*", "ExoticWeapons_*"],
  "armor": ["Armor_*", "CoolMod_Armor_*"]
}
```

</details>

<details>
<summary><b>🔄 Reverse Recipes</b></summary>

32 built-in recipes. Example:
```json
{
  "Weapon_Sword_Iron": [{ "itemId": "Ingredient_Bar_Iron", "count": 12 }],
  "Armor_Chest_Gold": [{ "itemId": "Ingredient_Bar_Gold", "count": 16 }]
}
```

On failure, `failureReturnRate` (30%) of these materials are returned to the player's inventory.

</details>

---

## 🏗️ Architecture

```
src/main/java/com/crystalrealm/ecotalereforging/
├── EcoTaleReforgingPlugin.java        # Plugin lifecycle
├── commands/                           # /reforge, /reforgeadmin
├── config/
│   ├── ConfigManager.java             # JSON load/save/hot-reload
│   └── ReforgeConfig.java             # Config model
├── gui/
│   ├── ReforgeGui.java                # Player reforge interface
│   ├── AdminReforgeGui.java           # Admin settings panel
│   └── PageOpenHelper.java            # Page opening utility
├── lang/
│   └── LangManager.java              # 6-language localization
├── npc/
│   └── ReforgeStationManager.java     # Player tracking & block interaction
├── service/
│   ├── ItemValidationService.java     # Wildcard pattern matching
│   ├── ReforgeDataStore.java          # Reforge level storage
│   ├── ReforgeService.java            # Core reforge logic
│   └── WeaponStatsService.java        # Weapon stat integration
├── system/
│   ├── ReforgeActionBarSystem.java    # Action bar stat display
│   └── ReforgeDamageSystem.java       # ECS damage & defense system
├── tooltip/
│   └── ReforgeTooltipProvider.java    # DynamicTooltipsLib provider
└── util/
    ├── AssetExtractor.java            # JAR resource extraction
    ├── MessageUtil.java               # Message formatting
    ├── MetadataHelper.java            # Item metadata (reforge level)
    ├── PermissionHelper.java          # LuckPerms integration
    └── PluginLogger.java              # Structured logger
```

### Assets

```
src/main/resources/
├── manifest.json
├── default-config.json                 # 10 levels, 32 reverse recipes
├── lang/
│   ├── en.json                        # English (~140 keys)
│   ├── ru.json                        # Русский
│   ├── de.json                        # Deutsch
│   ├── fr.json                        # Français
│   ├── es.json                        # Español
│   └── pt_br.json                     # Português (BR)
├── Common/
│   ├── Blocks/Benches/
│   │   ├── ReforgeStation.blockymodel # 3D anvil model
│   │   └── ReforgeStation_Texture.png # 32×32 texture
│   ├── Icons/ItemsGenerated/
│   │   └── EcoTale_Reforge_Station.png  # 16×16 icon
│   └── UI/Custom/Pages/
│       ├── CrystalRealm_EcoTaleReforging_ReforgePanel.ui
│       └── CrystalRealm_EcoTaleReforging_AdminPanel.ui
└── Server/
    ├── Item/
    │   ├── Items/EcoTaleReforging/EcoTale_Reforge_Station.json
    │   └── Block/Hitboxes/EcoTaleReforging/EcoTale_Reforge_Station.json
    └── Languages/ (en-US, ru-RU, fallback)
```

---

## 🔧 Technical Details

- **Java 17**, Gradle 9.3.1
- **Hytale .ui** — button-based GUI (no text input fields)
- **Gson** for JSON config serialization
- **DynamicTooltipsLib v1.2.0** — optional tooltip integration
- **ECS** — `ReforgeDamageSystem` registers via `EntityStoreRegistry`
- **Hot-reload** — `ConfigManager.reload()` updates config in-place
- **Wildcard patterns** — `*` suffix = prefix match, standalone `*` = match all
- **Reforge Station** — block interaction via `UseBlockEvent.Pre` ECS system

---

## 📝 Changelog

### v1.0.2
- Removed non-functional HP bonus system (Hytale API limitation — max health cannot be modified via plugins yet)
- Protection mode: pay ×2 coin cost to prevent item destruction on failure (item stays, level resets to 0)
- Updated level table with balanced progression (Iron → Cobalt → Mithril → Adamantite → Onyxium)
- Added "How Bonuses Work" documentation explaining flat DMG/DEF mechanics

### v1.0.1
- Fixed tooltip registration (direct API instead of reflection)
- Added DynamicTooltipsLib to OptionalDependencies
- ActionBar notification system for reforge results
- Removed unused NPC code — reforging is now exclusively via Reforge Station block

### v1.0.0
- Progressive reforging: 10 levels, success chances, materials, coin costs
- Reforge Station block with custom 3D model
- Player reforge GUI and full admin settings panel
- Damage & defense system (`ReforgeDamageSystem`)
- Material refund on failure (configurable %)
- 32 reverse recipes for weapons & armor
- Character-by-character pattern editor in admin panel
- Custom mod item support via wildcard patterns
- 6-language localization (EN, RU, DE, FR, ES, PT-BR)
- LuckPerms integration
- DynamicTooltipsLib tooltip integration
- Hot-reload configuration
