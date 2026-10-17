import fs from 'node:fs/promises';
import {fileURLToPath} from 'node:url';

// Shared by every collection. Finishes, popularity and drop rates never enter
// this scale. These are NIGHT editorial grades, not author-issued statistics.
export const POWER_SCALE = Object.freeze({
  id: 'night-power-v1',
  ranks: Object.freeze(['F', 'E', 'D', 'C', 'B', 'A', 'S', 'SS', 'SSS']),
  benchmarks: Object.freeze({
    F: 'Human baseline; no active supernatural combat power.',
    E: 'Enhanced physical ability, with limited reach and versatility.',
    D: 'Established superhuman combat ability with a limited toolkit.',
    C: 'Developed supernatural ability or a trained combat specialist.',
    B: 'Elite supernatural combatant with demonstrated offensive or defensive strength.',
    A: 'Exceptional mastery, speed, defence or special abilities, with meaningful limits.',
    S: 'Apex combatant capable of deciding large-scale conflicts.',
    SS: 'Transcendent combat or conceptual powers beyond the apex band, with documented limits.',
    SSS: 'Demonstrated world/reality-scale power; neither fame nor a divine title qualifies.'
  })
});

export function rankValue(rank) {
  return POWER_SCALE.ranks.indexOf(rank);
}

export function validatePower(power, card) {
  if (!power || power.scale !== POWER_SCALE.id || !['proposed', 'pending'].includes(power.status))
    throw Error(`Missing power assessment: ${card.id}`);
  if (power.status === 'pending' ? power.rank !== null : rankValue(power.rank) < 0)
    throw Error(`Invalid power grade: ${card.id}`);
  if (power.formId !== card.formId || power.artworkSHA256 !== card.source.sha256)
    throw Error(`Power assessment needs a form/artwork review: ${card.id}`);
  if (![power.scope, power.basis, power.limits].every(v => typeof v === 'string' && v.trim()))
    throw Error(`Incomplete power reasoning: ${card.id}`);
  if (!power.references?.length || power.references.some(url => {
    try { return new URL(url).protocol !== 'https:'; } catch { return true; }
  })) throw Error(`Missing power reference: ${card.id}`);
  return power;
}

export async function loadPowerAssessments(series, cards, pendingForms = []) {
  if (!/^[a-z][a-z0-9-]*$/.test(series)) throw Error('Invalid power series');
  const file = fileURLToPath(new URL(`../catalog/power/${series}.json`, import.meta.url));
  const data = JSON.parse(await fs.readFile(file, 'utf8'));
  if (data.scale !== POWER_SCALE.id || data.series !== series) throw Error('Power catalogue scale/series mismatch');
  const expected = new Set([...cards.map(c => c.id), ...pendingForms.map(c => c.id)]);
  for (const id of Object.keys(data.assessments)) if (!expected.has(id)) throw Error(`Unknown power identity: ${id}`);
  for (const card of cards) card.power = validatePower(data.assessments[card.id], card);
  for (const form of pendingForms) {
    const power = data.assessments[form.id];
    if (!power || power.scale !== POWER_SCALE.id || power.formId !== form.form ||
        (power.status === 'pending' ? power.rank !== null : rankValue(power.rank) < 0))
      throw Error(`Missing pending-form power assessment: ${form.id}`);
    form.power = power;
  }
  return data;
}

const number = n => String(Math.round(n * 100) / 100);
const xml = s => String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&apos;'}[c]));

/** Add the revised presentation without changing any archived renderer. */
export function powerPresentation(svg, card, design, {nameScale = .84} = {}) {
  validatePower(card.power, card);
  if (!Number.isFinite(nameScale) || nameScale < .7 || nameScale > 1) throw Error('Invalid title scale');
  let titles = 0;
  svg = svg.replace(/<text\b([^>]*)>([^<]*)<\/text>/g, (whole, attributes, content) => {
    if (/\bdata-name=|\bdata-text="name"/.test(attributes)) {
      titles++;
      attributes = attributes.replace(/\bfont-size="([\d.]+)"/, (_, size) => `font-size="${number(Number(size) * nameScale)}"`);
      attributes = attributes.replace(/\bletter-spacing="([\d.]+)"/, (_, size) => `letter-spacing="${number(Number(size) * nameScale)}"`);
      return `<text${attributes} data-title-scale="${nameScale}">${content}</text>`;
    }
    // The grade replaces the small right-hand header, rather than adding a
    // second competing label. Catalogue numbers in the footer remain intact.
    const x = Number(attributes.match(/\bx="([\d.]+)"/)?.[1]);
    const y = Number(attributes.match(/\by="([\d.]+)"/)?.[1]);
    if (x > 800 && y < 150 && !attributes.includes('data-power-rank')) return '';
    return whole;
  });
  if (titles !== 1) throw Error('Expected one main character title');
  // The preserved front still has two four-point decorative sparkles. Remove
  // those icons here as requested; leave the underlying archival code intact.
  if (['nocturne', 'tidelight'].includes(design)) {
    let fronts = 0;
    svg = svg.replace(/(<g data-depth="front"[^>]*>)([\s\S]*?)(<\/g>)/, (_, open, contents, close) => {
      fronts++;
      const cleaned = contents.replace(/<path\b[^>]*\bfill="url\(#[^"]*metal\)" opacity="\.9"\/>/, '');
      if (cleaned === contents) throw Error('Sparkle path changed; review the presentation adapter');
      return open + cleaned + close;
    });
    if (fronts !== 1) throw Error('Expected one foreground depth layer');
  }
  const p = `power-${card.id}-${design}-`;
  const tone = card.recipe?.powerBadgeTone?.[design];
  if (tone !== undefined && !['light', 'dark'].includes(tone)) throw Error('Invalid power badge tone');
  const light = tone ? tone === 'light' : ['tidelight', 'ivory'].includes(design);
  const rank = card.power.rank ?? '—';
  const ink = light ? '#173544' : '#f4f3e9';
  const x = 813, y = ['nocturne','tidelight'].includes(design) ? 78 : 44, w = 112, h = 72;
  const description = card.power.rank
    ? `NIGHT power rank ${rank}. ${card.power.scope}. ${card.power.basis}`
    : `Power rank awaiting form assessment. ${card.power.scope}`;
  const badge = `<defs>
    <clipPath id="${p}lens"><rect x="${x}" y="${y}" width="${w}" height="${h}" rx="22"/></clipPath>
    <filter id="${p}blur" filterUnits="userSpaceOnUse" x="${x-18}" y="${y-18}" width="${w+36}" height="${h+36}"><feGaussianBlur stdDeviation="5"/></filter>
    <linearGradient id="${p}glass" x1="0" y1="0" x2=".8" y2="1"><stop stop-color="${light?'#fff':'#071c28'}" stop-opacity="${light?.46:.32}"/><stop offset=".55" stop-color="${light?'#fff':'#102b39'}" stop-opacity="${light?.22:.18}"/><stop offset="1" stop-color="${light?'#d9edef':'#b8dce0'}" stop-opacity=".12"/></linearGradient>
    <linearGradient id="${p}edge" x1="0" y1="0" x2=".7" y2="1"><stop stop-color="${light?'#fff':'#e5f4ef'}" stop-opacity=".76"/><stop offset=".45" stop-color="${light?'#6f96a4':'#afd1d9'}" stop-opacity=".12"/><stop offset="1" stop-color="${light?'#fff':'#c8e4e7'}" stop-opacity=".4"/></linearGradient>
  </defs><g data-layer="power-rank" data-power-rank="${rank}" role="img" aria-label="${xml(description)}">
    <title>${xml(description)}</title>
    <g clip-path="url(#${p}lens)"><use href="#${p}scene" filter="url(#${p}blur)"/><rect x="${x}" y="${y}" width="${w}" height="${h}" fill="url(#${p}glass)"/></g>
    <rect x="${x+.6}" y="${y+.6}" width="${w-1.2}" height="${h-1.2}" rx="21.4" fill="none" stroke="url(#${p}edge)" stroke-width="1.2"/>
    <path d="M${x+24} ${y+2}h${w-48}" fill="none" stroke="${light?'#fff':'#edf7f4'}" stroke-opacity=".45" stroke-width=".7"/>
    <text x="${x+w/2}" y="${y+h/2+14}" text-anchor="middle" font-family="${['nocturne','tidelight'].includes(design)?'NightSans':design==='obsidian'?'AtelierSans':'NSans'},Arial,sans-serif" font-size="${rank.length>2?36:40}" font-weight="500" letter-spacing="1.2" fill="${ink}" stroke="${light?'#f1f7f3':'#06121b'}" stroke-opacity=".5" stroke-width=".6" paint-order="stroke fill">${rank}</text>
  </g>`;
  // Keep the existing scene as the lens's backdrop. The badge is a sibling,
  // never part of its own backdrop, so SVG use references cannot recurse.
  const boundary = svg.indexOf('</defs>');
  if (boundary < 0 || !svg.endsWith('</svg>')) throw Error('Unexpected SVG root structure');
  svg = svg.slice(0, boundary + 7) + `<g id="${p}scene">` + svg.slice(boundary + 7, -6) + '</g>' + badge + '</svg>';
  return svg.replace('<svg ', `<svg data-power-scale="${POWER_SCALE.id}" data-power-status="${card.power.status}" `);
}
