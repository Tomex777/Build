# Sora third-party notices

Sora contains or adapts open-source components used by its Anime/Manga player and reader.

## Aniyomi

Source: https://github.com/aniyomiorg/aniyomi  
License: Apache License 2.0

Sora's Anime/Manga player and reader adapters are derived from Aniyomi/Mihon reader and player architecture. Adapted source files retain attribution headers and have been modified to consume Sora Core's extension-resolved streams/pages and progress model.

Copyright belongs to the respective Aniyomi, Mihon, Tachiyomi, mpvKt, and other upstream contributors.

## Aniyomi mpv library

Source: https://github.com/aniyomiorg/aniyomi-mpv-lib  
License: MIT

Sora links the Aniyomi mpv Android library for Anime/TV video rendering.

## mpv

Source: https://github.com/mpv-player/mpv

The Aniyomi mpv library incorporates libmpv and its transitive native dependencies under their respective licenses.

## Tachiyomi reader support libraries

Sora uses Tachiyomi-origin reader support libraries including the subsampling scale image view, image decoder, and DirectionalViewPager through their published open-source artifacts. Their original copyright and license terms remain applicable.

## NIGHT Play games and The Last Call

Original game engines, original story text, UI and editorial assets by NIGHT Play
(from the same Tomex777/Build repository). Sora bundles these locally under
`assets/night-play/` for offline use rather than presenting a remote website.
Relevant per-game source notices are preserved alongside the imported code.

- Chess uses `chess.js v0.10.3` (BSD-2-Clause); original notice remains in the vendored source.
- Jigsaw's engine adapts the MIT-licensed PlayJigsaw project; the full license
  and attribution are included in `night-play/jigsaw/THIRD_PARTY_LICENSE.txt`.
- Action Runner adapts categories from gamelabz's MIT HTML5 endless runner;
  see `night-play/action/THIRD_PARTY_NOTICES.md`.
- Wing Hop art uses embedded Kenney CC0 sprites, shipped as local data URIs
  rather than remote assets.
- Jigsaw gallery photography may be downloaded from Unsplash subject to its
  own license; the local procedural puzzle themes do not require a connection.

Multiplayer and online party-room services are not bundled or represented
as local games. Optional online gallery images and 3D Ludo dice assets require
a connection, while the game rules, local boards and save data are included.
