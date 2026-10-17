import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {loadPowerAssessments} from './power.mjs';

export const ROOT = fileURLToPath(new URL('../', import.meta.url));
export const DESIGNS = ['vanguard','ivory','voltage','crimson','emberglass','obsidian','tidelight','nocturne'];
export const digest = bytes => crypto.createHash('sha256').update(bytes).digest('hex');
export const normalise = value => String(value).normalize('NFKD').replace(/[\u0300-\u036f]/g,'').toLowerCase().replace(/[^a-z0-9]+/g,' ').trim();
export function stable(value) {
  if (Array.isArray(value)) return value.map(stable);
  if (value && typeof value === 'object') return Object.fromEntries(Object.keys(value).sort().map(k=>[k,stable(value[k])]));
  return value;
}
export function inside(relative, root=ROOT) {
  const full = path.resolve(root, relative);
  if (path.isAbsolute(relative) || !full.startsWith(path.resolve(root)+path.sep)) throw Error('Path must stay inside the card library');
  return full;
}
export async function atomic(file, bytes) {
  await fs.mkdir(path.dirname(file), {recursive:true});
  await fs.writeFile(file+'.pending', bytes);
  await fs.rename(file+'.pending', file);
}
export async function loadCatalogue(series='bleach') {
  if (!/^[a-z][a-z0-9-]*$/.test(series)) throw Error('Invalid series');
  const catalogue = JSON.parse(await fs.readFile(inside(`catalog/${series}.json`),'utf8'));
  const ids = new Set(), numbers = new Set();
  for (const card of catalogue.cards) {
    if (!/^[a-z][a-z0-9-]{0,70}$/.test(card.id) || ids.has(card.id)) throw Error('Invalid or duplicate card identity');
    ids.add(card.id);
    if (!Number.isInteger(card.number) || card.number<1 || card.number>9999 || numbers.has(card.number)) throw Error('Invalid stable catalogue number');
    numbers.add(card.number);
    if (![card.character,card.name,card.title,card.form,card.formId].every(x=>typeof x==='string'&&x.trim()) || card.title.length>36) throw Error(`Incomplete identity: ${card.id}`);
    if (!card.designs?.length || card.designs.some(d=>!DESIGNS.includes(d))) throw Error(`Invalid designs: ${card.id}`);
    const s=card.source, r=card.recipe;
    if (!/^[a-f0-9]{64}$/.test(s?.sha256) || !s.rights || !s.usage || !s.page?.startsWith('https://') || !s.image?.startsWith('https://')) throw Error(`Missing artwork provenance: ${card.id}`);
    inside(s.localPath);
    const [x,y,w,h]=r?.crop??[];
    if (![x,y,w,h].every(Number.isInteger) || x<0 || y<0 || w<1 || h<1 || x+w>s.width || y+h>s.height) throw Error(`Invalid artwork crop: ${card.id}`);
    if (!r.focal?.every(n=>Number.isFinite(n)&&n>=0&&n<=1)) throw Error(`Missing focal point: ${card.id}`);
  }
  await loadPowerAssessments(series,catalogue.cards,catalogue.pendingForms);
  return catalogue;
}
export async function rendererDigest() {
  const files=[];
  for (const folder of ['renderer/src','src']) for (const n of (await fs.readdir(inside(folder))).sort()) if(n.endsWith('.mjs')) files.push(`${folder}/${n}`);
  files.push('renderer/examples/cards.json','package-lock.json');
  for (const n of (await fs.readdir(inside('renderer/assets/fonts'))).sort()) if(n.endsWith('.ttf')) files.push(`renderer/assets/fonts/${n}`);
  const hashes=[];
  for (const name of files.sort()) hashes.push([name,digest(await fs.readFile(inside(name)))]);
  return digest(JSON.stringify(hashes));
}
export function reviewFingerprint(card,design,renderer,width=3000) {
  return digest(JSON.stringify(stable({id:card.id,number:card.number,character:card.character,name:card.name,title:card.title,form:card.form,formId:card.formId,source:card.source,recipe:card.recipe,power:card.power,design,renderer,width})));
}
export function findEntries(entries,query,{form,design,rank}={}) {
  const q=normalise(query);
  return entries.filter(c=>[c.id,c.name,c.title,c.character,...c.aliases??[]].some(v=>normalise(v)===q)&&(!form||[c.form,c.formId].some(v=>normalise(v)===normalise(form)))&&(!design||c.design===design)&&(!rank||c.power?.rank===String(rank).toUpperCase()));
}
