import { githubLookup } from '../../utility-services.js'
export default {
  name:'github',
  aliases:['gh'],
  description:'Look up a GitHub user or repository.',
  usage:'.github <user|owner/repo>',
  async run(ctx) {
    const query = ctx.args.join(' ').trim()
    if (!query) return ctx.reply('Use .github <user> or .github <owner/repo>.')
    try {
      const result = await githubLookup(query)
      const d = result.data || {}
      if (result.type === 'repo') {
        return ctx.reply([
          '🐙 *' + d.full_name + '*',
          d.description || '',
          '★ ' + Number(d.stargazers_count || 0).toLocaleString() + ' · Forks ' + Number(d.forks_count || 0).toLocaleString() + ' · Issues ' + Number(d.open_issues_count || 0).toLocaleString(),
          d.language ? 'Language: ' + d.language : '',
          d.license?.spdx_id ? 'License: ' + d.license.spdx_id : '',
          d.html_url || '',
        ].filter(Boolean).join('\n'))
      }
      return ctx.reply([
        '🐙 *' + (d.name || d.login) + '*',
        '@' + d.login,
        d.bio || '',
        'Repos ' + Number(d.public_repos || 0) + ' · Followers ' + Number(d.followers || 0).toLocaleString() + ' · Following ' + Number(d.following || 0).toLocaleString(),
        d.location ? '📍 ' + d.location : '',
        d.html_url || '',
      ].filter(Boolean).join('\n'))
    } catch (error) { return ctx.reply(error?.message || 'GitHub lookup failed.') }
  },
}
