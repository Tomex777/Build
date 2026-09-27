# Mira

Mira is the standalone Movies + TV sibling of Nami.

It is a separate Android application with its own package, source contracts, state and CI. The project deliberately reuses Nami's proven engineering patterns—source isolation, VLC playback, resumable downloads, API 26/36 validation—without making Mira depend on Nami being installed or treating movies/TV as anime-shaped data.

Initial real sources:
- Internet Archive: real movie search/details/direct media resolution.
- TVMaze: real TV search/details/seasons/episodes.

Android baseline: compileSdk 36, targetSdk 36, minSdk 26.
