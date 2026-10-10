# KaleidoscopeGrilling

Paper plugin (26.3) based on the mod [Kaleidoscope Grilling](https://github.com/breezeth-CN/KaleidoscopeGrilling)
(folders `common/` and `neoforge-1.21.1/`): grill, skewers, seasonings and oils. It builds on **KaleidoscopeCookery**
(stations, cookbook, pack delivery) and ships its own resource pack. The mod's heat system (烟火气) is deliberately
not included.

## Features

- Crops: canola, onion, sweet potato, houttuynia (also in Nether fortresses) and a Sichuan pepper bush instead of the pepper tree
- Skewering with a stick in the off hand, fixed recipes and secret skewers, skewer recipe book and wall recipe
- Grill station: oiling, turning, seasoning, burning; skewer eating animations and sounds
- Seasoning bottles and special seasoning, Grilling food effects
- Oil press, big vat, oil types (canola, chili, magma chili) in buckets and oil pots
- Skewer plate, advanced kitchen rack, extra loot (straw hat grass drops, kitchen knife drops, fortress and village chests)
- Cookbook pages for grilling, fixed skewers, pressing canola oil and mixing seasoning

## Installation

1. Install KaleidoscopeCookery and CubesideUtils.
2. Run `mvn install` in the `KaleidoscopeCookery` repo (next to this one), then `mvn package` here
   (needs `python3`; the pack tools come from `../KaleidoscopeCookery/tools`, other location: `-Dcookery.dir=...`).
3. Copy `target/KaleidoscopeGrilling-*.jar` into `plugins/`.
4. The pack `plugins/KaleidoscopeGrilling/KaleidoscopeGrilling-Pack.zip` is served by KaleidoscopeCookery's web server,
   or upload it and set `resource-pack.external-url` in this plugin's `config.yml`.

## Development

The data is generated with the tools from KaleidoscopeCookery:

| Command (in the KaleidoscopeCookery repo) | Result here |
|---|---|
| `python3 -I tools/import_assets.py --project grilling --dest ../KaleidoscopeGrilling` | `resourcepack/imported`, `generated/model_properties.yml` |
| `python3 -I tools/gen_data.py` | `items.yml`, `recipes.yml`, `generated/skewers.yml`, language files in `resourcepack/custom` |
| `python3 -I tools/gen_creative_tabs.py` | `generated/creative_tabs.yml` |
| `python3 -I tools/gen_pepper_bush.py` | Pepper bush textures, models and seeds in `resourcepack/custom` |

## Licenses

- Mod code: BSD 3-Clause (`licenses/KaleidoscopeGrilling-LICENSE-CODE.txt`, `licenses/KaleidoscopeGrilling-NOTICE.txt`).
- Mod assets: CC BY-NC-SA 4.0 (`resourcepack/custom/LICENSE-ASSETS.txt`), notes and changes in
  `resourcepack/custom/CREDITS.txt`. The pack is non-commercial and distributed under the same license.
- Kaleidoscope Grilling contains adapted assets from Kaleidoscope Cookery (Kaleidoscope Official Production Team, CC BY-NC-SA 4.0).
