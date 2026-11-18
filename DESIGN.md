# Gemblo Blast: Design Principles

The rules that make Gemblo Blast look and feel the way it does. Check new work against this doc. If a change breaks a rule here, it needs a reason.

**Core idea:** a classic 8×8 block puzzle that feels *premium and satisfying*. Simple rules, clean screen, and every action rewarded with juice.

---

## 1. Clean screen

- The only things on screen are the **score**, the **board**, the **3 tray pieces** and the **banner ad**.
- Top bar: **trophy + best score** at top-left, **gear (settings)** at top-right. Nothing else.
- No coins and no booster bar. Nothing in the UI that the core loop doesn't need.
- Full-screen (immersive). The system bars are hidden and can be swiped in briefly.
- One-handed: the board sits at screen center, nudged up. The tray and the thumb zone sit below it.

## 2. Color

- **Solid background with no gradient**: a "normal but darkish" saturated color.
- **The theme changes** on every app open, Play press and new game. Themes: royal, teal, grape, berry, forest, ocean, sunset (`Themes` in `GameUi.kt`).
- Each theme has a background, a deep matching **grid** color, an **empty-tile** color and a **shadow** color. Everything else is derived from the theme, so every effect fits every theme.
- **Blocks must pop.** Use five vivid, saturated colors (red, yellow, green, blue, purple). Never wash them toward white.

## 3. Blocks

- **Square, bevelled "jewel" blocks**, never rounded. Bright top lip, lit left, shaded right, dark bottom, with an almost-flat face.
- On the board, blocks sit tight on thin grid lines. **Every gap is identical**: whole-pixel cells and a whole-pixel line width.
- Tray pieces are smaller and **hover**: a soft two-layer shadow below them and a gentle bob.

## 4. Board

- It looks **pressed into the screen**:
  - a thin (~2.5dp) inset rim with shaded top/left lips and lit bottom/right lips, each fading smoothly from the background;
  - a **narrow**, soft inner shadow just inside the top edge (a hint on the sides), drawn under the blocks.
- There's no thick border. The outer grid line is the same width as the inner ones.

## 5. Juice: every action is rewarded

| Moment | Feedback |
|---|---|
| Pick up | Piece grows from tray size to board size and floats above the finger. Tick sound and light haptic. |
| Hover | Faint ghost on the target. Lines that would clear glow **neon in the piece's color**, with a sweeping shine and colored sparkles. |
| Place | Pop-in squash, plastic **snap** sound (±5% pitch wobble), click haptic, small "+N" flying from the grid center to the score. |
| Clear | Each block lifts (3D pop + drop shadow) with a **neon halo in its own color** and sparkles **around** it (never a white wash inside), then bursts into spinning shards and confetti. Plus a line beam, a shockwave ring, a screen shake that scales with size, and a big "+N" that pops at the **grid center**, holds, then flies into the score. |
| Combo | Clear sound rises a semitone per combo step. "Combo ×N" with 3 pips (moves left before it breaks). **The score dances** (sway, hop, pulse on a beat) over a spinning golden sunburst with orbiting glints, growing with the combo and fading out when it breaks. |
| Big clear / Color Flush / All Clear | Screen-edge flash in the clear's color, rainbow text, rising rumble haptic, fanfare. |
| Bad drop | Piece flies back to the tray, soft thud. |
| Game over | Board greys out row by row, then the popup. |

- **One score font.** Flying "+N" numbers use exactly the score counter's style: Fredoka, white, soft drop shadow, no fat outline.
- **Bold type.** Fredoka's variable weight axis is set explicitly (600–700).
- Text pops in (scale overshoot) and drifts up as it fades.

## 6. Popups, not screens

- Game over and settings are **popups**: a chunky white card with a 3D bottom lip that springs in (scale and wobble) over a dimmed board.
- Game over is uncluttered. It has a **block buddy** (sad with a tear, or happy on a new best), the title, the score, the trophy and best score, **Revive (ad)** once per game, and **Play again**. Jewel blocks rain gently behind the card.
- Settings: **Sound / Music / Vibrate** toggles, then **Home / Replay / No Ads** tiles.
- Every button **squashes and bounces** when pressed.

## 7. Feel and input

- **One-handed drag.** The piece moves 1.4× sideways and 1.6× upward relative to the finger, and **snaps to the nearest open spot within one cell**.
- **Continue.** The game saves after every move and when you leave the app, so it survives Android closing the app. Home shows **Continue** (with the score) plus **New game**. A finished game is never offered.
- **Fair deals.** Each set of 3 pieces can always be placed in some order when possible.
- **Haptics** drive the vibration motor directly (tick, click, heavy, rumble, boom, lose pattern), so they work even when system touch feedback is off.

## 8. Sound and music

- All sound is **generated in code** (`Sound.kt`), so there are no audio files to license. **Never copy assets from other games.**
- Placing a block is a crisp plastic snap: contact tick, hollow plastic ring, soft thud, then a tiny settle click about 14ms later. No distortion on soft sounds.
- Music is a soft, seamless loop (C–Am–F–G, 96 bpm). It pauses when the app is in the background.

## 9. Money

- A **bottom banner ad** (adaptive, with its space reserved so the board never jumps), **Revive via rewarded ad**, and a **Remove Ads** purchase.
- No coins and no pay-to-win in the core loop.
- Debug builds use Google's test ad ids. "No Ads" can be toggled back in debug builds; in release builds it is the real purchase.

## 10. Originality

- It's in the genre of 8×8 block puzzles, but **with our own name, colors, art, sounds and twists** (Color Flush, gem blocks, Fever mode and daily boards are planned).
- Don't copy any specific game's look, assets or name. Keep competitor names out of the store title.
