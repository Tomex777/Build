import {chromium} from 'playwright';
import sharp from 'sharp';
import fs from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';

/** Render the complete SVG at the requested density using bounded viewport tiles. */
export async function createTiledRasterizer({executablePath=process.env.NIGHT_BROWSER_PATH,timeout=120000}={}){
 const browser=await chromium.launch({headless:true,...(executablePath?{executablePath}:{}),args:['--no-sandbox','--font-render-hinting=none']});
 let queue=Promise.resolve(),closed=false;
 const renderToFile=(svg,filename,{width=6000,format='png',onProgress=()=>{}}={})=>{
  if(closed)return Promise.reject(new Error('Rasterizer is closed'));
  if(typeof svg!=='string'||!svg.startsWith('<svg ')||!Number.isInteger(width)||width<1000||width>12000||width%1000!==0)return Promise.reject(new Error('Use a NIGHT SVG and width 1000–12000 in 1000-pixel steps'));
  if(!['png','jpeg'].includes(format))return Promise.reject(new Error('Use PNG or JPEG'));
  const job=queue.then(async()=>{
   const scale=width/1000,height=width*1.4,tileW=500,tileH=350;
   const context=await browser.newContext({viewport:{width:tileW,height:tileH},deviceScaleFactor:scale});
   const folder=await fs.mkdtemp(path.join(os.tmpdir(),'night-tiles-'));
   try{
    const page=await context.newPage();page.setDefaultTimeout(timeout);
    await page.route('**/*',r=>r.request().url().startsWith('data:')?r.continue():r.abort());
    await page.setContent(`<html><head><style>html,body{margin:0;width:500px;height:350px;background:transparent}svg{display:block;width:500px;height:350px}</style></head><body>${svg}</body></html>`);
    await page.evaluate(async()=>{await document.fonts.ready;await Promise.all([...document.querySelectorAll('image')].map(x=>new Promise((resolve,reject)=>{const i=new Image();i.onload=resolve;i.onerror=reject;i.src=x.getAttribute('href')})));});
    const layers=[];let index=0;
    for(let y=0;y<1400;y+=tileH)for(let x=0;x<1000;x+=tileW){
     await page.locator('svg').evaluate((el,{x,y,w,h})=>{el.setAttribute('width',w);el.setAttribute('height',h);el.setAttribute('viewBox',`${x} ${y} ${w} ${h}`);},{x,y,w:tileW,h:tileH});
     const f=path.join(folder,`${index}.png`);
     await page.screenshot({path:f,omitBackground:true,animations:'disabled',timeout});
     const m=await sharp(f).metadata();if(m.width!==tileW*scale||m.height!==tileH*scale)throw new Error('Unexpected tile dimensions');
     layers.push({input:f,left:x*scale,top:y*scale});onProgress({completed:++index,total:8,width,height});
    }
    await fs.mkdir(path.dirname(path.resolve(filename)),{recursive:true});
    const pipeline=sharp({create:{width,height,channels:4,background:{r:0,g:0,b:0,alpha:0}},limitInputPixels:300000000}).composite(layers);
    // Publish only a completely encoded, closed file. This also prevents file
    // watchers and downloaders from seeing an in-progress large export.
    const encoded=path.join(folder,`complete.${format==='png'?'png':'jpg'}`);
    if(format==='png')await pipeline.png({compressionLevel:6}).toFile(encoded);
    else await pipeline.flatten({background:'#e7e6df'}).jpeg({quality:97,chromaSubsampling:'4:4:4'}).toFile(encoded);
    const meta=await sharp(encoded,{limitInputPixels:300000000}).metadata();
    if(meta.width!==width||meta.height!==height)throw new Error('Unexpected export dimensions');
    // Decode to completion before exposing the file; metadata alone is insufficient.
    await sharp(encoded,{limitInputPixels:300000000,failOn:'warning'}).resize(20,28).raw().toBuffer();
    const pending=path.resolve(filename)+'.pending';
    await fs.copyFile(encoded,pending);
    const handle=await fs.open(pending,'r+');try{await handle.sync();}finally{await handle.close();}
    await fs.rename(pending,filename);
    return {filename,width,height,format,bytes:(await fs.stat(filename)).size};
   }finally{await context.close();await fs.rm(folder,{recursive:true,force:true});}
  });
  queue=job.catch(()=>{});return job;
 };
 return {renderToFile,close:async()=>{closed=true;await queue;await browser.close();}};
}
