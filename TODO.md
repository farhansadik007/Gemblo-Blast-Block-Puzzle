# Gemblo Blast: To Do

## Home background animation redesign (open)

The Home screen background is a solid theme color, now with a fine static grain. The floating blocks (`DriftBlocks` in `GameScreen.kt`) are **not approved yet**. Three versions were rejected:
- see-through colored blocks: muddy
- flat white tiles: "looks like boxes"
- solid 3D blocks blended into the background color: "same shit"

Checklist:

- [ ] Pick a direction:
  - [ ] Match a reference screenshot of a home screen the owner likes (use its style and motion, never its art)
  - [ ] Remove the floating blocks; keep only the grain and light specks
  - [ ] Big soft light glows: 2–3 huge, blurred circles of a lighter theme color drifting slowly behind the logo and mascot
- [ ] Make a quick preview on the phone (debug build) and get approval before polishing
- [ ] Works on all 7 themes: royal, teal, grape, berry, forest, ocean, sunset
- [ ] Never sits behind the top bar (trophy, gear), the logo, the mascot or the buttons
- [ ] Smooth: driven by the app clock (`ui.now`), no jitter, no pop-in (fade in and out)
- [ ] Calm: slow motion that never competes with the logo or the Play/Continue button
- [ ] Cheap to draw (no per-frame allocations), steady 60 fps on a mid-range phone
- [ ] Update DESIGN.md once approved

## Before release

- [ ] Flaticon author names for `ic_no_ads`, `ic_premium`, `ic_classic`, `ic_continue` in `licenses/ART.md`, plus a credit line in the app
- [ ] Play Console: in-app product `no_ads` ($3.99)
- [ ] AdMob: real banner, interstitial and rewarded ids at the top of `app/build.gradle.kts`
- [ ] Privacy policy: mention in-app purchases and notifications
- [ ] Story picture 7+ (and the castle) for the Journey road; "Coming soon" moves up automatically

## Done this round

- [x] Grain texture on the Home background (`Grain.kt`)
- [x] Premium candy buttons: Continue/Play, game-over buttons, and the settings popup (Home, Replay, No Ads, close, switches)
- [x] App icon: a gold plus piece hovering over its landing spot on a 5x5 jewel board. Drawn by `tools/make_icon.py` (run it to regenerate). Play Store image: `design/icon_512.png`; launcher: adaptive icon in `res/mipmap-*`
- [x] Journey mode: road map, 9 levels per story picture, Album, blurred undiscovered pieces, "Coming soon"
- [x] No Ads purchase (Play Billing), Premium popup and crown, rewarded Revive, ads between games (none in the first 10)
- [x] Grid intro wave, score count-up, rainbow combo border, combo bell melody, recorded clear and win sounds, calm piano
- [x] Daily reminder notification (7 PM, skipped on days already played; switch in settings)
- [x] PaperCrane Labs splash on white; Back button handling with "press back again to exit"
