import assert from 'node:assert/strict';
import {prepareChannelContent} from './prepare.mjs';
const channelId='120363456789012345@newsletter';
const verifiedDisplayName='NIGHT 👩🏽‍💻  قمر 🇳🇬';
const media=Buffer.from('FAKE_MEDIA_FIXTURE_ONLY');
let calls=0;
const transform=async opts=>{
  calls++;
  assert.strictEqual(opts.input,media);
  assert.equal(opts.channelId,channelId);
  assert.equal(opts.displayName,verifiedDisplayName,'Never swap channel name for author');
  assert.equal(opts.opacity,.20);
  assert.equal(opts.position,'top-left');
  return {buffer:Buffer.from('COMPOSITED'),assetKey:'layer-hash',format:'png',position:'top-left',warnings:[]};
};
const unmodified=await prepareChannelContent({text:'what the fuck',mediaBuffer:media});
assert.equal(unmodified.caption,'what the fuck');
assert.strictEqual(unmodified.mediaBuffer,media);
assert.equal(unmodified.watermarked,false);
assert.equal(calls,0,'ALL modules default OFF; no invocation');
const edited=await prepareChannelContent({
  text:'what the fuck',mediaBuffer:media,channelId,verifiedDisplayName,
  posterName:'TOMEX',branding:{
    captionReplacementsEnabled:true,captionReplacements:[{from:'what the fuck',to:'WTF'}],
    signatureEnabled:true,watermarkEnabled:true,
    watermarkOpacity:.20,watermarkPosition:'top-left'
  },transform
});
assert.equal(edited.caption,'WTF\n\n> TOMEX');
assert.equal(edited.watermarked,true);
assert.equal(edited.mediaBuffer.toString(),'COMPOSITED');
assert.equal(calls,1);
const textOnly=await prepareChannelContent({text:'Hello',posterName:'TOMEX',
  branding:{watermarkEnabled:true,signatureEnabled:true},channelId,verifiedDisplayName});
assert.equal(textOnly.caption,'Hello\n\n> TOMEX');
assert.equal(textOnly.watermarked,false);
assert.equal(calls,1);
await assert.rejects(()=>prepareChannelContent({
  text:'Hello',mediaBuffer:media,channelId,
  branding:{watermarkEnabled:true},transform
}),/verified channel display/);
await assert.rejects(()=>prepareChannelContent({
  mediaBuffer:media,channelId,verifiedDisplayName,
  branding:{watermarkEnabled:true},transform:async()=>({success:true})
}),/failed closed/);
assert.equal(calls,1,'Failed closed without side-effectful fake post');
await assert.rejects(()=>prepareChannelContent({
  text:'Hello',sourceKind:'manual',
  branding:{signatureEnabled:true,signaturePosterName:'Owner persona'}
}),/Poster signature requires/,'Manual posts cannot impersonate configured automatic persona');
const auto=await prepareChannelContent({
  text:'Hello',sourceKind:'automation',
  branding:{signatureEnabled:true,signaturePosterName:'Curator Ada'}
});
assert.equal(auto.caption,'Hello\n\n> Curator Ada');
const uiAuto=await prepareChannelContent({
  text:'Hello',sourceKind:'automation',
  branding:{signatureEnabled:true,posterName:'TOMEX'}
});
assert.equal(uiAuto.caption,'Hello\n\n> TOMEX');
await assert.rejects(()=>prepareChannelContent({
  text:'Hello',sourceKind:'automation',
  branding:{signatureEnabled:true,posterName:'Ada',signaturePosterName:'Sade'}
}),/Conflicting/,'Two diverging author fields cannot silently pick a different identity');
// An approved PNG for an emoji channel must reach BOTH preview and final send.
// The binary is supplied by authenticated server-side storage, never trusted UI input.
const fallback=Buffer.from('STUB_OWNER_APPROVED_PNG');
const withFallback=await prepareChannelContent({
  text:'NIGHT',mediaBuffer:media,channelId,verifiedDisplayName,
  branding:{watermarkEnabled:true},approvedFallbackPng:fallback,
  transform:async options=>{
    assert.strictEqual(options.approvedFallbackPng,fallback);
    assert.equal(options.colorEmojiVerified,false,'Fallback must not spoof host emoji verification');
    assert.equal(options.displayName,verifiedDisplayName,'Full ZWJ identity preserved');
    return {buffer:Buffer.from('COMPOSITED_FALLBACK'),assetKey:'fallback-hash'};
  }
});
assert.equal(withFallback.mediaBuffer.toString(),'COMPOSITED_FALLBACK');
assert.equal(withFallback.watermarked,true);
const pairedAssets={light:fallback,dark:Buffer.from('DARK_INK_APPROVED_PNG')};
const paired=await prepareChannelContent({
  mediaBuffer:media,channelId,verifiedDisplayName,
  branding:{watermarkEnabled:true},approvedFallbackPng:pairedAssets,
  transform:async options=>{
    assert.strictEqual(options.approvedFallbackPng,pairedAssets,'Do not lose approved contrast pair');
    return {buffer:Buffer.from('PAIR_COMPOSITED'),assetKey:'pair-hash'};
  }
});
assert.equal(paired.mediaBuffer.toString(),'PAIR_COMPOSITED');
await assert.rejects(()=>prepareChannelContent({
  branding:{watermarkEnabled:false},approvedFallbackPng:'data:image/png;base64,...'
}),/server-verified PNG Buffer/,'Never accept client-supplied fallback string');
await assert.rejects(()=>prepareChannelContent({
  text:'Hi',branding:{captionReplacementsEnabled:true,captionReplacements:'incorrect'}
}),/Invalid saved caption replacement rules/,'Malformed enabled replacement must fail closed');
console.log('Channel branding preview/send transform contract: PASS');
