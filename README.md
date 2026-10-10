# Nuit Chains

Client-Mod für **Minecraft 26.3 (Fabric)** und Addon für [Nuit](https://modrinth.com/mod/nuit).

- Pinker Galaxy-Himmel ist direkt eingebaut, ein Ressourcenpaket ist nicht nötig.
- **Echte 3D-Ketten** hängen überall in der Welt hoch am Himmel. Man kann drunter durchlaufen, sieht sie von unten und von der Seite, und sie pendeln leicht.
- Rein clientseitig, funktioniert also auch auf fremden Servern.

## Installieren

In den `mods`-Ordner kommen:
1. Fabric API
2. Nuit (Fabric, 26.3)
3. `nuit-chains-….jar` (unter *Releases* → `latest`)

Ein altes Pink-Galaxy-Ressourcenpaket sollte dabei deaktiviert sein, sonst liegen zwei Himmel übereinander.

## Anpassen ohne Programmieren

Die Einstellungen liegen in `src/main/resources/assets/nuit_chains/sky/`:

| Datei | Inhalt |
|---|---|
| `01_pink_galaxy.json` | Hintergrund-Himmel |
| `02_chains.json` | Ketten: Anzahl, Höhe, Größe, Pendeln |

Mit einem eigenen Ressourcenpaket lassen sich diese Dateien auch überschreiben, und zwar unter demselben Pfad `assets/nuit_chains/sky/…`.

Wichtige Werte in `02_chains.json` → `chains`:

| Wert | Bedeutung | Standard |
|---|---|---|
| `spacing` | Abstand des Rasters in Blöcken | 220 |
| `chance` | Anteil der Rasterfelder mit Kette (0–1) | 0.55 |
| `cellRadius` | Wie viele Felder rundum sichtbar sind (mehr = mehr Ketten, mehr Leistung) | 2 |
| `topY` | Aufhänge-Höhe | 420 |
| `minLength` / `maxLength` | Kettenlänge | 150 / 280 |
| `linkLength` / `linkWidth` / `thickness` | Größe der Glieder | 11 / 7 / 1.45 |
| `spikes` | Dornen pro Seite eines Glieds (0 = glatt) | 2 |
| `lean` | Maximale Schräglage einer Kette (Grad) | 14 |
| `swingDegrees` / `swingSpeed` | Pendel-Ausschlag und Tempo | 3 / 1 |
| `wave` | Wie stark sich die Kette beim Pendeln biegt | 0.6 |
| `seed` | Andere Zahl = andere Verteilung | 2424 |

Unter `damage`: `brokenChance` (Anteil zerfallender Ketten), `brokenLinks` (wie viele Glieder am Ende zerfallen), `wear` / `wearStrength` (kleine Schäden mitten in der Kette), `fragments` / `fragmentFall` (Kristall-Splitter unter dem Ende).

Weitere Schalter: `hideClouds` (Vanilla-Wolken aus, Standard `true`), `nearDistance` (bis zu dieser Entfernung sind Ketten echte 3D-Objekte in der Welt, Standard 220).

`tint` färbt die Ketten ein, z. B. `{ "red": 1, "green": 0.6, "blue": 0.9 }`.

## Neue Effekte hinzufügen

1. Eine neue Klasse nach dem Vorbild von `ChainSkybox.java` anlegen. Sie erbt von Nuits `AbstractSkybox`, braucht einen `CODEC` und eine `render()`-Methode.
2. Die Klasse in `NuitChainsClient.onInitializeClient()` registrieren:
   ```java
   NuitApi.registerSkyboxType(id("mein_effekt"), 1, MeinEffekt.CODEC);
   ```
3. Eine JSON-Datei mit `"type": "nuit_chains:mein_effekt"` unter `assets/nuit_chains/sky/` ablegen.
4. Pushen. GitHub baut die `.jar` dann automatisch (siehe *Actions* bzw. *Releases*).

`ChainGeometry.java` enthält nur Mathe und keinen Minecraft-Code. Dort steckt alles zu Platzierung, Pendeln und 3D-Form der Glieder.

## Selbst bauen

```
./gradlew build
```
Dafür ist Java 25 nötig. Die fertige Datei liegt danach in `build/libs/`.
