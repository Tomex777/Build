# Humanoid hair sources

Short, Bob, and Afro come from the MakeHuman Community system asset pack:
https://static.makehumancommunity.org/assets/assetpacks/makehuman_system_assets.html

These assets are CC0-1.0. Each MHCLO and material source includes its CC0 declaration;
the full CC0 license is bundled with the humanoid in the application.

`source.json` records hashes of the original files retrieved from the official pack.
`prepared.json` records hashes of every converter input used here. Source OBJ geometry,
MHCLO vertex mappings, and material metadata are retained unchanged. `tint.png` is a
derived texture: the original RGBA image is resized to 512 pixels with Lanczos,
converted to grayscale, and luminance remapped to 96–255; alpha is retained. The app
applies the selected hair color to this neutral texture.

The independent converter uses each asset's vertex mappings to fit the neutral body
and its four shape targets. Hair is rigidly weighted to the existing head joint.
It is suitable for posing; it does not simulate hair movement or collisions.
