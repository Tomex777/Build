import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {prepareChannelContent} from './prepare.mjs';
import {watermarkStaticImage,WatermarkCache} from './watermark.mjs';
const require=createRequire(import.meta.url),sharp=require('sharp');
const jpg=await sharp({create:{width:900,height:480,channels:3,background:'#222f40'}})
  .jpeg({quality:85}).toBuffer();
const renderText=async({width,height,displayName})=>{
  assert.equal(displayName,'NIGHT 👩🏽‍💻');
  const svg='<svg xmlns="http://www.w3.org/2000/svg" width="'+width+'" height="'+height+
    '"><text x="24" y="'+Math.round(height*.7)+'" fill="white" font-size="27">NIGHT</text></svg>';
  return sharp(Buffer.from(svg)).png().toBuffer();
};
const render=opts=>watermarkStaticImage({...opts,renderText});
const branding={
  captionReplacementsEnabled:true,
  captionReplacements:[{from:'bullshit',to:'BS'}],
  signatureEnabled:true,posterName:'TOMEX',
  watermarkEnabled:true,watermarkOpacity:.25,watermarkPosition:'bottom-left'
};
const previewCache=new WatermarkCache({maxEntries:6,maxBytes:2*1024*1024});
const first=await prepareChannelContent({
  text:'Not bullshit.',mediaBuffer:jpg,channelId:'120363456789012345@newsletter',
  verifiedDisplayName:'NIGHT 👩🏽‍💻',branding,sourceKind:'automation',rendererVerifiedEmoji:true,
  watermarkCache:previewCache,sharpLibrary:sharp,transform:render
});
assert.equal(first.caption,'Not BS.\n\n> TOMEX');
assert.equal(first.watermarked,true);assert.equal(first.imageFormat,'jpeg');
assert.notDeepEqual(first.mediaBuffer,jpg,'Real image pixels changed');
assert.equal((await sharp(first.mediaBuffer).metadata()).format,'jpeg');
// The exact approved/revisioned content must render byte-for-byte identically
// in an authorized preview and the later send when source+assets are frozen.
const laterSend=await prepareChannelContent({
  text:'Not bullshit.',mediaBuffer:jpg,channelId:'120363456789012345@newsletter',
  verifiedDisplayName:'NIGHT 👩🏽‍💻',branding,sourceKind:'automation',rendererVerifiedEmoji:true,
  watermarkCache:previewCache,sharpLibrary:sharp,transform:render
});
assert.equal(laterSend.caption,first.caption,'Reviewed caption and send agree');
assert.equal(laterSend.watermarkAssetKey,first.watermarkAssetKey,'Reviewed channel identity and layer agree');
assert.deepEqual(laterSend.mediaBuffer,first.mediaBuffer,'Reviewed output media is deterministic');
const changedStyle=await prepareChannelContent({
  text:'Not bullshit.',mediaBuffer:jpg,channelId:'120363456789012345@newsletter',
  verifiedDisplayName:'NIGHT 👩🏽‍💻',branding:{...branding,watermarkOpacity:.3},
  sourceKind:'automation',rendererVerifiedEmoji:true,
  watermarkCache:previewCache,sharpLibrary:sharp,transform:render
});
assert.notEqual(changedStyle.watermarkAssetKey,first.watermarkAssetKey,
  'An altered branding revision cannot reuse the reviewed visual asset');
const other=await prepareChannelContent({
  text:'Not bullshit.',mediaBuffer:jpg,channelId:'120363456789012346@newsletter',
  verifiedDisplayName:'NIGHT 👩🏽‍💻',branding,sourceKind:'automation',rendererVerifiedEmoji:true,
  sharpLibrary:sharp,transform:render
});
assert.notEqual(first.watermarkAssetKey,other.watermarkAssetKey,'Different channel claims never share cached watermark identity');
const noChanges=await prepareChannelContent({text:'Not bullshit.',mediaBuffer:jpg,
  branding:{captionReplacementsEnabled:false,signatureEnabled:false,watermarkEnabled:false}});
assert.strictEqual(noChanges.mediaBuffer,jpg);
assert.equal(noChanges.caption,'Not bullshit.');
console.log('Channel branding actual preview/send JPEG transform contract: PASS');
