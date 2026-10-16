# Yomi — App Building Rules (ABR)

These are release gates, not optional polish. Apply them to every Yomi screen and future change.

1. **One action, one control.** Remove duplicate shortcuts and repeated cards in the same context. A reader page must not have extra controls that duplicate Mihon's own reader actions.
2. **Designed inputs.** Never surface stock Android edit fields. Search, dialogs and input states must use intentional spacing, shapes, typography, color, focus, keyboard actions and accessibility.
3. **No filler.** Each visible sentence must convey an action, a real state, or recovery instructions. Avoid generic promotional/privacy/local-mode copy.
4. **Real iconography.** Use maintained vector drawables; don't use emoji, letters or visual placeholders as permanent icons.
5. **Visual fit.** Evaluate spacing, touch targets, title wrapping, contrast, content insets, keyboard overlap, modal presentation and responsive proportions on both compact and large screens.
6. **Android 8+**: `minSdk = 26`. Preserve API 26 behavior when changing any API-sensitive feature.
7. **Functional features only.** Import a CBZ using Android Files and Open-with; decode actual pages; restore covers, progress and bookmarks; open folders; use every reading mode/gesture/settings control. Disabled or decorative controls do not count.
8. **Motion is purposeful.** Use original Mihon reader animations and gesture response; avoid gratuitous animations that delay page turns.
9. **Verify reality, not only compilation.** Build/install an APK; run API 26 and API 36 emulator acceptance, check accessibility/screenshots and actual taps, and test a user-supplied CBZ on a physical device. Inspect failure artifacts and correct problems; a passing build alone does not mean completion.
10. **Do not claim finished prematurely.** Mihon's *actual* pinned reader (`7a917968e3bf71c4a665e6655a550877d81ead1d`) must be integrated, not just approximated. Yomi keeps its own local library/branding while Mihon retains its original reader experience.

## UI review notes

- The active **Continue reading** book is excluded from the nearby **Recently opened** strip to avoid a repeated action.
- Search uses a custom Compose field, a single clear exit, keyboard Search action, and results that update without a redundant search-submit button.
- Empty states expose a meaningful next action rather than decorative promotional copy.
- Reader-source provenance and third-party notices remain intact. Never rewrite upstream reader chrome just to make Yomi look different.

## Required evidence before a release

- A successful Gradle build plus runtime Android 8/Android 16 proof, including import from `content://` and screenshot review.
- Working page decoding, tap turns, GIFs, modes, progress restoration, accessible reader settings, and open-with.
- On-device reproduction/fix for reported CBZ blank pages and missing covers.
- Production-signed and install-tested APK, not a CI debug-key candidate.

See [issue #76](https://github.com/Tomex777/Build/issues/76) for Mihon parity criteria.