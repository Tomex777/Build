# Night Motion Lab

Experimental browser-only motion laboratory for the Night brand.

This does **not** modify Night's production Android UI and does **not** modify the portfolio. It lives in its own directory and branch so motion ideas can be judged before anything is integrated.

## Run

The lab has no framework or build step.

From this directory:

\`\`\`bash
python3 -m http.server 8080
\`\`\`

Then open \`http://localhost:8080\`.

Opening \`index.html\` directly also works in modern browsers that allow local ES modules, but a tiny local HTTP server is more reliable.

## What is included

- 128 motion concepts with stable IDs \`N001\` through \`N128\`
- Categories: Reveals, Physical motion, Depth / 3D, Typography, Logo, Shadow-driven, Dark / Night, Effects, Microinteractions, Portfolio and App
- Actual Night vector mark translated from \`whatsapp-ai-android/app/src/main/res/drawable/ic_night.xml\`
- Global play, pause, replay, loop and speed controls
- Per-card replay and loop controls
- Category filters and text search
- LIKE / MAYBE / NO decisions stored in \`localStorage\`
- Selected and Likes views
- Copy + text-file export of the shortlist
- Comparison mode for 2–4 synchronized concepts
- Nine static depth/shadow studies
- Responsive phone/tablet/desktop layout
- IntersectionObserver pausing of offscreen cards
- Reduced-motion detection with an explicit lab override

## Stable animation IDs

IDs are generated in catalog order and should be treated as stable references.

Example shortlist:

\`\`\`
N017 — Weighted Drop — LIKE
N043 — Vertical Glyph Reveal — MAYBE
N071 — Edge Shadow Crawl — LIKE
\`\`\`

Do not reorder the existing catalog if other chats have begun referencing IDs. Add new concepts at the end of a category only if you are also intentionally accepting an ID migration; otherwise append a new category or add explicit IDs.

## Favorites / shortlist persistence

Ratings are stored under:

\`\`\`
night-motion-lab-v1
\`\`\`

in browser \`localStorage\`.

The exported text file contains all LIKE and MAYBE items. NO is intentionally excluded from the shortlist export.

## Source organization

- \`index.html\` — semantic lab shell and dialogs
- \`styles.css\` — opaque dark surfaces, depth studies, responsive layout
- \`app.js\` — gallery state, persistence, filtering, comparison and controls
- \`animations/catalog.js\` — concept names, descriptions, categories and stable ordering
- \`animations/engine.js\` — reusable scene renderer and category-specific motion recipes
- \`assets/night-logo.svg\` — browser translation of the real Night Android vector mark

## Adding another motion

1. Add a new name/description entry in \`animations/catalog.js\`.
2. Add a genuinely new motion recipe branch for its variant in \`animations/engine.js\`.
3. If the concept needs a new scene type, add it to \`sceneMaps\` and the scene styling in \`styles.css\`.
4. Confirm the card pauses when offscreen and respects global speed / loop controls.
5. Do not count a duration-only, direction-only or tiny translate change as a new concept.

## Replacing the logo

The current SVG is derived from the real Night vector already present in the Night Android project. If the production mark changes later, replace only:

\`assets/night-logo.svg\`

and the inline SVG constant at the top of:

\`animations/engine.js\`

Keep the CSS class hooks \`.logo-dome\`, \`.logo-bar\`, and \`.logo-bg\` if you want icon-part animation recipes to keep working.

## Performance notes

Only cards near the viewport are active. IntersectionObserver pauses offscreen cards. Most motion uses the Web Animations API and transform/opacity/clip/filter properties; there is no heavy animation framework.

## Integration rule

This lab is for selection only. Do not copy a chosen animation into Night or the portfolio until the user has reviewed the gallery and explicitly picked the desired IDs.
