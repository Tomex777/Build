// Shared, side-effect-free transform for owner preview AND final send.
// NEVER authorization, storage, scheduling or actual publication.
import {composeChannelCaption} from './caption.mjs';
import {watermarkStaticImage} from './watermark.mjs';

export async function prepareChannelContent({
  text='',mediaBuffer=null,channelId,verifiedDisplayName,
  posterName='',sourceKind='manual',branding={},maxUtf8Bytes=2048,
  rendererVerifiedEmoji=false,watermarkCache=null,
  // C supplies only a verified, owner-approved server-stored asset, never raw UI bytes.
  approvedFallbackPng=null,
  sharpLibrary=null,canvasLibrary=null,
  transform=watermarkStaticImage
}={}){
  if(typeof text!=='string')throw new TypeError('Post caption must be text.');
  if(mediaBuffer!==null&&!Buffer.isBuffer(mediaBuffer))throw new TypeError('Post media must be a Buffer.');
  if(branding===null||typeof branding!=='object'||Array.isArray(branding))throw new TypeError('Invalid branding config.');
  const fallbackOk=approvedFallbackPng===null||Buffer.isBuffer(approvedFallbackPng)||
    (typeof approvedFallbackPng==='object'&&!Array.isArray(approvedFallbackPng)&&
      Buffer.isBuffer(approvedFallbackPng.light)&&Buffer.isBuffer(approvedFallbackPng.dark));
  if(!fallbackOk)
    throw new TypeError('Approved watermark fallback must be a server-verified PNG Buffer or approved light/dark pair.');
  if(branding.captionReplacementsEnabled===true && branding.captionReplacements!==undefined &&
      !Array.isArray(branding.captionReplacements))throw new TypeError('Invalid saved caption replacement rules.');
  if(!['manual','automation'].includes(sourceKind))throw new Error('Invalid content authorship mode.');
  // A's website currently stores branding.posterName; earlier D schema proposed
  // signaturePosterName. Accept either until the signed C config contract
  // standardises on posterName. Conflicting nonempty values MUST fail closed.
  const uiPersona=branding.posterName;
  const legacyPersona=branding.signaturePosterName;
  if(sourceKind==='automation'&&branding.signatureEnabled===true&&
      typeof uiPersona==='string'&&uiPersona.trim()&&
      typeof legacyPersona==='string'&&legacyPersona.trim()&&
      uiPersona!==legacyPersona){
    throw new Error('Conflicting saved automatic poster identities.');
  }
  const automaticPersona=(typeof uiPersona==='string'&&uiPersona.trim()?uiPersona:legacyPersona)||'';
  const caption=composeChannelCaption({
    text,maxUtf8Bytes,
    replacements:{enabled:branding.captionReplacementsEnabled===true,
      rules:Array.isArray(branding.captionReplacements)?branding.captionReplacements:[]},
    poster:{enabled:branding.signatureEnabled===true,
      name:sourceKind==='manual'?posterName:automaticPersona}
  });
  if(branding.watermarkEnabled!==true||mediaBuffer===null){
    // Avoid touching binary data for OFF modules; identity of media remains intact.
    return {caption,mediaBuffer,watermarked:false,watermarkAssetKey:null};
  }
  // Owner-provided channel name is forbidden. C MUST provide a value from
  // verified original WhatsApp metadata, and fail closed on account/admin loss.
  if(typeof verifiedDisplayName!=='string'||!verifiedDisplayName.trim()){
    throw new Error('Cannot watermark without verified channel display name.');
  }
  const composited=await transform({
    input:mediaBuffer,channelId,displayName:verifiedDisplayName,
    opacity:branding.watermarkOpacity??.22,
    position:branding.watermarkPosition||'auto',
    styleVersion:branding.watermarkStyleVersion??1,
    colorEmojiVerified:rendererVerifiedEmoji,
    cache:watermarkCache,approvedFallbackPng,sharpLibrary,canvasLibrary
  });
  if(!Buffer.isBuffer(composited?.buffer)||!composited?.assetKey)throw new Error('Image compositor failed closed.');
  return {caption,mediaBuffer:composited.buffer,watermarked:true,
    watermarkAssetKey:composited.assetKey,
    imageFormat:composited.format,watermarkPosition:composited.position,
    warnings:composited.warnings||[]};
}
