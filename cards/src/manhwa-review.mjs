import {validateArtLock,collectionSummary} from './manhwa.mjs';
const escape = value => String(value).replace(/[&<>"']/g, ch => ({
  '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'
}[ch]));
/** Flat searchable research *roster*, not a substitute for actual art cards.
 * All image-free items explicitly remain pending. */
export function researchReviewHtml(roster,lock) {
  validateArtLock(roster,lock);
  const groups=collectionSummary(roster,lock);
  const names=Object.fromEntries(roster.series.map(s=>[s.id,s.name]));
  const label={power:'Power',character:'Story',romance:'Romance'};
  const options=roster.series.map(s=>`<option value="${escape(s.id)}">${escape(s.name)}</option>`).join('');
  const rows=roster.editions.map(e=>{
    const art=Boolean(lock.editions[e.id]),spoiler=e.story.spoiler;
    return `<li data-series="${escape(e.series)}" data-family="${escape(e.family)}" data-spoiler="${spoiler}"
       data-search="${escape([e.title,e.form,e.family,e.subjects.join(' '),...e.aliases].join(' ').toLowerCase())}"
       ${spoiler?'hidden':''}>
      <div class="identity"><span class="eyebrow">${escape(names[e.series])} · ${escape(label[e.family])}</span>
        <h2>${escape(e.title)}</h2><p>${escape(e.form)}</p></div>
      <div class="right"><span class="badge">${art?'SOURCE PINNED':'ART PENDING'}</span>
        <span class="note">${escape(e.research.verification==='chapter-verified'?'EPISODE REVIEWED':'CHAPTER CHECK')}</span></div>
      <a href="${escape(e.research.evidence[0])}" target="_blank" rel="noopener noreferrer">Publisher ↗</a>
    </li>`;
  }).join('\n');
  const total=roster.editions.length,pinned=groups.reduce((a,g)=>a+g.artPinned,0);
  return `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>NIGHT — Manhwa Candidate Roster</title>
<style>
:root{color-scheme:dark;font-family:system-ui,-apple-system,Segoe UI,sans-serif;background:#0c0d10;color:#eceef3}
*{box-sizing:border-box}body{margin:0}header,main{max-width:1120px;margin:auto;padding:0 22px}
header{padding-top:64px;padding-bottom:32px;border-bottom:1px solid #303039}
.brand{font-size:13px;font-weight:750;letter-spacing:.28em;color:#bac1d0}
h1{font-size:clamp(33px,5vw,64px);letter-spacing:-.055em;line-height:1.04;margin:20px 0 10px;font-weight:760}
header p{color:#aab0bc;line-height:1.6;max-width:760px}
.stat{display:flex;gap:28px;flex-wrap:wrap;padding:18px 0;font-size:13px;color:#adb2be}
.stat b{color:#f6f5f8;font-size:21px;display:block}
.controls{display:flex;gap:10px;flex-wrap:wrap;padding:30px 0 18px;align-items:center}
input,select{font:inherit;background:#16171d;color:#f2f2f5;border:1px solid #333640;border-radius:7px;padding:13px;min-width:130px}
input[type=search]{flex:1;min-width:190px}
label{display:flex;align-items:center;gap:8px;white-space:nowrap;color:#bec1c9;font-size:13px}
label input{min-width:0;accent-color:#ddd}
ul{padding:0;margin:0 0 70px;list-style:none}
li{display:grid;grid-template-columns:minmax(0,1fr) 140px 90px;gap:12px;align-items:center;padding:19px 0;border-bottom:1px solid #292b33}
li[hidden]{display:none}
.eyebrow{font-size:11px;text-transform:uppercase;letter-spacing:.11em;color:#9b9fac}
h2{font-size:19px;letter-spacing:-.02em;margin:5px 0 0}
.identity p{margin:5px 0 0;font-size:13px;color:#a4a9b4}
.right{text-align:right;font-size:10px;letter-spacing:.05em}
.badge,.note{display:block;margin:5px 0;color:#e6ceb0}
.note{color:#8793ac}
a{font-size:12px;color:#d9dce5;text-decoration:none;text-align:right}
a:hover{text-decoration:underline}
footer{padding:20px 0 40px;color:#858a95;font-size:12px}
@media(max-width:560px){header{padding-top:36px}li{grid-template-columns:minmax(0,1fr) 120px;gap:4px}
li a{grid-column:1/-1;text-align:left;margin-top:8px}.controls{align-items:stretch}
select{flex:1}.stat{gap:18px}}
</style></head><body>
<header><div class="brand">NIGHT / MANHWA</div><h1>Candidate roster</h1>
<p>Research index, not finished collectible-card artwork. Every depicted form needs its chapter,
original transparent illustration, rights documentation, and full-size frame-break review.</p>
<div class="stat"><span><b>${total}</b>EDITION CANDIDATES</span>
<span><b>${roster.series.length}</b>MANHWA WORLDS</span>
<span><b>${pinned}</b>ART SOURCES PINNED</span></div></header>
<main><div class="controls">
<input id="query" type="search" placeholder="Search characters or aliases" aria-label="Search characters">
<select id="series" aria-label="Filter series"><option value="">All manhwa</option>${options}</select>
<select id="family" aria-label="Filter card family"><option value="">All families</option>
<option value="power">Power</option><option value="character">Story</option><option value="romance">Romance</option></select>
<label><input type="checkbox" id="spoilers"> Spoilers</label></div>
<p id="shown" aria-live="polite" style="font-size:12px;color:#aab0bc"></p><ul id="records">${rows}</ul>
<footer>Publisher / community sources support editorial research only. No illustration rights or power ranks are implied.</footer></main>
<script>
const query=document.querySelector('#query'),series=document.querySelector('#series'),
 family=document.querySelector('#family'),spoilers=document.querySelector('#spoilers'),
 rows=[...document.querySelectorAll('#records li')],shown=document.querySelector('#shown');
function update(){
 const q=query.value.trim().toLowerCase();let visible=0;
 for(const r of rows){
   r.hidden=Boolean((!spoilers.checked&&r.dataset.spoiler==='true')
      ||(series.value&&r.dataset.series!==series.value)
      ||(family.value&&r.dataset.family!==family.value)
      ||!r.dataset.search.includes(q));
   if(!r.hidden)visible++;
 }
 shown.textContent=visible+' of '+rows.length+' editions';
}
for(const el of [query,series,family,spoilers])el.addEventListener('input',update);
update();
</script></body></html>`;
}
