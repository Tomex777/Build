import { parseSocialRequest, prepareSocialDownload } from '../../utils/social-download.js'
import { withMediaToolJob } from '../../utils/media-toolkit.js'
import { commandText, contextInfo, quotedMessage } from '../../utils/whatsapp/messages.js'

async function send(ctx, request, document = false) {
  const chat = ctx.message?.key?.remoteJid
  const sock = ctx.account?.sock
  if (!chat || !sock) throw new Error('WhatsApp connection is unavailable.')
  let result
  let sent = 0
  try {
    result = await prepareSocialDownload(request)
    for (const [index,file] of result.files.entries()) {
      const caption = [
        `📥 *NIGHT • ${request.platform}*`,
        result.files.length > 1 ? `Item ${index + 1}/${result.files.length}` : '',
        request.displayUrl,
      ].filter(Boolean).join('\n')
      const payload = document || !['video/mp4','image/jpeg','image/png','image/webp'].includes(file.mime)
        ? { document:{url:file.file}, mimetype:file.mime, fileName:file.name, caption }
        : file.mime.startsWith('image/')
          ? { image:{url:file.file}, mimetype:file.mime, caption }
          : { video:{url:file.file}, mimetype:file.mime, caption }
      await sock.sendMessage(chat,payload,{quoted:ctx.message})
      sent++
    }
    return sent
  } catch (error) {
    if (sent) error.message = `${sent} file(s) sent, but the rest failed: ${error.message}`
    throw error
  } finally { await result?.cleanup?.() }
}

export default {
  name:'dl',
  aliases:['social','tt','tiktok','ig','instagram','fb','facebook','xdl','twitterdl','redditdl'],
  description:'Download public social-media videos and supported photo posts, without paid APIs.',
  usage:'.dl <public post URL> [--doc]',
  help:'TikTok, Instagram, Facebook, X/Twitter, Reddit, Pinterest, Threads and Vimeo. Links must be publicly accessible; private posts and login-only content are not supported. Paste a post URL, or reply to a message containing the URL with .dl.',
  async run(ctx) {
    const args = Array.isArray(ctx.args) ? ctx.args : []
    const document = args.includes('--doc') || args.includes('-d')
    const direct = args.filter(arg => arg !== '--doc' && arg !== '-d').join(' ').trim()
    const info = contextInfo(ctx.message?.message)
    const quoted = quotedMessage(ctx.message, info, ctx.message?.key?.remoteJid)
    const source = direct || commandText(quoted?.message) || ''
    if (!source) return ctx.reply(`Use ${ctx.publicPrefix || '.'}dl <public post URL> [--doc].\nExample: ${ctx.publicPrefix || '.'}ig https://www.instagram.com/reel/EXAMPLE/`)
    let request
    try { request = parseSocialRequest(source) }
    catch (error) { return ctx.reply(error.message) }
    const progress = typeof ctx.progress === 'function'
      ? await ctx.progress(`📥 Downloading from ${request.platform}…`)
      : null
    try {
      const sent = await withMediaToolJob(() => send(ctx,request,document))
      await progress?.done?.(`Sent ${sent} ${request.platform} file${sent === 1 ? '' : 's'}.`)
    } catch (error) {
      console.error('NIGHT social download failed:',error?.message || error)
      await progress?.fail?.(error.message || 'Social-media download failed.')
      if (!progress) await ctx.reply(error.message || 'Social-media download failed.')
    }
  },
}
