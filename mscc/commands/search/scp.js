import { fetchScp, scpMessage } from '../../utils/scp-lookup.js'

export default {
  name:'scp',
  description:'Explore an SCP dossier from the official Foundation Wiki, with source and licensing.',
  usage:'.scp <number|random|help>',
  async run(ctx) {
    const args=Array.isArray(ctx.args)?ctx.args:[]
    const input=String(args[0] || '').trim()
    if (!input || input.toLowerCase()==='help') return ctx.reply([
      '☢️ *SCP FOUNDATION ARCHIVE*',
      '.scp 682 — Read an SCP dossier',
      '.scp SCP-049 — Also supported',
      '.scp random — Discover a random file',
      '',
      'Source-linked descriptions only; premium collectible cards are being built separately.',
    ].join('\n'))
    try {
      const random=input.toLowerCase()==='random'
      let dossier=null
      for(let i=0;i<(random?3:1);i++) {
        const id=random?String(1+Math.floor(Math.random()*6000)):input
        dossier=await fetchScp(id)
        if(dossier)break
      }
      if (!dossier)return ctx.reply('That SCP file was not found or has no readable dossier. Try another number.')
      return ctx.reply(scpMessage(dossier))
    } catch (error) {
      return ctx.reply((error?.name==='TimeoutError'||error?.name==='AbortError')
        ? 'SCP Wiki took too long to respond. Please try again.'
        : (error?.message || 'SCP lookup failed. Please try later.'))
    }
  },
}
