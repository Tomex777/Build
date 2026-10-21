import test from 'node:test';
import assert from 'node:assert/strict';
import {loadManhwaRoster} from '../src/manhwa.mjs';
import {loadVisualSources,manhwaGalleryHtml,validateVisualSources} from '../src/manhwa-gallery.mjs';
const roster=await loadManhwaRoster();
const visuals=await loadVisualSources(roster);
test('thirteen character-correct manhwa illustration studies exist, not blank roster tiles',()=>{
 assert.equal(visuals.entries.length,13);
 assert.equal(new Set(visuals.entries.map(x=>x.id)).size,13);
 assert.deepEqual(['sss-revival','infinite-mage','study-group'].map(s=>visuals.entries.filter(x=>x.series===s).length),[5,4,4]);
 assert.ok(visuals.entries.every(x=>x.image.startsWith('https://')));
 assert.ok(visuals.entries.every(x=>x.source.startsWith('https://')));
 assert.ok(validateVisualSources(visuals,roster));
});
test('showcase actually renders thirteen visibly illustrated collectible cards with frame overlays',()=>{
 const html=manhwaGalleryHtml({roster,visuals});
 assert.ok(html.includes('<img class="portrait-art portrait-main"'));
 assert.ok(html.includes('inner-rail'));
 assert.ok(html.includes('foil-sheen'));
 assert.ok(html.includes('Gong-ja × Raviel'));
 assert.ok(html.includes('Sirone × Amy'));
 assert.ok(html.includes('Gamin Yoon'));
 assert.ok(html.includes('Hanwool Phi'));
 assert.equal((html.match(/class="card-wrap"/g)||[]).length,13);
 assert.ok(html.includes('details='));
 assert.ok(html.includes('modal')||html.includes('<dialog'));
 assert.ok(html.includes('referrerpolicy="no-referrer"'));
 assert.ok(html.includes('82-character roster'));
 assert.ok(html.includes('No speculative power tier')||html.includes('no speculative power tier'));
});
test('a swapped image from a different universe cannot be given an arbitrary new identity',()=>{
 const fake=structuredClone(visuals);
 fake.entries[0].relatedEdition='gamin-yoon';
 assert.throws(()=>validateVisualSources(fake,roster),/Invalid visual edition identity/);
 const invalid=structuredClone(visuals);
 invalid.entries.find(x=>x.id==='sirone-amy').secondImage=null;
 assert.throws(()=>validateVisualSources(invalid,roster),/Two-source preview/);
});
