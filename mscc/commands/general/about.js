export default {
  name:'about',
  description:'Show what Night is and who the three personalities are.',
  usage:'.about',
  async run(ctx) {
    return ctx.reply([
      '🌙 *Night*',
      'A multi-personality WhatsApp companion built around useful commands, media, group tools, and natural conversation.',
      '',
      '◇ *Josia* — general tools, search, utilities, group help, and everyday requests.',
      '✦ *Nami* — anime and manga.',
      '✧ *MiMi* — music, movies, and TV.',
      '',
      'Use *.help* for the full command directory.',
      'Use *.menu*, *.nami*, or *.mimi* for personality-specific menus.',
      ctx.appVersion ? 'Version: ' + ctx.appVersion : '',
    ].filter(Boolean).join('\n'))
  },
}
