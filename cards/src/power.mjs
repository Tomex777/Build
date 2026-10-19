import fs from 'node:fs/promises';
import {fileURLToPath} from 'node:url';

// Each universe is assessed independently. These are form-specific NIGHT
// editorial grades, not author-issued statistics or cross-universe equivalence.
export const POWER_SCALE = Object.freeze({
  id: 'night-universe-power-v1',
  scope: 'in-universe',
  ranks: Object.freeze(['F', 'E', 'D', 'C', 'B', 'A', 'S', 'SS', 'SSS', 'EX']),
  benchmarks: Object.freeze({
    F: 'Human baseline; no active supernatural combat power.',
    E: 'Enhanced physical ability, with limited reach and versatility.',
    D: 'Established superhuman combat ability with a limited toolkit.',
    C: 'Developed supernatural ability or a trained combat specialist.',
    B: 'Elite supernatural combatant with demonstrated offensive or defensive strength.',
    A: 'Exceptional mastery, speed, defence or special abilities, with meaningful limits.',
    S: 'Apex combatant capable of deciding large-scale conflicts.',
    SS: 'Transcendent combat or conceptual powers beyond the apex band, with documented limits.',
    SSS: 'Exceptional upper limit of this universe, assessed for this exact form and continuity.',
    EX: 'A documented exceptional form beyond the universe\'s SSS benchmark; a specific feat review is required.'
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
  if(!/^[a-z][a-z0-9-]*$/.test(power.universe??'')||power.universe!==card.universe)
    throw Error(`Power assessment belongs to another universe: ${card.id}`);
  if(!power.continuity?.trim())throw Error(`Missing power continuity: ${card.id}`);
  const feat=power.featReview;
  if(['SSS','EX'].includes(power.rank)&&(feat?.status!=='verified'||feat.universe!==power.universe||feat.continuity!==power.continuity||!feat.locator?.trim()||!feat.claim?.trim()||!feat.reference?.startsWith('https://')))
    throw Error(`Tier ${power.rank} needs specific verified feat evidence: ${card.id}`);
  if (power.formId !== card.formId || power.artworkSHA256 !== card.source.sha256)
    throw Error(`Power assessment needs a form/artwork review: ${card.id}`);
  if(power.review && (power.review.status!=='source-and-form-reviewed'||power.review.artworkSHA256!==card.source.sha256||!/^\d{4}-\d{2}-\d{2}$/.test(power.review.date)))
    throw Error(`Stale source/form review: ${card.id}`);
  if (![power.scope, power.basis, power.limits].every(v => typeof v === 'string' && v.trim()))
    throw Error(`Incomplete power reasoning: ${card.id}`);
  if (!power.references?.length || power.references.some(url => {
    try { return new URL(url).protocol !== 'https:'; } catch { return true; }
  })) throw Error(`Missing power reference: ${card.id}`);
  return power;
}

export function validateFormGrades(cards) {
  const forms=new Map();
  for(const card of cards){
    const key=card.power.universe+'/'+card.power.continuity+'/'+card.character+'/'+card.formId,previous=forms.get(key);
    if(previous && previous!==card.power.rank)throw Error(`Conflicting grade for the same form: ${key}`);
    forms.set(key,card.power.rank);
  }
}

export async function loadPowerAssessments(series, cards, pendingForms = []) {
  if (!/^[a-z][a-z0-9-]*$/.test(series)) throw Error('Invalid power series');
  const file = fileURLToPath(new URL(`../catalog/power/${series}.json`, import.meta.url));
  const data = JSON.parse(await fs.readFile(file, 'utf8'));
  if (data.scale !== POWER_SCALE.id || data.series !== series || data.universe!==series || data.scope!=='in-universe') throw Error('Power catalogue scale/series mismatch');
  const expected = new Set([...cards.map(c => c.id), ...pendingForms.map(c => c.id)]);
  for (const id of Object.keys(data.assessments)) if (!expected.has(id)) throw Error(`Unknown power identity: ${id}`);
  for (const card of cards) card.power = validatePower(data.assessments[card.id], card);
  validateFormGrades(cards);
  for (const form of pendingForms) {
    const power = data.assessments[form.id];
    if (!power || power.scale !== POWER_SCALE.id || power.universe!==series || !power.continuity?.trim() || power.formId !== form.form ||
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
    svg = svg.replace('M78 125H190M814 125H923', 'M78 125H190');
  }
  const p = `power-${card.id}-${design}-`;
  const tone = card.recipe?.powerBadgeTone?.[design];
  if (tone !== undefined && !['light', 'dark'].includes(tone)) throw Error('Invalid power badge tone');
  const light = tone ? tone === 'light' : ['tidelight', 'ivory'].includes(design);
  const rank = card.power.rank ?? '—';
  const x = 813, y = ['nocturne','tidelight'].includes(design) ? 78 : 44, w = 112, h = 72;
  const size = rank.length > 2 ? 42 : rank.length > 1 ? 50 : 56;
  const family = ['nocturne','tidelight'].includes(design) ? 'NightSans' : design === 'obsidian' ? 'AtelierSans' : 'NSans';
  const description = card.power.rank
    ? `NIGHT ${card.power.universe.toUpperCase()} form rank ${rank}. ${card.power.scope}. ${card.power.basis}`
    : `Power rank awaiting form assessment. ${card.power.scope}`;
  // Glass exists only inside the letter outlines. The copied artwork is
  // refracted through the glyphs, with translucent faces and bevel highlights.
  // The reserved area is layout metadata, never a visible plate or pill.
  const badge = `<defs>
    <text id="${p}glyph" data-rank-source="true" x="${x+w/2}" y="${y+h/2+19}" text-anchor="middle" font-family="${family},Arial,sans-serif" font-size="${size}" font-weight="500" letter-spacing="1.8">${rank}</text>
    <clipPath id="${p}letters"><use href="#${p}glyph"/></clipPath>
    <filter id="${p}refract" filterUnits="userSpaceOnUse" x="${x-16}" y="${y-16}" width="${w+32}" height="${h+32}">
      <feTurbulence type="fractalNoise" baseFrequency=".018 .024" numOctaves="1" seed="17" result="surface"/>
      <feDisplacementMap in="SourceGraphic" in2="surface" scale="7" xChannelSelector="R" yChannelSelector="G"/>
      <feGaussianBlur stdDeviation=".22"/>
    </filter>
    <filter id="${p}cast" x="-.2" y="-.2" width="1.4" height="1.6"><feDropShadow dx=".55" dy="1.2" stdDeviation=".65" flood-color="#03121c" flood-opacity="${light?.28:.58}"/></filter>
    <linearGradient id="${p}face" x1="0" y1="0" x2=".5" y2="1">
      <stop stop-color="${light?'#1d4b61':'#f1fbff'}" stop-opacity="${light?.48:.64}"/>
      <stop offset=".32" stop-color="${light?'#44768c':'#d2eaf1'}" stop-opacity="${light?.20:.24}"/>
      <stop offset=".48" stop-color="#f7ffff" stop-opacity=".65"/>
      <stop offset=".52" stop-color="${light?'#153f56':'#b7d9e4'}" stop-opacity="${light?.38:.12}"/>
      <stop offset="1" stop-color="${light?'#315c73':'#dff4fa'}" stop-opacity="${light?.48:.52}"/>
    </linearGradient>
    <linearGradient id="${p}edge" x1="0" y1="0" x2=".65" y2="1">
      <stop stop-color="${light?'#234b60':'#f3ffff'}" stop-opacity=".9"/>
      <stop offset=".38" stop-color="${light?'#769aab':'#b1d2df'}" stop-opacity=".35"/>
      <stop offset=".56" stop-color="#fff" stop-opacity=".9"/>
      <stop offset="1" stop-color="${light?'#1b4156':'#d3edf6'}" stop-opacity=".72"/>
    </linearGradient>
  </defs><g data-layer="power-rank" data-power-material="glass-letters" data-power-rank="${rank}" data-rank-x="${x}" data-rank-y="${y}" data-rank-width="${w}" data-rank-height="${h}" role="img" aria-label="${xml(description)}">
    <title>${xml(description)}</title>
    <g data-layer="rank-refraction" clip-path="url(#${p}letters)"><use href="#${p}scene" transform="translate(-3 -2)" filter="url(#${p}refract)"/></g>
    <use href="#${p}glyph" fill="none" stroke="${light?'#0c2d40':'#020f18'}" stroke-width="1.8" stroke-opacity="${light?.22:.64}" filter="url(#${p}cast)"/>
    <use data-rank-ink="true" href="#${p}glyph" fill="url(#${p}face)" stroke="url(#${p}edge)" stroke-width=".95" stroke-linejoin="round" paint-order="stroke fill"/>
    <use href="#${p}glyph" transform="translate(-.35 -.5)" fill="none" stroke="#f4ffff" stroke-opacity="${light?.44:.66}" stroke-width=".45"/>
  </g>`;
  // The glass lettering is a sibling of its backdrop, so use references
  // cannot recurse. The original illustration and frame layers stay intact.
  const boundary = svg.indexOf('</defs>');
  if (boundary < 0 || !svg.endsWith('</svg>')) throw Error('Unexpected SVG root structure');
  svg = svg.slice(0, boundary + 7) + `<g id="${p}scene">` + svg.slice(boundary + 7, -6) + '</g>' + badge + '</svg>';
  return svg.replace('<svg ', `<svg data-power-scale="${POWER_SCALE.id}" data-power-universe="${card.power.universe}" data-power-status="${card.power.status}" `);
}
