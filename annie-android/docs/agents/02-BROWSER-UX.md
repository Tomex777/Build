# Agent 2 — Annie Browser UX, navigation and responsive integration

**Mission:** Make the standalone full browser usable and visually native to Annie, retaining **real ported Omni Browser internals** from Agent 1 rather than polishing the current placeholder shell.

## Screenshot defects to reproduce

Screenshot (22 Oct): top horizontal X / “New tab” + x / + sits isolated above an enormous rounded `about:blank` address input and separate Go button; bottom toolbar is miniature text glyphs, and an undifferentiated gray empty web area fills the screen. The browser does not match Annie's palette, typography, spacing, safe-area handling or ABR.

## What to implement

- Compact usable mobile toolbar with a real address/search field and IME submit; reliable Go, focus, page loading, suggestion/search resolution, one navigation entry point (no duplicated reload/back affordances). Reasonable controls for back/forward, tab switch, bookmarks, menu, downloads and real progress; accessible vector icons and touch targets >=48dp.
- A useful styled **New Tab / Home** state instead of `about:blank` showing as an empty gray page. History and bookmarks must be real stored state, not fake tiles.
- Omni-derived tab management: create, switch, close, restore; tab count and page titles; no state loss on rotate and app background; URL bar reflects active tab only.
- Preserve website proper viewport, scroll behavior, Gecko native image/video/file upload, permissions, full screen, keyboard interactions. Fix UI that shifts or scrolls the wrong surface when an overlay is displayed, including fullscreen images (where native integration requires it).
- Handle IME/safe insets, display cutout and system navigation; test 360/393dp, >600dp tablet, landscape and split-screen. On mobile don't devote 140dp to omnibox/tabs if not necessary; avoid bottom buttons becoming tiny or overlapping gestures.
- Rebrand **Annie Browser** only; no upstream trademark in UI. Do not falsely claim every upstream tool works until integration is demonstrated.
- Follow the established `ci/check-abr.py` and the owner's stricter EBR/ABR expectations: no filler labels, native-styled inputs, no unnecessary duplicates, polished responsive interaction, no status bar tricks outside fullscreen.

## Ownership

Agent 2 owns the user-facing Compose browser interface, navigation UX, start page, UI state restoration and visuals. Agent 1 owns the imported engine/services and license integration. Agree on the interface contract and avoid concurrently editing the same original browser file.

## Required tests

- Empty fresh new tab, known URL, search phrase, keyboard Go, load failure, redirect, site permission, browser Back vs Android Back, multi-tab isolation/switch/restore, history/bookmarks, site form/file upload, image and video fullscreen, download lifecycle, private mode.
- Screenshots with emulator at 360dp portrait, 393dp portrait, 600+dp tablet, landscape for top and bottom UI; performance/memory check with >5 tabs.
- No cosmetic-only PR accepted as complete; compile and instrument with actual Gecko pages, no offline blank snapshot substituted.

**Do not** replace Omni with a second lightweight home-grown browser because it is easier.
