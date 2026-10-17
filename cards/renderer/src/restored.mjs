import {renderPortraitCard, setPortraitLight} from './portrait.mjs';

export const RESTORED_DESIGNS = ['nocturne', 'tidelight'];

// Remove one complete, nested SVG group. Fail loudly if the preserved renderer
// changes, instead of silently deleting an unrelated part of the illustration.
function removeGroup(svg, opening, description) {
  const start = svg.indexOf(opening);
  if (start < 0 || svg.indexOf(opening, start + 1) !== -1)
    throw new Error(`Expected one ${description}; review the restored adapter`);
  const groups = /<\/?g\b[^>]*>/g;
  groups.lastIndex = start;
  let depth = 0;
  for (let match; (match = groups.exec(svg));) {
    depth += match[0].startsWith('</') ? -1 : 1;
    if (depth === 0) return svg.slice(0, start) + svg.slice(groups.lastIndex);
  }
  throw new Error(`Unclosed ${description}`);
}

/**
 * The preserved depth design in its existing taller format. Nocturne's entire
 * engraved circular disc is removed, including its dark fill, rings and ticks.
 * Tidelight keeps its original pearl architecture. Both omit the old emblem.
 * Previous renderers, artwork placement and typography remain reproducible.
 */
export function renderRestoredCard(def, assets, options = {}) {
  if (!RESTORED_DESIGNS.includes(def.theme))
    throw new Error('Restored editions support Nocturne and Tidelight');
  const side = options.side ?? 'front';
  let svg = renderPortraitCard(def, assets, {...options, removeEmblem: true});
  if (def.theme === 'nocturne') {
    svg = removeGroup(svg, side === 'front'
      ? '<g><ellipse cx="525" cy="588"'
      : '<g opacity=".65"><g><ellipse', 'Nocturne circular backdrop');
  }
  return svg.replace('<svg ', `<svg data-restored="true" data-circular-disc="${def.theme === 'nocturne' ? 'removed' : 'not-applicable'}" `);
}

export function setRestoredLight(svg, light) {setPortraitLight(svg, light);}
