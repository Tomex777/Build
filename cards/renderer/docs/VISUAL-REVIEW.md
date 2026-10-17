# Visual review record

This review evaluated the rendered cards, not whether the source executed.

| Iteration | Observed weakness | Revision | Render evidence |
| --- | --- | --- | --- |
| 01 | Corner flourishes collided with the brand emblem and small footer credit. Verdant placed the face against the left crop. Nocturne left too much space above the figure. | Remove the redundant header emblem and ornamental curls; simplify stepped corners; move Verdant's focal x from .36 to .285; raise Nocturne's character by 35 master units. | `qa/iteration-01.png` |
| 02 | Cropping and spacing were improved, but the finish read mostly as a thin colored border. The title was visually flat. | Add faint vector foil contours through a face-safe mask; introduce a controlled metallic title gradient; strengthen the edition label slightly. | `qa/iteration-02.png` |
| 03 | Fine details were visible at full size and the primary identity survived small display sizes. | Keep the face clear, preserve the dark text field, and make the reflective gradient respond to pointer/keyboard light changes in the viewer. | `qa/iteration-03.png`, `qa/foil-light.gif` |
| 04 | The first release repeated the same framing and bottom fade. | Enlarge Nocturne’s silhouette; build a pearl field for Tidelight and an engraved botanical arch for Verdant; use a true italic title on Verdant. | `qa/iteration-04.png` |
| 05 | The first revision duplicated an orbital ring, let the scenic image interfere with the pearl title field, and left some leaves disconnected from their stems. | Remove the redundant ring, bring the pearl opacity forward, connect leaves to a continuous curved stem, and soften the foil mask. | `qa/iteration-05.png`, `qa/before-after.png` |

Full 2000 × 2800 fronts and reverses were inspected, followed by native 320 ×
448 and 360 × 504 browser renders. The final cards were also inspected as a
set, in the offline viewer, and in cropped detail views of the frame and text.

## Composition decisions

- **Nocturne:** the complete floating silhouette is retained. The orbital
  engraving establishes depth behind the figure without covering it. The
  lifted pose and clear dark field give the text breathing room.
- **Tidelight:** a wider scenic crop gives the pen and water stroke more space.
  A luminous pearl field carries dark navy type; the brighter material separates
  this card from the two dark compositions.
- **Verdant:** the arched picture field protects both eyes, the hand and the
  parasol. Connected engraved leaves, champagne metal and a true italic title
  extend the illustration’s botanical subject. The painted artwork is intact.

## At WhatsApp viewing size

The recognizable artwork, name, and edition remain the hierarchy. Fine
engraving and the tiny artist credit are deliberately detail-size content;
the complete credit is supplied in the caption files and on the reverse.
`qa/whatsapp-size-check.png` shows three cards at their actual 320-pixel width.

The sharing JPEGs have a 1600-pixel long edge and quality 94. A separate
720-pixel, quality-70 stress image checks readable names after stronger
compression; it is a simulation, not evidence of WhatsApp's current encoder.

## Verification scope

See `qa/verification.json` for actual browser/export checks and exact browser
version. Keyboard light control, edition selection, reverses, foil toggling,
offline use, mobile touch layouts, embedded SVG fonts, PNG downloads, safe text
margins, and output dimensions were checked. The live shader coordinates were compared with fresh SVG exports at three light positions. An export-in-progress was also tested while changing the selected card. Unit/integration tests cover
rights filtering, byte limits, source revalidation, crop coverage, integrity,
and manifest registration.

No message was sent through WhatsApp and no physical Android device was used.
The small-size evidence establishes image readability, not WhatsApp server
recompression behavior. The finish is a digital light simulation; print-ready
spot-foil separations and CMYK proofing are outside this release.

## Version 1.1 verification

13 unit/integration tests and 43 browser/export checks passed. The original
three cards remain in the before/after evidence; the updated source and all
six front/reverse SVGs are checked for exact alignment.
