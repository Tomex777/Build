import {chromium} from 'playwright';
/** One browser per renderer, a serial queue, isolated pages, explicit cleanup. */
export async function createRasterizer({executablePath=process.env.NIGHT_BROWSER_PATH,timeout=30000}={}){
 const browser=await chromium.launch({headless:true,...(executablePath?{executablePath}:{}),args:['--no-sandbox','--font-render-hinting=none']});
 let queue=Promise.resolve(),closed=false;
 const render=(svg,{width=2000}={})=>{
  if(closed)return Promise.reject(new Error('Rasterizer is closed'));
  if(typeof svg!=='string'||!svg.startsWith('<svg ')||!Number.isInteger(width)||width<160||width>4000)return Promise.reject(new Error('Use a NIGHT SVG and an output width between 160 and 4000'));
  const job=queue.then(async()=>{
   const context=await browser.newContext({viewport:{width:1000,height:1400},deviceScaleFactor:width/1000});
   try{
    const page=await context.newPage();page.setDefaultTimeout(timeout);
    // Renderer output is self-contained. Unexpected network content is blocked.
    await page.route('**/*',route=>route.request().url().startsWith('data:')?route.continue():route.abort());
    await page.setContent(`<html><head><style>html,body{margin:0;width:1000px;height:1400px;background:transparent}svg{display:block}</style></head><body>${svg}</body></html>`,{timeout});
    await page.evaluate(async()=>{await document.fonts.ready;await Promise.all([...document.querySelectorAll('image')].map(x=>new Promise((resolve,reject)=>{const i=new Image();i.onload=resolve;i.onerror=()=>reject(new Error('Embedded image failed to decode'));i.src=x.getAttribute('href')})));});
    return await page.locator('svg').screenshot({omitBackground:true,animations:'disabled',timeout});
   }finally{await context.close()}
  });
  queue=job.catch(()=>{});return job;
 };
 return {render,close:async()=>{closed=true;await queue;await browser.close()}};
}
