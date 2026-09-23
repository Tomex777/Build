export default {
  name: 'reloadcommands',
  aliases: ['reloadcmds', 'cmdreload'],
  description: 'Reload command modules after adding or editing files in the commands folder.',
  ownerOnly: true,
  async run(ctx) {
    const names = await ctx.reloadCommands()
    await ctx.reply(`✅ Reloaded ${names.length} MSCC commands.\n${names.map(name => '.' + name).join('  ')}`)
  },
}
