import fs from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
const here=path.dirname(fileURLToPath(import.meta.url));
const htmlEscape=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const validHttps=s=>{try{return new URL(s).protocol==='https:';}catch{return false;}};
export function validateVisualSources(data,roster) {
 if(data?.version!==1||!Array.isArray(data.entries)||data.entries.length<3)throw Error('Invalid visual preview manifest');
 const seriesIds=new Set(roster.series.map(s=>s.id));
 const byId=new Map(roster.editions.map(e=>[e.id,e]));
 const seen=new Set();
 for(const item of data.entries){
  const id=item.relatedEdition||item.id;
  const edition=byId.get(id);
  if(!edition||!seriesIds.has(item.series)||edition.series!==item.series||!item.id||seen.has(item.id))throw Error('Invalid visual edition identity '+item.id);
  seen.add(item.id);
  for(const field of ['title','edition','finish','color','glow','tag','credit','family','alt']){
   if(typeof item[field]!=='string'||!item[field])throw Error('Missing visual preview field '+field);
  }
  if(!validHttps(item.image)||!validHttps(item.source)||item.secondImage&&!validHttps(item.secondImage))throw Error('Unsafe visual reference URL');
  if(!['power','character','romance'].includes(item.family))throw Error('Invalid visual preview family');
  if(item.family==='romance'&&edition.family!=='romance')throw Error('Romance requires matching duo edition');
  if(!['central','pair','separate-pair'].includes(item.shape))throw Error('Unknown visual layout');
  if(item.shape==='separate-pair'&&!item.secondImage)throw Error('Two-source preview requires two images');
 }
 return data;
}
export async function loadVisualSources(roster,file=path.join(here,'../catalog/manhwa-visual-preview.json')){
 return validateVisualSources(JSON.parse(await fs.readFile(file,'utf8')),roster);
}
export function manhwaGalleryHtml({roster,visuals,rosterUrl='./roster.html'}){
 validateVisualSources(visuals,roster);
 const total=roster.editions.length;
 const counts=Object.fromEntries(roster.series.map(s=>[s.id,roster.editions.filter(e=>e.series===s.id).length]));
 const title={ 'sss-revival':'SSS-Class Revival Hunter', 'infinite-mage':'The Infinite Mage', 'study-group':'Study Group'};
 const cards=visuals.entries.map((item,index)=>{
  const i=index+1;
  const duo=item.shape==='separate-pair';
  const img=htmlEscape(item.image),second=htmlEscape(item.secondImage??'');
  const shape=htmlEscape(item.shape);
  return `<article class="card-wrap" data-universe="${htmlEscape(item.series)}" data-family="${htmlEscape(item.family)}" data-idx="${index}">
    <button class="open-card" type="button" aria-label="Enlarge ${htmlEscape(item.title)} — ${htmlEscape(item.edition)}" data-open="${index}">
     <div class="collectible ${htmlEscape(item.finish)} ${shape}" style="--metal:${htmlEscape(item.color)};--aurora:${htmlEscape(item.glow)};--portrait-position:${htmlEscape(item.crop||'50% 35%')}">
       <div class="plate"><span class="plate-filigree"></span><span class="plate-circle"></span><span class="plate-grain"></span></div>
       <div class="soft-background" style="background-image:url('${img}')"></div>
       <div class="inner-rail"></div><div class="frame-corners"></div>
       ${duo?`<img class="portrait-art portrait-left" src="${img}" loading="${index<4?'eager':'lazy'}" decoding="async" referrerpolicy="no-referrer" alt="">
                     <img class="portrait-art portrait-right" src="${second}" loading="lazy" decoding="async" referrerpolicy="no-referrer" alt="">`
           :`<img class="portrait-art portrait-main" src="${img}" loading="${index<4?'eager':'lazy'}" decoding="async" referrerpolicy="no-referrer" alt="">`}
       <div class="foil-sheen"></div><div class="bottom-veil"></div>
       <div class="card-top"><span>NIGHT <small>COLLECTIBLES</small></span><span class="card-number">M / ${String(i).padStart(3,'0')}</span></div>
       <div class="card-type"><span>${htmlEscape(title[item.series])}</span><b>${item.family==='romance'?'♡':item.family==='power'?'✧':'◇'}</b></div>
       <div class="card-bottom"><div class="eyebrow">${htmlEscape(item.tag)}</div>
         <strong class="card-title">${htmlEscape(item.title)}</strong>
         <div class="card-form">${htmlEscape(item.edition)}</div>
         <div class="fine"><span>${htmlEscape(item.finish.toUpperCase())}</span><span>${item.family==='romance'?'BOND':item.family==='power'?'POWER · UNRANKED':'STORY'}</span></div>
       </div>
     </div></button>
     <div class="under-card"><span class="under-name">${htmlEscape(item.title)}</span><span class="under-tag">${htmlEscape(item.edition)}</span></div>
  </article>`;
 }).join('\\n');
 const details=JSON.stringify(visuals.entries.map(a=>({name:a.title,form:a.edition,source:a.source,credit:a.credit,id:a.id,series:a.series,image:a.image,secondImage:a.secondImage??null}))).replace(/</g,'\\\\u003c');
 return `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>NIGHT / Manhwa Cards — Visual Gallery</title>
<meta name="description" content="NIGHT manhwa collectible card visual studies for SSS-Class Revival Hunter, The Infinite Mage and Study Group.">
<style>
:root{font-family:Inter,ui-sans-serif,system-ui,-apple-system,'Segoe UI',sans-serif;color:#ede8dd;background:#0b0c0e}
*{box-sizing:border-box}html{scroll-behavior:smooth}body{margin:0;min-width:320px;background:radial-gradient(ellipse 82% 34% at 56% -7%,#2c2e35 0%,transparent 77%),#0b0c0e}
button,input,select{font:inherit}button{cursor:pointer}a{color:inherit}header{height:74px;display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid #28292a;padding:0 max(28px,calc((100vw - 1330px)/2));position:relative;z-index:15}
.logo{font-weight:790;letter-spacing:.26em;font-size:17px}.logo small{color:#888a90;font-size:9px;letter-spacing:.18em;margin-left:12px;font-weight:550}
header nav{display:flex;gap:28px}header nav a{font-size:12px;text-decoration:none;color:#babac0;letter-spacing:.055em}
header nav a:hover{color:#fff}
.hero{max-width:1260px;margin:0 auto;padding:70px 28px 28px;position:relative}
.kicker{display:inline-flex;gap:12px;align-items:center;font-size:10px;font-weight:720;letter-spacing:.27em;color:#c6ac89}
.kicker:before{content:'';height:1px;width:25px;background:#b69e7c}
h1{font-family:Georgia,'Times New Roman',serif;font-size:clamp(48px,7.3vw,99px);font-weight:400;letter-spacing:-.062em;line-height:1;max-width:800px;margin:22px 0}
h1 em{color:#c7ad8d;font-weight:400}.intro{font-size:14px;line-height:1.8;max-width:640px;color:#a7a7ad;margin:20px 0 0}
.metrics{display:flex;gap:44px;margin:33px 0 0}.metric b{font-family:Georgia,serif;font-size:28px;font-weight:400;color:#efebe4}.metric span{font-size:10px;letter-spacing:.13em;text-transform:uppercase;display:block;margin-top:3px;color:#98969b}
.divider{height:1px;background:#2d2d31;max-width:1204px;margin:42px auto 0}
.gallery{max-width:1260px;margin:0 auto;padding:27px 28px 84px}
.controls{display:flex;justify-content:space-between;align-items:center;gap:18px;flex-wrap:wrap;padding:4px 0 42px}
.controls h2{font-size:12px;font-weight:700;letter-spacing:.21em;text-transform:uppercase;margin:0}
.filters{display:flex;gap:9px;flex-wrap:wrap}
.filter{background:#141518;border:1px solid #343439;color:#b6b6bc;padding:10px 14px;border-radius:2px;font-size:11px;letter-spacing:.04em;transition:border-color .2s,background .2s}
.filter[aria-pressed="true"],.filter:hover{background:#282727;color:#f3e6cb;border-color:#978267}
.gallery-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:43px 27px}
.card-wrap{min-width:0;transition:transform .22s ease}.card-wrap:hover{transform:translateY(-8px)}
.card-wrap[hidden]{display:none!important}
.open-card{display:block;width:100%;height:auto;padding:9px 5px 0;border:0;background:none;text-align:left;color:inherit}
.collectible{aspect-ratio:5/8;position:relative;width:100%;border-radius:13px;isolation:isolate;overflow:visible;
 box-shadow:0 18px 38px #000a,0 0 0 1px #79706755,0 0 0 4px #151618;
 background:linear-gradient(150deg,#22242a 0%,#0a0d13 65%,#121115 100%)}
.plate{position:absolute;inset:0;border-radius:inherit;overflow:hidden;background:
 radial-gradient(ellipse 77% 43% at 53% 26%,var(--aurora),transparent 77%),
 radial-gradient(ellipse 70% 56% at 50% 57%,#282731c2,transparent 80%),
 linear-gradient(142deg,#292833,#090a0e 52%,#161516)}
.plate:after{content:'';position:absolute;inset:5px;border-radius:9px;border:1px solid #ffffff25;box-shadow:inset 0 0 17px #c8a78e33}
.plate-filigree{position:absolute;inset:13%;border:1px solid #ffffff11;transform:rotate(-18deg) scale(1.25)}
.plate-circle{position:absolute;width:95%;aspect-ratio:1;top:17%;left:2.5%;border:1px solid #ddd7c617;border-radius:50%;box-shadow:0 0 0 28px #e5c79e08,0 0 0 61px #efd9b408}
.plate-grain{position:absolute;inset:0;opacity:.1;background:repeating-linear-gradient(112deg,transparent 0 7px,#ffffff10 8px 9px)}
.soft-background{position:absolute;inset:0;border-radius:13px;background-size:cover;background-position:var(--portrait-position);
 opacity:.24;filter:grayscale(.5) blur(12px) saturate(.5);mask-image:linear-gradient(#0005 0%,#000 29%,#000 55%,transparent 83%)}
.inner-rail{position:absolute;z-index:2;inset:9% 6.7% 9%;border:1px solid color-mix(in srgb,var(--metal),transparent 17%);border-radius:42% 42% 4px 4px / 12% 12% 4px 4px;box-shadow:0 0 0 1px #0008,inset 0 0 0 4px #0002}
.inner-rail:before,.inner-rail:after{content:'';position:absolute;width:18px;height:18px;border:1px solid var(--metal);transform:rotate(45deg);left:calc(50% - 9px)}
.inner-rail:before{top:-9px}.inner-rail:after{bottom:-9px}
.frame-corners{position:absolute;z-index:2;inset:13px;border:1px solid #ffffff35;border-radius:9px;pointer-events:none}
.frame-corners:after{content:'';position:absolute;inset:5px;border:1px solid #e6d6b01b;border-radius:6px}
.portrait-art{position:absolute;z-index:3;object-fit:cover;object-position:var(--portrait-position);pointer-events:none;
 filter:drop-shadow(7px 9px 10px #0006);-webkit-mask-image:linear-gradient(to bottom,transparent 0%,black 6%,black 68%,transparent 100%);
 mask-image:linear-gradient(to bottom,transparent 0%,black 6%,black 68%,transparent 100%)}
.portrait-main{width:116%;height:88%;left:-8%;top:-5%;border-radius:17% 17% 8% 8% / 7% 7% 2% 2%;
 -webkit-mask-image:radial-gradient(ellipse 56% 62% at 50% 45%,#000 56%,transparent 89%);
 mask-image:radial-gradient(ellipse 56% 62% at 50% 45%,#000 56%,transparent 89%)}
.portrait-left,.portrait-right{width:66%;height:80%;top:0%;object-fit:cover;object-position:50% 18%;
 -webkit-mask-image:radial-gradient(ellipse 63% 65% at 50% 47%,#000 48%,transparent 87%);
 mask-image:radial-gradient(ellipse 63% 65% at 50% 47%,#000 48%,transparent 87%)}
.portrait-left{left:-10%;transform:rotate(-6deg)}.portrait-right{right:-11%;transform:rotate(5deg)}
.pair .portrait-main{width:110%;left:-5%;top:-7%;height:88%;object-position:50% 18%}
.foil-sheen{position:absolute;z-index:4;inset:0;border-radius:inherit;background:linear-gradient(115deg,#ffffff00 20%,#ffffff14 25%,#ffffff00 36%);opacity:.35;pointer-events:none}
.bottom-veil{position:absolute;z-index:4;left:0;right:0;bottom:0;height:41%;border-radius:0 0 13px 13px;background:linear-gradient(transparent 0%,#0a0c12ac 30%,#0a0b10f2 71%,#101014 100%);pointer-events:none}
.card-top{position:absolute;z-index:5;left:10%;right:10%;top:5%;display:flex;justify-content:space-between;gap:8px;align-items:flex-start;
 font-size:13px;letter-spacing:.19em;color:#e7ded1;text-shadow:0 1px 4px #000}
.card-top small{font-size:5.5px;letter-spacing:.16em;display:block;margin-top:4px;font-weight:500}
.card-number{font-size:8px;letter-spacing:.16em}
.card-type{position:absolute;z-index:5;left:9%;right:9%;top:13%;font-size:8px;letter-spacing:.11em;text-transform:uppercase;
 color:#ddd0c4;display:flex;justify-content:space-between;align-items:center;text-shadow:0 1px 7px #000}
.card-type b{font-size:19px;color:var(--metal);font-weight:400}
.card-bottom{position:absolute;z-index:5;bottom:7%;left:9%;right:9%;color:#f7f1ea;line-height:1}
.eyebrow{font-size:8px;letter-spacing:.23em;font-weight:660;text-transform:uppercase;color:var(--metal);margin-bottom:8px}
.card-title{font-family:Georgia,'Times New Roman',serif;font-weight:500;font-size:clamp(18px,2.1vw,30px);line-height:.98;
 letter-spacing:-.045em;display:block;text-shadow:0 2px 8px #000d}
.card-form{font-size:10px;margin-top:7px;color:#e2d6c9;letter-spacing:.05em}
.fine{display:flex;gap:10px;justify-content:space-between;align-items:center;border-top:1px solid #ffffff34;margin-top:17px;padding-top:9px;
 font-size:7.5px;letter-spacing:.09em;color:#c5c0b6}
.ivory .plate{background:linear-gradient(151deg,#8caaaa,#647c87 26%,#182630 61%,#0e1018)}
.ivory .plate-circle{border-color:#dfecef3a}
.crimson .plate{background:radial-gradient(ellipse at 64% 27%,#612e46,transparent 80%),linear-gradient(125deg,#4f1724,#100d17 77%)}
.tidelight .plate{background:radial-gradient(ellipse at 45% 26%,#4b6186 0%,#0c1c2e 75%);color:#dedce9}
.emberglass .plate{background:radial-gradient(ellipse at 50% 23%,#845038,#17101c 78%);color:#f1d6a8}
.obsidian .plate{background:radial-gradient(ellipse at 50% 22%,#66503e,#09090c 72%)}
.under-card{padding:17px 9px 0;display:flex;justify-content:space-between;gap:5px;align-items:baseline;min-height:40px}
.under-name{font-size:12px;font-weight:700;color:#e4dfd5}.under-tag{font-size:10px;color:#919092;text-align:right}
.empty{padding:28px 0;color:#9f9995;text-align:center}
.endcap{max-width:1204px;margin:0 auto 70px;border-top:1px solid #333238;padding:30px 0;color:#969399;display:flex;align-items:flex-start;justify-content:space-between;gap:25px;font-size:11px;line-height:1.8}
.endcap strong{font-size:13px;font-weight:600;color:#ded7cd}
.endcap p{max-width:680px;margin:6px 0}.endcap a{color:#c5b39c}
.viewer{border:0;padding:0;background:#101014;color:#eae5de;max-width:min(94vw,900px);max-height:95vh;width:min(94vw,900px);border:1px solid #484041;box-shadow:0 25px 80px #000e;overflow:auto}
.viewer::backdrop{background:#000d;backdrop-filter:blur(8px)}
.viewer-inner{display:grid;grid-template-columns:minmax(260px,1fr) minmax(200px,.8fr);align-items:center;gap:28px;padding:35px}
.viewer-photo{max-width:420px;width:100%;justify-self:center}.viewer .card-title{font-size:27px}
.viewer .collectible{max-width:420px}.viewer .portrait-main{object-fit:cover}
.viewer-copy small{font-size:10px;letter-spacing:.2em;color:#cfb997}.viewer-copy h3{font:400 clamp(28px,4vw,51px) Georgia;letter-spacing:-.035em;line-height:1;margin:16px 0}
.viewer-copy p{color:#a8a5a3;font-size:13px;line-height:1.75}.viewer-copy a{font-size:13px;color:#dcc3a1;line-height:1.6}
.close{position:sticky;top:0;float:right;z-index:10;background:#222026;color:#fff;border:1px solid #565258;padding:12px 17px}
.badge{display:inline-block;font-size:9px;letter-spacing:.08em;color:#ddd1bb;border:1px solid #52463d;padding:7px 9px}
.loading-note{font-size:11px;color:#888895;margin:20px 0 0}
@media(max-width:1050px){.gallery-grid{grid-template-columns:repeat(3,minmax(0,1fr))}.gallery{max-width:900px}.hero{max-width:900px}}
@media(max-width:700px){header{padding:0 18px;height:65px}.logo{font-size:15px}.logo small{display:none}
header nav{gap:16px}.hero{padding:49px 19px 15px}h1{font-size:clamp(43px,13vw,75px)}.intro{font-size:13px}.metrics{gap:25px}.divider{margin-top:28px}
.gallery{padding:19px 14px 60px}.gallery-grid{grid-template-columns:repeat(2,minmax(0,1fr));gap:30px 10px}
.controls{gap:20px;padding:0 0 25px}.filter{padding:9px 11px;font-size:10px}
.card-title{font-size:clamp(16px,4.7vw,25px)}.eyebrow{font-size:7px;margin-bottom:6px}
.card-type{font-size:6px}.card-top{font-size:10px}.card-top small{font-size:4px}.card-number{font-size:7px}
.fine{font-size:6px}.card-form{font-size:8px;margin-top:4px}.under-card{display:block;padding:13px 3px 0}.under-tag{display:block;text-align:left;margin-top:4px;font-size:9px}
.endcap{padding:25px 19px;display:block;margin:0 auto 35px}
.viewer-inner{display:block;padding:20px}.viewer-photo{max-width:320px;margin:10px auto 30px}.viewer-copy h3{font-size:32px}}
@media(prefers-reduced-motion:reduce){*,*:before,*:after{animation:none!important;transition:none!important;scroll-behavior:auto!important}}
</style>
</head><body><header><a class="logo" href="./index.html" style="text-decoration:none">NIGHT<small>THE CARD LIBRARY</small></a>
<nav><a href="#collection">Collection</a><a href="${htmlEscape(rosterUrl)}">82-character roster ↗</a></nav></header>
<section class="hero"><div class="kicker">MANHWA · VISUAL EDITIONS</div>
<h1>Stories worth<br><em>collecting.</em></h1>
<p class="intro">Actual manhwa imagery inside NIGHT collectible card compositions. Explore visual studies from three worlds, including romance and grounded story cards. Tap any card to see it enlarged.</p>
<div class="metrics"><div class="metric"><b>${visuals.entries.length}</b><span>Visual studies</span></div>
<div class="metric"><b>${roster.series.length}</b><span>Universes</span></div><div class="metric"><b>${total}</b><span>Roster candidates</span></div></div>
<div class="loading-note">Visual references are externally linked from their credited sources. Artwork rights and final cutout approval remain pending.</div></section>
<div class="divider"></div><main class="gallery" id="collection"><div class="controls">
<h2>Collectible gallery</h2><div class="filters" role="group" aria-label="Filter manhwa">
<button class="filter" data-filter="all" aria-pressed="true">All · ${visuals.entries.length}</button>
<button class="filter" data-filter="sss-revival" aria-pressed="false">SSS Revival · ${counts['sss-revival']}</button>
<button class="filter" data-filter="infinite-mage" aria-pressed="false">Infinite Mage · ${counts['infinite-mage']}</button>
<button class="filter" data-filter="study-group" aria-pressed="false">Study Group · ${counts['study-group']}</button>
<button class="filter" data-filter="romance" aria-pressed="false">Romance</button></div></div>
<div class="gallery-grid" id="card-grid">${cards}</div>
<p class="empty" id="nothing" hidden>No matching cards.</p></main>
<div class="endcap"><div><strong>NIGHT / MANHWA</strong><p>Visual compositions for review, not a release of licensed collectible merchandise. No speculative power tier, no generated images. Image references retain their original source links. Finished collectible masters require approved art rights, source-byte locking, silhouette separation, and full-resolution inspection.</p></div><a href="${htmlEscape(rosterUrl)}">Browse the full 82-character research roster ↗</a></div>
<dialog class="viewer" id="viewer" aria-label="Enlarged card preview"><button type="button" class="close" id="close" aria-label="Close card">✕ CLOSE</button><div class="viewer-inner"><div id="view-card" class="viewer-photo"></div><div class="viewer-copy"><small>NIGHT — VISUAL STUDY</small><h3 id="view-title"></h3><div class="badge">SOURCE-LINKED ART</div><p id="view-credit"></p><a id="view-source" href="#" target="_blank" rel="noopener noreferrer">Original reference ↗</a><p>Use the full-size card to judge spacing, typographic hierarchy and the frame breakout before final art approval.</p></div></div></dialog>
<script>
const details=${details};
const cards=[...document.querySelectorAll('.card-wrap')];
const buttons=[...document.querySelectorAll('.filter')];
const dialog=document.querySelector('#viewer');
function apply(f){
 buttons.forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.filter===f)));
 let showing=0;
 cards.forEach(c=>{const match=f==='all'||c.dataset.universe===f||(f==='romance'&&c.dataset.family==='romance');
 c.hidden=!match;if(match)showing++;});
 document.querySelector('#nothing').hidden=showing>0;
}
buttons.forEach(b=>b.addEventListener('click',()=>apply(b.dataset.filter)));
document.querySelectorAll('[data-open]').forEach(b=>b.addEventListener('click',()=>{
 const idx=Number(b.dataset.open),data=details[idx],copy=cards[idx].querySelector('.collectible').cloneNode(true);
 copy.querySelectorAll('img').forEach(im=>{im.loading='eager'});
 const target=document.querySelector('#view-card');target.replaceChildren(copy);
 document.querySelector('#view-title').textContent=data.name+' — '+data.form;
 document.querySelector('#view-credit').textContent=data.credit;
 const link=document.querySelector('#view-source');link.href=data.source;
 dialog.showModal();
}));
document.querySelector('#close').addEventListener('click',()=>dialog.close());
dialog.addEventListener('click',e=>{if(e.target===dialog)dialog.close()});
</script></body></html>`;
}
