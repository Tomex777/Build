export const categoryMeta = [
  ["reveals","Reveals","Mask, wipe, draw-on and structured appearances"],
  ["physical","Physical motion","Weight, spring, inertia and settling"],
  ["depth","Depth / 3D","Perspective, camera and layered z-motion"],
  ["typography","Typography","Wordmark and letter choreography"],
  ["logo","Logo","Night mark assembly and icon-specific motion"],
  ["shadow","Shadow-driven","Motion led by cast and contact shadows"],
  ["dark","Dark / Night","Low-light reveals without turning everything neon"],
  ["effects","Effects","Focus, trails, scans, particles and reconstruction"],
  ["micro","Microinteractions","Hover, press, loading and navigation details"],
  ["portfolio","Portfolio","Future portfolio-specific experiments"],
  ["app","App","Potential Night in-app motion concepts"]
];

const sets = {
  reveals: [
    ["Shadow Fade Reveal","Shadow arrives first; the lockup resolves from low contrast."],
    ["Iris Aperture","A circular aperture opens around the Night mark."],
    ["Vertical Curtain","Two opaque curtains part vertically around the brand."],
    ["Dual Slice","Top and bottom slices meet into a complete lockup."],
    ["Center Wipe","A narrow center strip expands to reveal the subject."],
    ["Corner Peel","The brand uncovers diagonally like a lifted opaque layer."],
    ["Line Draw Arrival","The bar draws first, then the dome and wordmark land."],
    ["Segmented Mask","Multiple rectangular windows reveal the lockup in sequence."],
    ["Bar Then Dome","The horizontal Night bar establishes the baseline before the dome."],
    ["Word Through Window","NIGHT travels behind a narrow mask and settles beside the mark."],
    ["Soft Focus Reveal","Form exists early but focus and contrast resolve late."],
    ["Underline Sweep","A moving baseline exposes the wordmark while the icon settles."]
  ],
  physical: [
    ["Soft Impact","A short fall ends in a restrained squash and contact shadow."],
    ["Spring Rise","The lockup rises with one practical spring overshoot."],
    ["Magnetic Snap","Logo and wordmark begin apart and snap into alignment."],
    ["Pendulum Settle","The mark swings from an upper pivot and damps naturally."],
    ["Weighted Drop","A heavy vertical drop lands without cartoon bouncing."],
    ["Float Catch","The mark drifts upward, then catches into a firm baseline."],
    ["Inertia Slide","Fast horizontal travel eases into position with a small counter-shift."],
    ["Elastic Compress","The lockup compresses, releases, and settles at full scale."],
    ["Corner Tether","The logo is pulled from a corner as if attached to a soft tether."],
    ["Orbit Settle","The mark arcs around the wordmark and docks at the left."],
    ["Rubber Band Join","Wordmark letters stretch apart, then contract into normal tracking."],
    ["Gravity Flip","A short forward flip ends with a weighted face-on landing."]
  ],
  depth: [
    ["Perspective Settle","A tilted plane rotates to face the camera while its shadow tightens."],
    ["Camera Push","The virtual camera pushes through darkness toward a stable logo."],
    ["Layered Parallax","Shadow, icon and type travel at different depths before aligning."],
    ["Card Turn","An opaque Night card turns from edge-on to frontal."],
    ["Shadow Extrusion","A long pseudo-extrusion collapses into a shallow raised mark."],
    ["Depth Stack","Three offset layers collapse into a single crisp logo."],
    ["Emboss Press","The logo presses inward, then releases as a shallow emboss."],
    ["Raised Tile","The mark lifts as a physical tile with contact separation."],
    ["Z-Slice Assemble","Depth-separated slices slide along z-space and recombine."],
    ["Perspective Skew","A side-skewed wordmark corrects while the logo remains anchored."],
    ["Tunnel Pull","Nested dark frames pull backward to reveal Night at the focal plane."],
    ["Fold To Face","Two rigid halves fold flat into one brand surface."]
  ],
  typography: [
    ["Letter Stagger","N I G H T rise independently in a tight stagger."],
    ["Tracking Bloom","The word begins compressed and opens to normal tracking."],
    ["Compressed Expand","Horizontally compressed glyphs expand without moving the logo."],
    ["Baseline Rise","Letters lift from below a clipped baseline."],
    ["Split Word","NIG and HT approach from opposing sides and lock together."],
    ["Vertical Glyph Reveal","Each letter reveals bottom-to-top through its own mask."],
    ["Letter Flip","Letters rotate around the y-axis one after another."],
    ["Wave Settle","A single wave passes across the baseline and disappears."],
    ["Type On","Letters appear with a restrained cursor-like cadence."],
    ["Alternating Drop","Odd and even letters enter from opposite vertical directions."],
    ["Kerning Snap","Loose glyph spacing snaps inward in two measured phases."],
    ["Word Weight Pulse","The word briefly gains visual weight through shadow and scale, then normalizes."]
  ],
  logo: [
    ["Icon Assemble","Dome and bar enter as separate pieces and register precisely."],
    ["Dome Drop Bar Lock","Dome descends while the bar slides laterally into its final slot."],
    ["Split Rejoin","The Night mark divides into left/right halves and rejoins."],
    ["Fold Open","The logo opens from a narrow center fold."],
    ["Pulse Once","One controlled scale pulse adds presence without a looping bounce."],
    ["Shadow Emerge","The icon rises directly out of its own dark silhouette."],
    ["Line Trace","A bright tracing edge describes the mark before the fill appears."],
    ["Half Turn","The icon rotates 180 degrees around y and stops face-on."],
    ["Bar Slide Dome Follow","The bar becomes the motion leader and the dome follows its vector."],
    ["Compression Release","The mark is vertically compressed, then regains its original geometry."],
    ["Quarter Pieces","Four clipped quadrants move inward and reconstruct the icon."],
    ["Logo Blink In","A fast opacity cut is followed by a slower depth settle."]
  ],
  shadow: [
    ["Shadow First","Cast shadow animates in before the visible logo."],
    ["Shadow Lift","Contact shadow softens as the brand physically rises."],
    ["Directional Sweep","A light direction change moves the shadow across the surface."],
    ["Long Shadow Retract","A long flat shadow retracts until only subtle depth remains."],
    ["Contact Bounce","The shadow squashes on impact while the icon performs one tiny rebound."],
    ["Shadow Stretch","The brand stays mostly still while its shadow stretches and returns."],
    ["Silhouette Reveal","A dark silhouette resolves into the illuminated mark."],
    ["Twin Shadow Merge","Two opposing shadows converge into one centered contact shadow."],
    ["Shadow Pivot","The cast shadow rotates around a fixed logo as light direction changes."],
    ["Soft Pool Rise","A diffuse shadow pool appears, then the mark rises from it."],
    ["Edge Shadow Crawl","A narrow edge shadow travels around the logo perimeter."],
    ["Offset Return","The logo and its shadow begin misregistered, then line up."]
  ],
  dark: [
    ["Darkness To Form","The mark is revealed only by increasing local contrast."],
    ["Eclipse Reveal","An opaque disk passes across the logo like an eclipse."],
    ["Rim Wake","A restrained rim light travels around the Night mark."],
    ["Vignette Open","The visible area grows outward from a tight dark vignette."],
    ["Sparse Stars Gather","A few subtle points converge and disappear as the logo forms."],
    ["Blackout Cut","A hard blackout cut is followed by a very short dimensional settle."],
    ["Horizon Glow","A low horizon line illuminates the underside of the mark."],
    ["Moon Edge Pass","A narrow curved highlight crosses the dome and fades."],
    ["Deep Fade Up","Near-black forms separate gradually into readable layers."],
    ["Night Pulse","The surrounding darkness pulses once while the logo stays controlled."],
    ["Dim To Sharp","A dim soft version cross-resolves into a crisp face."],
    ["Halo Collapse","A broad muted halo contracts tightly around the mark and vanishes."]
  ],
  effects: [
    ["Blur To Focus","Strong blur clears while scale and contrast settle."],
    ["Afterimage Catchup","Two faint trailing copies catch the real mark and disappear."],
    ["Chromatic Recombine","A restrained RGB split recombines into neutral white."],
    ["Trail Collapse","A short motion trail compresses into the final position."],
    ["Grain Materialize","Fine grain briefly reveals the silhouette before fading away."],
    ["Scan Reveal","A thin scan band exposes the logo from top to bottom."],
    ["Liquid Mask","An organic clip-path expands and smooths into the full mark."],
    ["Particle Rebuild","Small monochrome particles converge into the brand lockup."],
    ["Echo Scale","Two faint scale echoes collapse into the main lockup."],
    ["Smear Resolve","Horizontal motion smear sharpens as movement stops."],
    ["Pixel Cluster","Blocky cells resolve into the smooth logo shape."],
    ["Ripple Focus","A circular ripple passes through the stage and leaves the mark crisp."]
  ],
  micro: [
    ["Hover Lift","A restrained hover lift with deeper contact shadow."],
    ["Press Sink","Press state moves inward and tightens the shadow."],
    ["Selected Lock","Selection adds a short rim lock and scale settle."],
    ["Loading Orbit","A minimal orbiting dot tracks the logo during loading."],
    ["Card Open","A compact card expands into a focused surface."],
    ["Card Close","Focused surface contracts back into its card footprint."],
    ["Tab Underline","An underline accelerates, overshoots, and anchors beneath NIGHT."],
    ["Toggle Snap","A small control snaps between states with a depth change."],
    ["Hold Confirm","A perimeter progress trace completes and locks the logo."],
    ["Focus Ring","Keyboard focus produces a short non-neon rim expansion."]
  ],
  portfolio: [
    ["Hero Entrance","Night hero lockup enters with depth and a quiet camera settle."],
    ["Project Hover Depth","A project card gains layered depth on hover."],
    ["Project Card Open","Card geometry expands toward a project detail view."],
    ["Screenshot Curtain","An opaque curtain exposes a Night screenshot frame."],
    ["Phone Mockup Rise","A phone mockup rises from a grounded shadow."],
    ["Title Track In","Project title tracking contracts while the device settles."],
    ["Scroll Reveal Stack","Several project layers reveal in a scroll-like stagger."],
    ["Project Crossfade","Outgoing project depth recedes while Night advances."],
    ["Back Navigation Fold","The project surface folds back into its originating card."],
    ["Thumbnail Focus","A thumbnail sharpens and enlarges into the primary project visual."]
  ],
  app: [
    ["App Launch","Launch mark emerges quickly from a grounded dark surface."],
    ["Splash Settle","Splash logo performs a short depth settle before content appears."],
    ["Chat Open","Chat surface rises beneath a stationary Night header mark."],
    ["Message Arrival","A new message bubble slides and settles with minimal elasticity."],
    ["Tab Change","Active tab indicator transfers with a compact shared-axis motion."],
    ["Sheet Open","A settings sheet rises with shadow separation and a soft stop."],
    ["Send Flight","A send glyph accelerates forward and hands off to a new message bubble."],
    ["AI Thinking","Three points use a non-bouncy traveling emphasis around the Night mark."],
    ["Image Open","An image tile expands toward a viewer while surrounding UI recedes."],
    ["Viewer Return","Viewer contracts back to its message tile with preserved direction."],
    ["Voice Call Connect","Call surface deepens while a single ring resolves around the mark."],
    ["Voice Call End","The ring collapses and the call surface recedes cleanly."]
  ]
};

const sceneMaps = {
  portfolio: ["hero","project","project","screenshot","phone","phone","stack","project","project","screenshot"],
  app: ["launch","launch","chat","message","tabs","sheet","send","thinking","viewer","viewer","call","call"]
};

let n = 1;
export const catalog = [];
for (const [category, items] of Object.entries(sets)) {
  items.forEach((entry, variant) => {
    catalog.push({
      id: "N" + String(n++).padStart(3,"0"),
      name: entry[0],
      description: entry[1],
      category,
      variant,
      scene: sceneMaps[category]?.[variant] || (category === "typography" ? "word" : "brand")
    });
  });
}
