// The only C↔D formatting bridge. D owns the side-effect-free renderer;
// C owns all owner/account/admin/rights checks and the actual send gate.
// Import lazily so a missing D PR or unverified emoji fonts fails closed.
import { ChannelError } from '../night-play-channels.js'
let formatter=null,cache=null

export async function prepareApprovedChannelContent({text,mediaBuffer,channelId,verifiedDisplayName,sourceKind='automation',branding}={}) {
  if(!Buffer.isBuffer(mediaBuffer)||mediaBuffer.length>192*1024||typeof text!=='string')
    throw new ChannelError(400,'Unsafe stored channel preview source.')
  try {
    if(!formatter) {
      const [{prepareChannelContent},{WatermarkCache}]=await Promise.all([
        import('../channel-branding/prepare.mjs'),
        import('../channel-branding/watermark.mjs')
      ])
      formatter=prepareChannelContent
      cache=new WatermarkCache({maxEntries:12,maxBytes:6*1024*1024})
    }
  } catch {
    throw new ChannelError(503,'Channel post formatter has not passed the protected integration gate.')
  }
  try {
    // Color emoji rendering remains DISABLED until native target-host visual proof.
    // A verified owner-approved fallback can be added through a separate protected feature.
    const result=await formatter({
      text,mediaBuffer,channelId,verifiedDisplayName,sourceKind,branding,
      rendererVerifiedEmoji:false,watermarkCache:cache,maxUtf8Bytes:2048
    })
    if(!result||typeof result.caption!=='string'||!Buffer.isBuffer(result.mediaBuffer)
      ||result.mediaBuffer.length>8*1024*1024)
      throw new Error('Incorrect media output.')
    return {caption:result.caption,mediaBuffer:result.mediaBuffer}
  } catch(error) {
    throw new ChannelError(503,'NIGHT could not prepare this exact approved post: '+String(error?.message||'unknown').slice(0,180))
  }
}

export function invalidateChannelWatermarkCache(channelId) {
  cache?.invalidateChannel(channelId)
}
