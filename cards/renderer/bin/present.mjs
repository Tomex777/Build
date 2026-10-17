import fs from 'node:fs/promises';
import path from 'node:path';
import {pathToFileURL} from 'node:url';
import {ROOT,dataURI} from '../src/node-assets.mjs';
import {launch} from './browser.mjs';
const browser=await launch();
const image=async p=>dataURI(await fs.readFile(p),'image/png');
const fonts={display:dataURI(await fs.readFile(ROOT+'/assets/fonts/Display.ttf'),'font/ttf'),sans:dataURI(await fs.readFile(ROOT+'/assets/fonts/Sans.ttf'),'font/ttf')};
try{
 const page=await browser.newPage({viewport:{width:1900,height:1190},deviceScaleFactor:1});
 const common=`@font-face{font-family:D;src:url('${fonts.display}')}@font-face{font-family:S;src:url('${fonts.sans}')}*{box-sizing:border-box}body{margin:0;background:#e7e6df;color:#203329;font-family:S,Arial,sans-serif}`;
 const cards=['nocturne','tidelight','verdant'],labels=['Spectral chrome','Pearl foil','Champagne gold'];
 let figures='';for(let i=0;i<cards.length;i++)figures+=`<figure><img src="${await image(ROOT+'/dist/'+cards[i]+'-front-2000.png')}"/><figcaption><span>00${i+1} &nbsp; ${cards[i].toUpperCase()}</span><small>${labels[i]}</small></figcaption></figure>`;
 await page.setContent(`<html><head><style>${common}header{margin:44px 74px 0;display:flex;justify-content:space-between;font-size:14px;letter-spacing:4px;border-bottom:1px solid #b7bdb2;padding-bottom:24px}h1{margin:24px 74px 30px;font:500 65px D}main{display:flex;gap:68px;justify-content:center}figure{margin:0;width:500px}img{display:block;width:500px;height:700px;filter:drop-shadow(0 20px 16px #123a2426)}figcaption{display:flex;justify-content:space-between;margin-top:28px;font-size:13px;letter-spacing:2px}small{font-size:12px;letter-spacing:0;color:#536057}footer{margin:48px 74px 0;border-top:1px solid #b7bdb2;padding-top:24px;font-size:12px;color:#536057;display:flex;justify-content:space-between}</style></head><body><header><span>NIGHT</span><span>COLLECTION 01</span></header><h1>The Artist Editions</h1><main>${figures}</main><footer><span>Artwork by Tyson Tan · CC BY-SA 4.0 · Cropped and framed by NIGHT</span><span>Actual renders · 2000 × 2800 masters</span></footer></body></html>`);
 await page.evaluate(()=>document.fonts.ready);await page.screenshot({path:ROOT+'/dist/NIGHT-Artist-Editions.png'});
 await page.setViewportSize({width:1120,height:610});
 let thumbs='';for(const id of cards)thumbs+=`<figure><img src="${await image(ROOT+'/dist/'+id+'-whatsapp-320.png')}"/><figcaption>${id.toUpperCase()} · 320 × 448</figcaption></figure>`;
 await page.setContent(`<html><head><style>${common}h1{font:500 31px D;margin:24px 48px 10px}p{margin:0 48px 20px;font-size:11px;color:#536057}main{display:flex;gap:32px;justify-content:center}figure{margin:0;width:320px}img{display:block;width:320px;height:448px}figcaption{margin-top:12px;font-size:10px;letter-spacing:2px}</style></head><body><h1>WhatsApp viewing size</h1><p>Native 320-pixel renders. Source images, without WhatsApp upload or recompression.</p><main>${thumbs}</main></body></html>`);
 await page.evaluate(()=>document.fonts.ready);await page.screenshot({path:ROOT+'/qa/whatsapp-size-check.png'});
 for(const [n,label] of [['01','First render'],['02','Crop and frame refinement'],['03','Foil contours and typography'],['04','Distinct orbital, pearl and botanical compositions'],['05','Type fields and engraving refinement']]){
  try{await fs.access(ROOT+'/qa/iterations/'+n+'/nocturne-whatsapp-320.png')}catch{continue}
  let imgs='';for(const id of cards)imgs+=`<figure><img src="${await image(ROOT+'/qa/iterations/'+n+'/'+id+'-whatsapp-320.png')}"/><figcaption>${id.toUpperCase()}</figcaption></figure>`;
  await page.setContent(`<html><head><style>${common}h1{font:500 31px D;margin:24px 48px 20px}main{display:flex;gap:32px;justify-content:center}figure{margin:0;width:320px}img{display:block;width:320px;height:448px}figcaption{margin-top:12px;font-size:10px;letter-spacing:2px}</style></head><body><h1>${n} · ${label}</h1><main>${imgs}</main></body></html>`);
  await page.evaluate(()=>document.fonts.ready);await page.screenshot({path:ROOT+'/qa/iteration-'+n+'.png'});
 }
 // The archived v1 thumbnails remain literal before images, at native size.
 try{
  await fs.access(ROOT+'/qa/v1-reference/nocturne-whatsapp-320.png');
  await page.setViewportSize({width:1120,height:1170});
  let before='',after='';
  for(const id of cards){before+=`<img src="${await image(ROOT+'/qa/v1-reference/'+id+'-whatsapp-320.png')}"/>`;after+=`<img src="${await image(ROOT+'/dist/'+id+'-whatsapp-320.png')}"/>`;}
  await page.setContent(`<html><head><style>${common}h1{font:500 34px D;margin:24px 48px 24px}h2{font:500 12px S;letter-spacing:3px;margin:0 0 15px}section{margin:0 48px 27px}.row{display:flex;gap:32px}img{width:320px;height:448px;display:block}footer{margin:20px 48px;font-size:10px;color:#536057}</style></head><body><h1>The Artist Editions · Before & After</h1><section><h2>ORIGINAL</h2><div class="row">${before}</div></section><section><h2>REFINED</h2><div class="row">${after}</div></section><footer>Actual native 320-pixel renders · Artwork by Tyson Tan · CC BY-SA 4.0 · Cropped and framed by NIGHT</footer></body></html>`);
  await page.evaluate(()=>document.fonts.ready);await page.screenshot({path:ROOT+'/qa/before-after.png'});
 }catch(error){if(error.code!=='ENOENT')throw error;}
 await page.setViewportSize({width:1000,height:1000});
 await page.goto(pathToFileURL(ROOT+'/dist/index.html').href);await page.evaluate(()=>document.fonts.ready);
 await page.addStyleTag({content:'#face{transform:none!important;transition:none!important;filter:none!important}#stage{width:440px!important}'});
 await fs.mkdir(ROOT+'/qa/foil-frames',{recursive:true});
 for(let i=0;i<24;i++){
  const t=i/24*Math.PI*2;
  await page.evaluate(([x,y])=>window.NIGHT_STUDIO.illuminate(x,y),[.5+.48*Math.cos(t),.5+.4*Math.sin(t)]);
  await page.locator('#face').screenshot({path:ROOT+'/qa/foil-frames/'+String(i).padStart(3,'0')+'.png',omitBackground:true});
 }
 console.log('Rendered overview, actual-size proof, iteration proofs and 24 foil-light frames.');
}finally{await browser.close()}
