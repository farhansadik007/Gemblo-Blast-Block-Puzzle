# Gemblo Blast

An 8×8 block puzzle for Android: drag pieces onto the board, clear rows and columns, chase your best score. It's built in Kotlin with Jetpack Compose.

Look and feel rules are in [DESIGN.md](DESIGN.md).

## Build

Requirements: JDK 17 and the Android SDK (put its path in `local.properties` as `sdk.dir=...`; this file is not committed).

```
./gradlew installDebug        # debug build on a connected device (package com.papercranelabs.gemblo.debug)
./gradlew testDebugUnitTest   # game rule tests
```

Debug builds always use Google's public **test** AdMob ids. Release builds refuse to build until the real AdMob ids are filled in at the top of `app/build.gradle.kts`. Signing keys are never stored in this repo.

## Project layout

- `game/Game.kt`: the rules (board, pieces, scoring, combos, Color Flush, fair piece dealing)
- `GameUi.kt`: game state, color themes and effects bookkeeping
- `GameScreen.kt`: all drawing: board, blocks, effects, Home screen, popups
- `Sound.kt`: sound effects and music, generated in code (no audio files)
- `Haptics.kt`, `Juice.kt`, `Icons.kt`, `ads/AdManager.kt`

## Licenses

- Code: all rights reserved, Paper Crane Labs.
- Font: Fredoka, SIL Open Font License 1.1, see [licenses/Fredoka-OFL.txt](licenses/Fredoka-OFL.txt).
- Gear icon outline: from Lucide (ISC license).
