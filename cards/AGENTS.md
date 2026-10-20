# NIGHT cards

This is NIGHT's **shared anime collectible-card library**, not a BLEACH-only card system. BLEACH was the pilot/test roster used to develop and review the universal frame, typography, depth, materials and tiers. Preserve the existing renderer and Obsidian archive. Implement new integration in `src/` rather than rewriting old designs.

- **Global visual invariant:** Re:Zero, Naruto, Solo Leveling, BLEACH and every future anime use the SAME approved NIGHT card design families and core renderer. Do not invent a separate per-anime card frame, rank badge, typography system, layout or website mock-card. Franchise-specific art, ambience and accent treatments may adapt inside shared designs without replacing their geometry. Any new design variant must be approved as a new global NIGHT design, not quietly attached to one anime.
- The public collectible gallery must show actual exports from the shared renderer. A standalone HTML/CSS simulation is only a temporary concept preview, not an approved NIGHT collectible card. Separate catalogues identify universes and forms, NOT separate visual systems.

- Finished cards must be programmed SVG/raster exports. Do not use image generation.
- Every card, design and tier must place the actual character silhouette across the inner frame. Aura stays behind the architecture; aura overlap alone does not count. Byakuya, Rukia and Ichigo are accepted depth references.
- Use in-universe, form-specific ranks. Bleach and Naruto do not determine each other's grades. Keep canonical in-world ranks distinct from NIGHT editorial grades. EX is available above SSS but needs explicit evidence for the particular form and continuity. The tier-style mapping remains a proposal until the user approves it; Parallax is rejected.
- Keep Nocturne's circular backdrop and the old bottom-right emblem absent.
- Bind artwork to its actual character and form. Do not relabel another form's artwork. Unacquired forms stay pending.
- Record the source, exact original bytes, SHA-256, native dimensions, rights and crop for every image. Official publication does not imply an open license.
- Render and inspect real images after composition changes. Check the full master, native-pixel details, and 320px phone proof before recording a visual review. Numerical checks do not certify artistic quality.
- Any change to source, crop, identity, renderer, fonts or dependencies invalidates the affected approval fingerprint. CI must not approve new art.
- Keep masters in build artifacts. Keep artwork, recipes, reviews and reusable source in Git.
- The bot reader must serve approved files without live image search or rendering. This branch does not deploy NIGHT/MSCC. Read `../mscc/AGENTS.md` before changing the running bot or its deployment.
