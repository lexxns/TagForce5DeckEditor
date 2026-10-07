# Tag Force 5 Deck Editor

A small JavaFX desktop app for editing the deck recipes inside a
**Yu-Gi-Oh! 5D's Tag Force 5** (PSP) save file.

It can:

- Open a save file and list the deck recipe slots stored in it.
- Show each recipe's Main / Extra / Side decks by card name.
- Replace a recipe with a `.ydk` deck exported from EDOPro / YGOPro.
- Write a `.ydk` deck into the next free recipe slot.

Card IDs are translated from the YGOPro/YDK numbering to the game's internal
Tag Force numbering using the bundled `tag_force_5.db`, so the imported deck
actually loads in-game.

> **Back up your save file before editing.** The app writes the changes straight
> back to the file you opened; there is no undo.

## Requirements

- **Java 24 or newer** (the project targets Java 24).
- **Maven 3.8+** (optional) — the bundled Maven wrapper (`./mvnw` / `mvnw.cmd`)
  downloads a suitable Maven for you, so the JDK is the only hard requirement.
- A Tag Force 5 save file (the PSP `DATA0` file).

## Usage

1. Launch the app — either unpack a release image and run `app/bin/app`, or run
   `./mvnw javafx:run` from a source checkout (see [Build from source](#build-from-source)).
2. Click **Browse...** and pick your save file. With PPSSPP on Linux the default
   location is:
   `~/.config/ppsspp/PSP/SAVEDATA/ULES014740001/DATA0`
   (Windows: `%USERPROFILE%\Documents\PPSSPP\PSP\SAVEDATA\...\DATA0`.)
   The app remembers the last file it opened; use **Clear Saved Path** to forget it.
3. Select a recipe in the left-hand list to view its contents on the right.
4. Click **Replace Selected...** and choose a `.ydk` file to overwrite that slot,
   or **Add New Deck** to write the `.ydk` into the next free slot (20 maximum).
5. Load the save in-game — the recipe appears in the deck editor.

Deck rules enforced on import:

- Main Deck: 40–60 cards.
- Extra Deck: 15 cards maximum.
- Side Deck: 15 cards maximum.
- Every card must exist in the bundled database and have a Tag Force ID,
  otherwise the import is rejected with a list of the offending IDs.

## Build from source

The build is a standard Maven build:

```bash
git clone https://github.com/lexxns/TagForce5DeckEditor.git
cd TagForce5DeckEditor

./mvnw clean package      # compile + run tests -> target/TagForceDeckEditor-<version>.jar
```

On Windows use `mvnw.cmd` instead of `./mvnw`, or just `mvn` if you already have
Maven installed.

### Run it

Run straight from the compiled classes:

```bash
./mvnw javafx:run
```

The `javafx-maven-plugin` is configured in `pom.xml`; change `<mainClass>` there if
you fork the project.

### Build a self-contained app image

To produce a runnable image with a bundled JRE and JavaFX (no separate Java
install needed on the target machine):

```bash
./mvnw javafx:jlink
```

This creates:

- `target/app/` — the app image. Run `target/app/bin/app` (Linux/macOS) or
  `target\app\bin\app.bat` (Windows).
- `target/app.zip` — the same image zipped.

The image is **platform-specific**: build it on the OS you want to run it on, and
build it with a JDK from that platform.

## Releases

Pre-built artifacts are published on the repository's
[Releases](../../releases) page:

| Artifact                                     | What it is                                                                                                                             |
|----------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------|
| `TagForceDeckEditor-<version>-linux-x64.zip` | Self-contained app image (bundled JRE + JavaFX). Unzip and run `app/bin/app`.                                                          |
| `TagForceDeckEditor-<version>.jar`           | The plain module jar. It is not standalone — JavaFX and SQLite must be on the module path, so prefer the image or `./mvnw javafx:run`. |

Releases are automated. Pushing a tag that starts with `v` triggers the
[release workflow](.github/workflows/release.yml), which builds, tests, packages
the jlink image and attaches both artifacts to a new GitHub Release:

```bash
git tag v1.0.0
git push origin v1.0.0
```

Every push and pull request also runs the
[build workflow](.github/workflows/build.yml), which compiles, runs the tests and
uploads the jar and the app image as downloadable workflow artifacts.

## Save file format

Only the parts the editor touches are documented here.

- File header: `0x20` bytes.
- Recipe slots start at `0x20`, each `0x1B4` (436) bytes, up to 20 slots.

Inside a recipe (offsets relative to the start of the recipe):

| Offset  | Size | Meaning                                               |
|---------|------|-------------------------------------------------------|
| `0x04`  | 64   | Deck name, UTF-16LE, null-terminated (32 chars max)   |
| `0x84`  | 8    | Date/time (`uint16` year, month, day, hour)           |
| `0x8C`  | 2    | Main + Extra card count                               |
| `0x94`  | 2    | Main Deck card count                                  |
| `0x98`  | 2    | Extra Deck card count                                 |
| `0x9C`  | 2    | Side Deck card count                                  |
| `0xA0`  | 120  | Main Deck card IDs (60 slots, `uint16` little-endian) |
| `0x118` | 30   | Extra Deck card IDs (15 slots)                        |
| `0x136` | 30   | Side Deck card IDs (15 slots)                         |

The three card blocks are **fixed size** — the game reserves room for a full deck
even when the Extra/Side deck is shorter — and the order is **Main → Extra → Side**.
Getting that order wrong (or packing the cards without the reserved space) makes
the game read Extra Deck monsters as Side Deck cards, where they show up blank and
unusable.

## Project layout

```
src/main/java/com/lexxns/tagforcedeckeditor/
  TagForceEditor.java        JavaFX UI and wire-up
  SaveGameParser.java        Reads/writes recipe blocks in the save file
  DeckRecipe.java            In-memory recipe
  DeckDetailsFormatter.java  Renders a recipe for the details pane
  CardIDMapper.java          YDK <-> Tag Force card ID lookups
  YDKFile.java               .ydk parser
  query/                     Tiny SQLite helper
src/main/resources/com/lexxns/tagforcedeckeditor/tag_force_5.db   Card database
src/test/java/                                                     Unit tests
```

## Troubleshooting

- **Cards appear blank or in the wrong deck in-game.** Older builds wrote the
  Extra and Side Deck blocks in the wrong order. Re-import the affected decks
  with the current version (this is also covered by `SaveGameParserTest`).
- **"Card not found in Tag Force 5 database".** The `.ydk` contains a card that
  does not exist in Tag Force 5, or an ID from a different card pool.
- **Nothing loads on startup.** The app restores the last opened file from Java
  Preferences; if that path no longer exists it does nothing. Use **Browse...**
  or **Clear Saved Path**.
