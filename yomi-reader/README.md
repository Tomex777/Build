# Yomi

Yomi is a standalone, local-first Android reader project.

This directory is intentionally separate from `torri-overlay`. Torri remains a source/extension manga app; Yomi owns local files, local folders, local library identity and local progress.

The first extraction commit establishes the brand-neutral reader-host contracts and deterministic local-library rules before any visual reader code is moved. The mature pager/webtoon implementation remains the target reader and will be adapted incrementally rather than rewritten.

Initial supported local shapes:

- one CBZ/ZIP book
- one image folder
- book folder containing image chapter folders
- book folder containing chapter archives

The Android app target remains compile/target SDK 36 and min SDK 26.
