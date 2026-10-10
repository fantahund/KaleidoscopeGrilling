# KaleidoscopeGrilling

Paper-Plugin (26.3) nach dem Mod [Kaleidoscope Grilling](https://github.com/breezeth-CN/KaleidoscopeGrilling)
(Ordner `common/` und `neoforge-1.21.1/`): Grill, Spieße, Gewürze und Öle. Es baut auf **KaleidoscopeCookery** auf
(Stationen, Kochbuch, Pack-Auslieferung) und bringt ein eigenes Resourcepack mit. Das Hitze-System (烟火气) des Mods
ist bewusst nicht enthalten.

## Installation

1. KaleidoscopeCookery und CubesideUtils installieren.
2. Im Repo `KaleidoscopeCookery` (liegt neben diesem) `mvn install`, danach hier `mvn package`
   (braucht `python3`; die Pack-Werkzeuge kommen aus `../KaleidoscopeCookery/tools`, anderer Ort: `-Dcookery.dir=...`).
3. `target/KaleidoscopeGrilling-*.jar` nach `plugins/` kopieren.
4. Das Pack `plugins/KaleidoscopeGrilling/KaleidoscopeGrilling-Pack.zip` liefert der Webserver von KaleidoscopeCookery aus,
   oder es wird hochgeladen und in `config.yml` unter `resource-pack.external-url` eingetragen.

## Entwicklung

Die Daten werden mit den Werkzeugen aus KaleidoscopeCookery erzeugt:

| Befehl (im Repo KaleidoscopeCookery) | Ergebnis hier |
|---|---|
| `python3 -I tools/import_assets.py --project grilling --dest ../KaleidoscopeGrilling` | `resourcepack/imported`, `generated/model_properties.yml` |
| `python3 -I tools/gen_data.py` | `items.yml`, `recipes.yml`, Sprachdateien in `resourcepack/custom` |
| `python3 -I tools/gen_creative_tabs.py` | `generated/creative_tabs.yml` |

## Lizenzen

- Code des Mods: BSD 3-Clause (`licenses/KaleidoscopeGrilling-LICENSE-CODE.txt`, `licenses/KaleidoscopeGrilling-NOTICE.txt`).
- Assets des Mods: CC BY-NC-SA 4.0 (`resourcepack/custom/LICENSE-ASSETS.txt`), Hinweise und Änderungen in
  `resourcepack/custom/CREDITS.txt`. Das Pack ist nicht-kommerziell und steht unter derselben Lizenz.
- Kaleidoscope Grilling enthält angepasste Assets aus Kaleidoscope Cookery (Kaleidoscope Official Production Team, CC BY-NC-SA 4.0).
