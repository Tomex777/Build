import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';
import sharp from 'sharp';
import {renderRomanceCard} from '../src/romance.mjs';

const root=fileURLToPath(new URL('../',import.meta.url));
const output=path.resolve(root,process.argv[2]??'dist/romance-preview');
await fs.mkdir(output,{recursive:true});
const sources=JSON.parse(await fs.readFile(path.join(root,'artwork/romance/sources.json'),'utf8'));
const catalogue=JSON.parse(await fs.readFile(path.join(root,'catalog/romance-preview.json'),'utf8'));
const fonts={};
for(const [key,name]of Object.entries({display:'Display',sans:'Sans'}))fonts[key]='data:font/ttf;base64,'+(await fs.readFile(path.join(root,`renderer/assets/fonts/${name}.ttf`))).toString('base64');
const browser=await chromium.launch({headless:true,executablePath:process.env.NIGHT_BROWSER_PATH,args:['--no-sandbox','--font-render-hinting=none']});
const entries=[];
try{
  for(const card of catalogue.cards){
    const subjects=[];
    for(const p of card.subjects){
      const s=sources.find(s=>s.id===p.id);if(!s)throw Error('Missing source '+p.id);
      const bytes=await fs.readFile(path.join(root,s.localPath));
      if(crypto.createHash('sha256').update(bytes).digest('hex')!==s.sha256)throw Error('Source bytes changed: '+s.id);
      const m=await sharp(bytes).metadata();
      if(m.width!==s.width||m.height!==s.height||!m.hasAlpha)throw Error('Source metadata differs: '+s.id);
      subjects.push({...s,art:'data:image/png;base64,'+bytes.toString('base64')});
    }
    const svg=renderRomanceCard(card,{subjects,fonts});
    const context=await browser.newContext({viewport:{width:1000,height:1600},deviceScaleFactor:3});
    try{
      const page=await context.newPage();
      await page.route('**/*',r=>r.request().url().startsWith('data:')?r.continue():r.abort());
      await page.setContent('<!doctype html><style>html,body{margin:0;background:transparent}svg{display:block}</style>'+svg);
      await page.evaluate(async()=>{await document.fonts.ready;await Promise.all([...document.querySelectorAll('image')].map(async n=>{const i=new Image();i.src=n.getAttribute('href');await i.decode();}));});
      const qa=await page.evaluate(async placements=>{
        const root=document.querySelector('svg'),images=[...root.querySelectorAll('[data-romance-subject]')];
        const ids=[...root.querySelectorAll('[id]')].map(n=>n.id);if(new Set(ids).size!==ids.length)throw Error('Duplicate SVG IDs');
        if(images.length!==2||root.querySelector('[data-layer="power-rank"]'))throw Error('Romance subject/rank contract failed');
        const name=root.querySelector('[data-name]'),box=name.getBoundingClientRect();
        if(box.left<40||box.right>960||box.bottom>1550)throw Error('Name outside safe area');
        const boundary=root.querySelector('[data-frame-boundary]'),bm=boundary.getScreenCTM(),length=boundary.getTotalLength();
        const point=t=>{const p=boundary.getPointAtLength(Math.min(length,t));return new DOMPoint(p.x,p.y).matrixTransform(bm);};
        const crossings=[];
        for(const [i,node]of images.entries()){
          const image=new Image();image.src=node.getAttribute('href');await image.decode();
          const v=node.parentElement.viewBox.baseVal,p=placements[i],scale=1600/1400;
          const canvas=document.createElement('canvas');canvas.width=1000;canvas.height=1600;
          const ctx=canvas.getContext('2d',{willReadFrequently:true});
          ctx.drawImage(image,v.x,v.y,v.width,v.height,scale*p.x+(1-scale)*500,scale*p.y,scale*p.width,scale*p.width*v.height/v.width);
          const pixels=ctx.getImageData(0,0,1000,1600).data;
          const alpha=(x,y)=>{x=Math.round(x);y=Math.round(y);return x>35&&x<965&&y>90&&y<1150?pixels[(y*1000+x)*4+3]:0;};
          let crossing=0,run=0,longest=0;
          for(let t=0;t<length;t+=3){
            const a=point(t),b=point(t+2),dx=b.x-a.x,dy=b.y-a.y,n=Math.hypot(dx,dy)||1;
            if(alpha(a.x-dy/n*8,a.y+dx/n*8)>180&&alpha(a.x+dy/n*8,a.y-dx/n*8)>180){crossing+=3;run+=3;longest=Math.max(longest,run);}else run=0;
          }
          if(longest<24)throw Error('Subject does not visibly cross frame: '+node.dataset.romanceSubject+' ('+longest+'px)');
          crossings.push({subject:node.dataset.romanceSubject,crossingLength:crossing,longestCrossing:longest});
        }
        return {twoOriginalImages:true,powerGradesAbsent:true,nameWithinCard:true,uniqueIds:true,breakout:crossings};
      },card.subjects);
      const png=await page.locator('svg[data-family="romance"]').screenshot({omitBackground:true,timeout:120000});
      const meta=await sharp(png).metadata();if(meta.width!==3000||meta.height!==4800)throw Error('Wrong master dimensions');
      await fs.writeFile(path.join(output,card.id+'.svg'),svg);
      await fs.writeFile(path.join(output,card.id+'.png'),png);
      await sharp(png).resize(320,512).png().toFile(path.join(output,card.id+'-phone.png'));
      const jpg=await sharp(png).resize(800,1280).flatten({background:'#dce2df'}).jpeg({quality:91}).toBuffer();
      entries.push({...card,qa,thumbnail:'data:image/jpeg;base64,'+jpg.toString('base64'),sources:subjects.map(({art,...s})=>s)});
      console.log(card.id,meta.width+'x'+meta.height,JSON.stringify(qa));
    }finally{await context.close();}
  }
}finally{await browser.close();}
const esc=x=>String(x).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const articles=entries.map(c=>`<article><button class="open" data-id="${esc(c.id)}" aria-label="Enlarge ${esc(c.name)}"><img src="${c.thumbnail}" alt="${esc(c.name)} — ${esc(c.bond)}" width="800" height="1280"></button><h2>${esc(c.name)}</h2><p>${esc(c.series)}</p><span>${esc(c.bond)}</span></article>`).join('');
const sourceNotes=entries.map(c=>`<li>${esc(c.name)}: ${c.sources.map(s=>`<a href="${esc(s.page)}">${esc(s.character)}</a>`).join(' · ')}</li>`).join('');
const html=`<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>NIGHT Romance</title><style>*{box-sizing:border-box}body{margin:0;background:#e8e7df;color:#203c41;font-family:Arial,sans-serif}header,main,footer{max-width:1320px;margin:auto;padding:30px}header{padding-top:44px;border-bottom:1px solid #b9c5c2}header p{font-size:11px;letter-spacing:4px}h1{font-family:Georgia,serif;font-size:42px;font-weight:400;margin:18px 0 0}main{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:32px}article{max-width:480px;margin:auto;width:100%}.open{display:block;border:0;background:none;padding:0;width:100%;cursor:zoom-in}img{width:100%;height:auto;display:block;filter:drop-shadow(0 8px 10px #1b353323)}h2{font-size:18px;font-weight:400;margin:18px 0 8px}article p{font-size:12px;line-height:1.5;margin:0 0 12px;color:#526d70}article span{font-size:10px;letter-spacing:2px}footer{font-size:11px;line-height:1.8;border-top:1px solid #b9c5c2;color:#526d70}a{color:inherit}dialog{background:#dfe5df;border:0;padding:20px;border-radius:10px;max-width:min(94vw,720px);max-height:96vh}dialog::backdrop{background:#102728e8}dialog img{max-height:84vh;width:auto;max-width:100%;margin:auto}.close{display:block;margin:0 0 10px auto;padding:9px 14px;background:transparent;border:1px solid #718b8a;cursor:pointer}:focus-visible{outline:3px solid #558b98;outline-offset:4px}@media(max-width:700px){header,main,footer{padding:23px}main{grid-template-columns:1fr;gap:36px}article{max-width:420px;margin:auto;width:100%}h1{font-size:34px}}</style><header><p>NIGHT / ROMANCE</p><h1>Shared stories.</h1></header><main>${articles}</main><footer>Original character artwork; programmed NIGHT frames and typography. Artwork rights reserved. Personal design study.<details><summary>Artwork sources</summary><ul>${sourceNotes}</ul></details></footer><dialog aria-label="Enlarged romance card"><button class="close">Close</button><img alt=""></dialog><script>const d=document.querySelector('dialog');let trigger;document.querySelectorAll('.open').forEach(b=>b.onclick=()=>{trigger=b;const i=b.querySelector('img');d.querySelector('img').src=i.src;d.querySelector('img').alt=i.alt;d.showModal()});document.querySelector('.close').onclick=()=>d.close();d.addEventListener('close',()=>trigger?.focus());d.onclick=e=>{if(e.target===d)d.close()};</script></html>`;
await fs.writeFile(path.join(output,'NIGHT-Romance-Collection.html'),html);
await fs.writeFile(path.join(output,'manifest.json'),JSON.stringify({family:'romance',entries:entries.map(({thumbnail,...c})=>c),pending:catalogue.pending},null,2)+'\n');
