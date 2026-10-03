const FALLBACKS = [
  'You have the confidence of a software update nobody asked for.',
  'You bring main-character energy to side-quest decisions.',
  'Your plans have more loading screens than progress.',
  'You could lose an argument with your own autocorrect.',
  'Even your excuses need a patch update.',
]

export default {
  name:'roast',
  description:'Get a harsh-but-playful Josia roast.',
  usage:'.roast [@user|subject]',
  async run(ctx) {
    let subject = ctx.args.join(' ').trim()
    let mentionJid = ''
    const first = String(ctx.args?.[0] || '').trim()

    if (first.startsWith('@') || (!subject && ctx.groupKey)) {
      try {
        const target = await ctx.resolveCommandTarget?.(first)
        if (target?.phoneNumber) {
          const profile = await ctx.getPublicUserProfile?.(target.phoneNumber)
          subject = String(profile?.displayName || target.phoneNumber)
          mentionJid = String(profile?.mentionJid || target.phoneNumber + '@s.whatsapp.net')
        }
      } catch {}
    }

    if (!subject) subject = 'you'

    let roast = ''
    if (typeof ctx.smartComplete === 'function') {
      try {
        const result = await ctx.smartComplete({
          system:[
            'Write one sharp, harsh, funny roast for the named subject.',
            'Keep it playful rather than threatening.',
            'Do not use slurs or attack protected traits, disability, health, trauma, or family tragedy.',
            'One or two sentences. No explanation.',
          ].join(' '),
          messages:[{ role:'user', content:'Roast: ' + subject }],
          allowWeb:false,
          temperature:0.9,
          maxTokens:160,
          reasoningEffort:'low',
        })
        roast = String(result?.text || '').trim()
      } catch {}
    }
    if (!roast) roast = FALLBACKS[Math.floor(Math.random() * FALLBACKS.length)]

    if (mentionJid && ctx.groupKey && ctx.account?.sock) {
      const phone = mentionJid.split('@')[0].split(':')[0]
      return ctx.account.sock.sendMessage(ctx.groupKey, {
        text:'🔥 @' + phone + '\n' + roast,
        mentions:[mentionJid],
      }, { quoted:ctx.message })
    }
    return ctx.reply('🔥 ' + roast)
  },
}
