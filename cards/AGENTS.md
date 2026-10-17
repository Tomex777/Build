# NIGHT cards

This is the reusable card library on the dedicated `cards` branch. Preserve the existing renderer and Obsidian archive. Implement new integration in `src/` rather than rewriting old designs.

- Finished cards must be programmed SVG/raster exports. Do not use image generation.
- Keep Nocturne's circular backdrop and the old bottom-right emblem absent.
- Bind artwork to its actual character and form. Do not relabel another form's artwork. Unacquired forms stay pending.
- Record the source, exact original bytes, SHA-256, native dimensions, rights and crop for every image. Official publication does not imply an open license.
- Render and inspect real images after composition changes. Check the full master, native-pixel details, and 320px phone proof before recording a visual review. Numerical checks do not certify artistic quality.
- Any change to source, crop, identity, renderer, fonts or dependencies invalidates the affected approval fingerprint. CI must not approve new art.
- Keep masters in build artifacts. Keep artwork, recipes, reviews and reusable source in Git.
- The bot reader must serve approved files without live image search or rendering. This branch does not deploy NIGHT/MSCC. Read `../mscc/AGENTS.md` before changing the running bot or its deployment.
