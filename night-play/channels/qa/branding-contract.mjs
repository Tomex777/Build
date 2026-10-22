// Agent E independent text/identity/security contract against D's real
// caption+watermark modules. No media upload or WhatsApp sending in this test.
import assert from 'node:assert/strict';
import path from 'node:path';
import {pathToFileURL} from 'node:url';
const root=path.resolve(process.env.NIGHT_BRANDING_ROOT || path.resolve(process.cwd(),'mscc/channel-branding'));
const caption=await import(pathToFileURL(path.join(root,'caption.mjs')).href);
const watermark=await import(pathToFileURL(path.join(root,'watermark.mjs')).href);
const jid='120363282083849178@newsletter';
const channelName='NIGHT 👩🏽‍💻 • 𝙊̀rẹ́ 🇳🇬';
const altered=channelName.replace('\u200d','');
assert.notEqual(channelName,altered,'ZWJ must matter');
const a=watermark.watermarkCacheKey({channelId:jid,displayName:channelName});
const b=watermark.watermarkCacheKey({channelId:jid,displayName:altered});
assert.notEqual(a,b,'Verified exact channel display-name graphemes produce distinct watermark assets');
assert.notEqual(a,watermark.watermarkCacheKey({
 channelId:'120363282083849179@newsletter',displayName:channelName
}), 'Watermark cannot leak cross-channel assets');
assert.notEqual(a,watermark.watermarkCacheKey({
 channelId:jid,displayName:channelName,opacity:.3
}), 'Opacity changes must invalidate variant cache');
assert.notEqual(a,watermark.watermarkCacheKey({
 channelId:jid,displayName:channelName,styleVersion:2
}), 'Style version changes must invalidate cache');

const rules={enabled:true,rules:[
 {from:'what the fuck',to:'WTF'}, {from:'fuck',to:'f*ck'},
 {from:'shit',to:'shii'}, {from:'bullshit',to:'BS'}]};
const sample='what the fuck! FUCK, bullshit; shits, bullshiters, and shell.';
const changed=caption.replaceCaptionWords(sample,rules);
assert.equal(changed,'WTF! f*ck, BS; shits, bullshiters, and shell.',
  'Phrase precedence, no double replacements, and Unicode lexical boundaries');
assert.equal(caption.replaceCaptionWords(sample,{enabled:false,rules}),sample,
  'No opt-in must preserve unmodified user text');
assert.equal(caption.replaceCaptionWords('WTF BS',rules),'WTF BS',
  'Do not corrupt already abbreviated text');
assert.equal(caption.replaceCaptionWords('foo_fuck foo-fuck',rules),'foo_fuck foo-f*ck',
  'Underscore stays within words, hyphen is a boundary');
const post='TOMEX 🇳🇬';
const composed=caption.composeChannelCaption({
 text:'A fun moment',replacements:{enabled:false},poster:{enabled:true,name:post}
});
assert.equal(composed,'A fun moment\n\n> '+post);
assert.ok(!composed.includes(channelName),'Poster quote never secretly becomes channel display name');
assert.equal(caption.composeChannelCaption({
 text:'',poster:{enabled:true,name:post}}),'> '+post,'Optional empty main caption is valid');
assert.equal(caption.composeChannelCaption({
 text:'text',poster:{enabled:false,name:post}}),'text','Poster OFF leaves no signature');
assert.throws(()=>caption.composeChannelCaption({
 text:'text',poster:{enabled:true,name:''}}),/Poster signature/);
assert.throws(()=>caption.composeChannelCaption({
 text:'x'.repeat(2100),maxUtf8Bytes:2048
}),/budget/,'Caption byte limits are enforced');
assert.throws(()=>caption.replaceCaptionWords('foo',{
 enabled:true,rules:Array.from({length:33},()=>({from:'test',to:'foo'}))
}),/at most 32/);
await assert.rejects(()=>watermark.makeReusableWatermark({
 channelId:jid,displayName:channelName
}),/emoji/i,'Unverified color emoji font quality must fail closed');
await assert.rejects(()=>watermark.watermarkStaticImage({
 input:Buffer.alloc(8*1024*1024+1),channelId:jid,displayName:'Hi'
}),/8 MiB/,'Unbounded media is refused before decoding');
const cache=new watermark.WatermarkCache({maxEntries:2,maxBytes:1024});
const key1='foo',key2='bar',key3='baz';
for(const k of [key1,key2,key3])cache.set(k,{buffer:Buffer.alloc(40),channelId:jid});
assert.equal(cache.get(key1),null,'LRU evicts older entries under cap');
assert.ok(cache.get(key3));
cache.invalidateChannel(jid);
assert.equal(cache.bytes,0,'Disconnect/name change can destroy per-channel cached watermark');
console.log('PASS D branding contract: poster vs verified name, graphemes/ZWJ, opt-in substitutions, UTF-8 limits, emoji fails closed and LRU');
