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

Die Einstellungen liegen in `src/main/resources/assets/nuit_chains/sky/` (die Zahl vorne = Zeichenreihenfolge):

| Datei | Inhalt |
|---|---|
| `01_pink_galaxy.json` | Hintergrund-Himmel (Nebel, Galaxie-Band, große Sterne) |
| `02_stars.json` | Funkelnde Sterne, kurzes Aufblitzen, Sternschnuppen |
| `03_haze_low.json` / `04_haze_high.json` | Zwei treibende Nebel-Schichten (`speed` = Tempo, 1 = eine Runde in 20 Minuten, negativ = andere Richtung) |
| `05_clouds.json` | Düstere Wolkenbänke mit pink angestrahlten Rändern, ziehen langsam vorbei (`speed`) |
| `06_wisps.json` | Dünne Wolkenfetzen davor, ziehen schneller – gibt Tiefe |
| `07_lightning.json` | Blitze |
| `08_chains.json` | Ketten: Anzahl, Höhe, Größe, Bewegung, Schäden |

Eine Ebene ausschalten: Datei per eigenem Ressourcenpaket überschreiben und `"conditions": { "skyboxes": { "entries": ["minecraft:none"] } }` setzen.

Mit einem eigenen Ressourcenpaket lassen sich diese Dateien auch überschreiben, und zwar unter demselben Pfad `assets/nuit_chains/sky/…`.

Wichtige Werte in `08_chains.json` → `chains`:

| Wert | Bedeutung | Standard |
|---|---|---|
| `spacing` | Abstand des Rasters in Blöcken | 180 |
| `chance` | Anteil der Rasterfelder mit Kette (0–1, weniger = weniger Ketten) | 0.48 |
| `cellRadius` | Wie viele Felder rundum sichtbar sind: 5 ≈ 1000 Blöcke Sichtweite (bis 10 möglich; mehr = mehr Ketten, kostet etwas mehr Leistung – bei FPS-Problemen auf 3 oder 4 senken) | 5 |
| `topY` | Aufhänge-Höhe | 450 |
| `minLength` / `maxLength` | Kettenlänge (wird gekürzt, falls sie unter `minBottomY` reichen würde) | 120 / 220 |
| `linkLength` / `linkWidth` / `thickness` | Größe der Glieder | 18 / 11.5 / 1.85 |
| `spikes` | Stacheln pro Seite eines Glieds (0 = glatt) | 1 |
| `lean` | Maximale Schräglage einer Kette (Grad) | 12 |
| `topFade` | Über wie viele Blöcke die Kette oben weich aus dem Nichts eingeblendet wird (höchstens 40 % der Kettenlänge) | 150 |
| `swingDegrees` / `swingSpeed` | Pendel-Ausschlag und Tempo | 1.6 / 1 |
| `wave` | Wie stark sich die Kette beim Pendeln biegt | 0.75 |
| `seed` | Andere Zahl = andere Verteilung | 2424 |

Unter `style`: `spikeStyle` (`"studded"` = gerade Stacheln mit Kragen, `"hooks"` = gebogene Widerhaken), `spikeSize` (Größe der Stacheln, 1 = normal).

Unter `damage`: `brokenChance` (Anteil zerfallender Ketten), `brokenLinks` (wie viele Glieder am Ende zerfallen), `wear` / `wearStrength` (Anteil und Größe der Einschläge entlang der ganzen Kette), `fragments` / `fragmentFall` (glühende Tropfen unter dem Ende), `veins` (leuchtende Energie-Adern auf den Gliedern, zum Ende hin stärker, 0–1, Standard 0.6).

Unter `variation`: `sizeMin` / `sizeMax` (Größenbereich der Ketten), `giantChance` / `giantSize` (seltene Riesen-Ketten), `topYVariation` (wie stark die Aufhängehöhe schwankt), `minBottomY` (tiefster Punkt, bis zu dem eine Kette hängen darf – Standard 230), `fadeVariation` (wie unterschiedlich lang das Einblenden oben ist, 0–1, Standard 0.8), `fadeOffset` (bis zu so vielen Blöcken bleibt eine Kette oben zusätzlich unsichtbar, jede anders – dadurch fangen sie unterschiedlich hoch an, Standard 70).

Unter `motion`: `windStrength` / `windSpeed` (gemeinsame, weiche Windböen für alle Ketten), `torsionDegrees` / `torsionSpeed` (Ketten drehen sich langsam um sich selbst), `emberPulse` (wie stark die glühenden Bruchkanten pulsieren), `waveDegrees` / `waveLength` / `wavePeriod` (Welle – Standard 11° / 11 Glieder / 4 s, die wie bei einer Peitsche von oben nach unten durch die Kette läuft: Stärke, Länge in Gliedern, Sekunden pro Welle). Alles läuft nur über die Zeit – Spieler in der Nähe verändern nichts.

Unter `energy` (Animationen wie beim Vorbild, rein über die Zeit, Spieler verändern nichts):

| Wert | Bedeutung | Standard |
|---|---|---|
| `flow` | Stärke der Energie-Stöße, die leuchtend von oben nach unten durch die Kette fließen (0 = aus) | 1 |
| `flowSpeed` / `flowInterval` | Tempo der Stöße (1 = etwa 3 Glieder pro Sekunde) / Sekunden zwischen zwei Stößen | 1 / 6 |
| `shimmer` | Wie lebendig die leuchtenden Adern über die Glieder kriechen und wabern (0 = stillstehend) | 1 |
| `arcs` | Stärke der elektrischen Bögen, die aus den Ketten zucken – das Glied leuchtet dabei auf (0 = aus) | 1 |
| `arcInterval` | Durchschnittliche Sekunden zwischen zwei Bögen an derselben Kette | 9 |
| `arcLength` | Länge der Bögen (1 = normal) | 1 |
| `sparks` | Funken, die bei jedem Bogen wegfliegen und verglühen | 7 |

Weitere Schalter: `hideClouds` (Vanilla-Wolken **und** die LOD-Wolken von Distant Horizons aus, Standard `true` – deine DH-Einstellungsdatei bleibt dabei unverändert), `nearDistance` (bis zu dieser Entfernung sind Ketten echte 3D-Objekte in der Welt, Standard 220).

`glow` = Stärke des Leuchtens um die Ketten (0 = aus, Standard 0.45; leuchtet bei Blitzen kurz mit auf). `tint` färbt die Ketten ein, z. B. `{ "red": 1, "green": 0.6, "blue": 0.9 }`.

### Sterne (`02_stars.json` → `stars`)

| Wert | Bedeutung | Standard |
|---|---|---|
| `count` | Anzahl der Sterne (die meisten ganz schwach) | 2200 |
| `brightness` / `size` | Helligkeit / Größe aller Sterne | 1 / 1 |
| `twinkle` / `twinkleSpeed` | Wie stark und wie schnell sie funkeln (0 = gar nicht) | 0.7 / 1 |
| `flares` | Wie oft einzelne Sterne kurz hell aufblitzen (0 = nie) | 1 |
| `shootingStarInterval` | Sekunden zwischen Sternschnuppen im Schnitt (0 = keine) | 24 |
| `bigStarPulse` | Wie stark die zwei großen Sterne langsam pulsieren und glitzern | 1 |

### Blitze (`07_lightning.json` → `lightning`)

| Wert | Bedeutung | Standard |
|---|---|---|
| `averageInterval` | Durchschnittlicher Abstand zwischen Blitzen (Sekunden) | 14 |
| `doubleChance` | Chance auf einen zweiten Blitz direkt danach | 0.35 |
| `minElevation` / `maxElevation` | Höhe des Startpunkts am Himmel (Grad) | 12 / 62 |
| `length` | Länge eines Blitzes (Grad) | 38 |
| `branches` | Maximale Verzweigungen | 4 |
| `width` / `glowSize` | Dicke des Blitzes / Größe des Leuchtens | 1 / 1 |
| `flashStrength` | Wie stark der ganze Himmel aufhellt | 0.22 |

`color` färbt die Blitze.

## Pinkes HUD

Hotbar, Auswahl-Rahmen, Nebenhand, Herzen, Rüstung, Essen und XP-Leiste sind pink. Auf der Hotbar läuft Energie um die Slot-Rahmen, die Rahmen flackern, Funken knistern, der ausgewählte Slot pulsiert, die Level-Zahl pulsiert pink. Die Grafiken sind feste Bilder; die Bewegung zeichnet der Mod per Code (`PinkHud`) – animierte GUI-Grafiken per `.mcmeta` werden in 26.3 nicht zuverlässig geladen.

Das HUD ist ein eingebautes Ressourcenpaket **„Pinkes HUD (Nuit Chains)“** – standardmäßig an, unter *Optionen → Ressourcenpakete* abschaltbar. Die Grafiken erzeugt `tools/gen_hud.py` (Farben und Animation stehen oben im Skript).

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
