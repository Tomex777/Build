import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';

const ROOT = fileURLToPath(new URL('../', import.meta.url));
const FAMILIES = new Set(['power', 'character', 'romance']);
const FINISHES = new Set(['vanguard','ivory','voltage','crimson','emberglass','obsidian','tidelight','nocturne']);
const hex64 = /^[0-9a-f]{64}$/;
const slug = /^[a-z][a-z0-9-]{1,80}$/;
const nonempty = x => typeof x === 'string' && x.trim().length > 0;
const safeURL = value => {
  try { const url = new URL(value); return url.protocol === 'https:' && !url.username && !url.password; }
  catch { return false; }
};
export const sha256 = input => createHash('sha256').update(input).digest('hex');

/** The roster can contain pending editions; a card becomes renderable only
 * after separately pinned, inspected source art is supplied. */
export function validateManhwaRoster(data) {
  if (data?.version !== 1 || !Array.isArray(data.series) || !Array.isArray(data.editions)
    || data.series.length < 1 || data.editions.length < 1) throw Error('Invalid NIGHT manhwa roster');
  const series = new Map(), editions = new Set();
  for (const s of data.series) {
    if (!slug.test(s.id) || series.has(s.id) || !nonempty(s.name) || s.continuity !== 'webcomic'
      || !safeURL(s.source) || !nonempty(s.rights)
      || !['combat-and-story','character-story-only'].includes(s.mode))
      throw Error('Invalid or duplicate manhwa series');
    series.set(s.id, s);
  }
  for (const card of data.editions) {
    const s = series.get(card.series);
    if (!slug.test(card.id) || editions.has(card.id) || !s || !FAMILIES.has(card.family)
      || !nonempty(card.title) || !nonempty(card.form)
      || !Array.isArray(card.subjects) || !card.subjects.length || card.subjects.some(x => !nonempty(x))
      || !nonempty(card.story?.descriptor) || !nonempty(card.story?.role)
      || card.story.source !== s.source || typeof card.story.spoiler !== 'boolean')
      throw Error('Invalid manhwa edition identity: '+card.id);
    editions.add(card.id);
    if (!Array.isArray(card.aliases) || card.aliases.some(x=>!nonempty(x))
      || new Set(card.aliases.map(x=>x.trim().toLowerCase())).size!==card.aliases.length)
      throw Error('Invalid character name aliases: '+card.id);
    const research=card.research;
    if (!research || research.evidenceType!=='publisher-series-plus-community-character-index'
      || !Array.isArray(research.evidence) || research.evidence.length<2
      || research.evidence[0]!==s.source || research.evidence.some(url=>!safeURL(url))
      || !['candidate-not-confirmed','chapter-verified'].includes(research.verification))
      throw Error('Missing manhwa source research: '+card.id);
    if (research.verification==='candidate-not-confirmed') {
      if (research.webcomicAppearance!=='requires-chapter-check'||research.episodeEvidence)
        throw Error('Unconfirmed manhwa edition cannot claim adaptation evidence: '+card.id);
    } else {
      const episode=research.episodeEvidence;
      if (research.webcomicAppearance!=='confirmed-in-webcomic'
        || !Number.isInteger(episode?.number)||episode.number<1
        || !safeURL(episode?.url)||!nonempty(episode?.reviewer)
        || !/^\\d{4}-\\d{2}-\\d{2}$/.test(episode?.reviewDate??''))
        throw Error('Chapter-verified manhwa claim needs reviewer and episode proof: '+card.id);
    }
    if (s.mode === 'character-story-only' && card.family === 'power')
      throw Error('Do not invent a power ladder for '+s.name);
    if (card.family === 'power') {
      if (card.power?.status !== 'pending-feat-review' || card.power.tier !== null
        || card.power.scope !== 'in-universe-only' || card.power.continuity !== s.continuity
        || card.power.reference !== s.source || 'bond' in card)
        throw Error('Manhwa power cannot be fabricated: '+card.id);
    } else if ('power' in card) throw Error('Non-power card must not have a tier: '+card.id);
    if (card.family === 'romance') {
      if (card.subjects.length !== 2 || card.bond?.grade !== null
        || !nonempty(card.bond?.focus) || !nonempty(card.bond?.relationshipStatus))
        throw Error('Romance must be a real duo without combat ranking: '+card.id);
    } else if ('bond' in card) throw Error('Non-romance card cannot carry a romance grade: '+card.id);
    if (card.artwork?.status !== 'awaiting-verified-cutout' || card.artwork.provenance !== null
      || card.artwork.usage !== 'reference-only' || card.artwork.licensedForDistribution !== false
      || card.artwork.sourcePage !== s.source
      || JSON.stringify(card.artwork.subjectSlots) !== JSON.stringify(card.subjects))
      throw Error('Unreviewed manhwa art must stay pending: '+card.id);
    if (card.design?.family !== 'NIGHT' || !FINISHES.has(card.design.finish)
      || card.design.breakoutRequired !== true
      || JSON.stringify(card.design.renderSize) !== '[3000,4800]'
      || JSON.stringify(card.design.phoneProof) !== '[320,512]')
      throw Error('Manhwa must use the shared NIGHT renderer: '+card.id);
  }
  return data;
}
export async function loadManhwaRoster(file = path.join(ROOT,'catalog/manhwa-roster.json')) {
  return validateManhwaRoster(JSON.parse(await fs.readFile(file,'utf8')));
}

/** A source lock can never be a guessed URL or an AI-derived stand-in.
 * The legacy renderer accepts one transparent source image per card.
 * Two-person romance editions require an explicit, provenance-preserving
 * dual-layer composition and cannot silently be flattened here. */
export function validateArtLock(roster, lock) {
  validateManhwaRoster(roster);
  if (lock?.version !== 1 || lock.collection !== roster.id || !lock.editions
    || typeof lock.editions !== 'object' || Array.isArray(lock.editions))
    throw Error('Invalid manhwa artwork lock');
  const byId = new Map(roster.editions.map(c => [c.id,c]));
  for (const [id,art] of Object.entries(lock.editions)) {
    const card = byId.get(id);
    if (!card) throw Error('Unknown locked manhwa edition: '+id);
    if (card.subjects.length !== 1) throw Error('Two-subject romance requires separately pinned layers: '+id);
    if (art.subject !== card.subjects[0] || art.form !== card.form || !safeURL(art.page)
      || !safeURL(art.image) || !hex64.test(art.sha256)
      || !Number.isInteger(art.width) || !Number.isInteger(art.height)
      || art.width < 500 || art.height < 900 || !nonempty(art.credit)
      || !nonempty(art.rights) || art.usage !== 'reference-only'
      || art.status !== 'source-pinned-candidate' || art.transparencyVerified !== true
      || !/^artwork\/manhwa\/[a-z0-9-/]+\.(png|webp)$/i.test(art.localPath)
      || art.localPath.includes('..'))
      throw Error('Invalid pinned art identity / provenance for '+id);
    const [x,y,w,h] = art.crop ?? [];
    if (![x,y,w,h].every(Number.isInteger) || x<0 || y<0 || w<1 || h<1
      || x+w>art.width || y+h>art.height
      || !Array.isArray(art.focal) || art.focal.length!==2
      || art.focal.some(n=>!Number.isFinite(n)||n<0||n>1))
      throw Error('Invalid pinned art crop / focal point for '+id);
  }
  return lock;
}

/** Produces an ordinary legacy renderer input only with a valid pinned image.
 * This does not grant visual approval or distribution rights. */
export function toRendererCard(edition, art, number) {
  if (edition.subjects.length !== 1 || !art || art.subject !== edition.subjects[0]
    || art.form !== edition.form || !hex64.test(art.sha256))
    throw Error('Cannot render missing or wrong-form manhwa illustration');
  return {
    id: edition.id, number, character: edition.subjects[0].toLowerCase().replace(/[^a-z0-9]+/g,'-').replace(/^-|-$/g,''),
    name: edition.title, title: edition.title.slice(0,36), form: edition.form,
    formId: edition.id, aliases: [...new Set([edition.title,...edition.subjects,...(edition.aliases??[])])],
    designs: [edition.design.finish], status:'candidate',
    source: {page:art.page,image:art.image,sha256:art.sha256,width:art.width,height:art.height,
      localPath:art.localPath,artist:art.credit,credit:art.credit,rights:art.rights,usage:'reference-only'},
    recipe: {crop:art.crop,focal:art.focal,bottomPadding:0.28}
  };
}
export async function verifyLocalSource(art, root = ROOT) {
  const full = path.resolve(root, art.localPath);
  if (!full.startsWith(path.resolve(root)+path.sep)) throw Error('Art path leaves project directory');
  const bytes = await fs.readFile(full);
  if (sha256(bytes) !== art.sha256) throw Error('Pinned source-byte checksum mismatch');
  // The existing artwork acquisition routine additionally checks native
  // dimensions and actual alpha pixels before any SVG is composed.
  return true;
}

export function collectionSummary(roster, lock = {version:1,collection:roster.id,editions:{}}) {
  validateArtLock(roster,lock);
  return roster.series.map(series => ({
    series:series.id,name:series.name,
    total:roster.editions.filter(c=>c.series===series.id).length,
    power:roster.editions.filter(c=>c.series===series.id&&c.family==='power').length,
    character:roster.editions.filter(c=>c.series===series.id&&c.family==='character').length,
    romance:roster.editions.filter(c=>c.series===series.id&&c.family==='romance').length,
    artPinned:roster.editions.filter(c=>c.series===series.id&&lock.editions[c.id]).length
  }));
}
