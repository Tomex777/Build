// Isolated native-image memory smoke; no network, credentials or WhatsApp posting.
// Usage (after installing/pinning Sharp): node mscc/channel-branding/resource-bench.mjs
// To fail a CI budget: CHANNEL_BRANDING_MAX_RSS_MIB=350 node .../resource-bench.mjs
import {createRequire} from 'node:module';
import {performance} from 'node:perf_hooks';
import {watermarkStaticImage,WatermarkCache} from './watermark.mjs';
const require=createRequire(import.meta.url),sharp=require('sharp');
const width=2400,height=1600;
const pixels=Buffer.allocUnsafe(width*height*3);
let state=0x1234abcd;
for(let i=0;i<pixels.length;i++){
  state=(Math.imul(state,1664525)+1013904223)>>>0;
  pixels[i]=state>>>16;
}
const image=await sharp(pixels,{raw:{width,height,channels:3}}).jpeg({quality:67}).toBuffer();
const renderer=({width:w,height:h})=>
  sharp(Buffer.from('<svg xmlns="http://www.w3.org/2000/svg" width="'+w+'" height="'+h+
    '"><text x="10" y="80" fill="white" font-size="34">NIGHT</text></svg>')).png().toBuffer();
const cache=new WatermarkCache({maxEntries:8,maxBytes:2*1024*1024});
const counts=[],at=performance.now();
const baselineRss=process.memoryUsage().rss;
let peakRss=baselineRss,rssSamples=0;
// Sharp executes native operations off the JS event loop. Sampling only when
// each promise resolves can miss its largest temporary decode/composite RSS.
const meter=setInterval(()=>{
  rssSamples++;
  peakRss=Math.max(peakRss,process.memoryUsage().rss);
},10);
try{
  for(let round=0;round<8;round++){
    const start=performance.now();
    const res=await watermarkStaticImage({
      input:image,channelId:'120363456789012345@newsletter',displayName:'NIGHT',
      position:'auto',opacity:.22,cache,renderText:renderer,sharpLibrary:sharp
    });
    counts.push({ms:Math.round(performance.now()-start),resultBytes:res.buffer.length});
    peakRss=Math.max(peakRss,process.memoryUsage().rss);
  }
}finally{clearInterval(meter)}
const report={
  note:'Isolated synthetic sample; NOT total live MSCC process RSS; single-worker assumption.',
  jobs:counts.length,sourcePixels:width*height,sourceEncodedBytes:image.length,
  totalMs:Math.round(performance.now()-at),
  samplingIntervalMs:10,samples:rssSamples,
  baselineRSSMiB:Math.round(baselineRss/1048576),
  sampledPeakRSSMiB:Math.round(peakRss/1048576),
  peakRSSMiB:Math.round(peakRss/1048576),
  maxJobMs:Math.max(...counts.map(x=>x.ms)),
  maxResultBytes:Math.max(...counts.map(x=>x.resultBytes)),
  cacheBytes:cache.bytes
};
console.log(JSON.stringify(report,null,2));
const limit=Number(process.env.CHANNEL_BRANDING_MAX_RSS_MIB||0);
if(limit>0&&report.peakRSSMiB>limit){
  console.error('FAIL: isolated branding worker exceeded specified resident-memory ceiling.');
  process.exitCode=1;
}
