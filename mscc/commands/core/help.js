import {
  commandHelpText,
  helpIndexText,
  resolveHelpCommand,
} from '../../command-help.js'

export default {
  name:'help',
  aliases:['commands'],
  description:'Show the public command index or one complete command/system help page.',
  usage:'.help [command]',
  help:'Use .help <command> to get the full flow, examples, related commands, installed sources, and saved download preference where relevant.',
  async run(ctx) {
    const prefix = ctx.publicPrefix || '.'
    const requested = String(ctx.args[0] || '').trim()
    const commands = ctx.commandList()

    if (!requested) {
      return ctx.reply(helpIndexText(ctx, commands))
    }

    const command = resolveHelpCommand(commands, requested)
    if (!command) {
      return ctx.reply(
        'Unknown command: ' + prefix + requested.replace(/^\./, '') +
        '\n\nUse ' + prefix + 'help to see the commands that are actually installed.'
      )
    }

    return ctx.reply(commandHelpText(ctx, command, commands))
  },
}
