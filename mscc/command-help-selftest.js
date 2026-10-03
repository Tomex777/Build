import assert from 'node:assert/strict'
import {
  commandHelpText,
  helpIndexText,
  resolveHelpCommand,
} from './command-help.js'

const commands = [
  { name:'help', aliases:['commands'], capability:'core', description:'Help', usage:'.help [command]' },
  { name:'menu', capability:'core', description:"Josia menu", usage:'.menu', profileOnly:'josiah' },
  { name:'library', aliases:['lib'], capability:'core', description:'Library', usage:'.library' },
  { name:'source', capability:'core', description:'Sources', usage:'.source <folder> [source|clear]' },
  { name:'sources', capability:'core', description:'Installed sources', usage:'.sources [folder]' },
  { name:'delivery', capability:'core', description:'Delivery defaults', usage:'.delivery <folder>' },
  { name:'nami', capability:'anime', description:'Nami menu', usage:'.nami', profileOnly:'nami' },
  { name:'anime', capability:'anime', description:'Anime search', usage:'.anime <title>' },
  { name:'manga', capability:'manga', description:'Manga search', usage:'.manga <title>' },
  { name:'mimi', capability:'music', description:'MiMi menu', usage:'.mimi', profileOnly:'mimi' },
  { name:'song', aliases:['music','play'], capability:'music', description:'Music search', usage:'.song <query>' },
  { name:'movie', aliases:['movies','film'], capability:'movies', description:'Movie search', usage:'.movie <title>' },
  { name:'tv', aliases:['series','show'], capability:'tv', description:'TV search', usage:'.tv <title>' },
  { name:'youtube', aliases:['yt'], capability:'media', description:'YouTube', usage:'.youtube <search> [--doc]' },
  { name:'image', aliases:['img'], capability:'search', description:'Images', usage:'.image <search> [amount]' },
  { name:'web', aliases:['www'], capability:'search', description:'Web', usage:'.web <search>' },
  { name:'calc', aliases:['calculate'], capability:'general', description:'Calculator', usage:'.calc <expression>', help:'Supports arithmetic.' },
  { name:'profile', aliases:['pf'], capability:'general', description:'User profile', usage:'.profile [@user]' },
  { name:'tweet', capability:'fun', description:'Tweet card', usage:'.tweet [dark|light] [@user] <text>' },
  { name:'post', capability:'fun', description:'Social post card', usage:'.post [instagram|facebook|story] [@user] <text>' },
]

const ctx = {
  publicPrefix:'.',
  listSources:capability => capability === 'anime'
    ? [
        { id:'kayoanime', name:'KayoAnime', primary:true },
        { id:'animesogo', name:'AnimeSogo', primary:false },
      ]
    : [],
  sourceMode:capability => capability === 'anime' ? 'managed' : 'user-choice',
  getSourceDefault:() => '',
  getDeliveryDefault:capability => capability === 'anime'
    ? { quality:'720', delivery:'document' }
    : null,
}

assert.equal(resolveHelpCommand(commands, 'yt')?.name, 'youtube')
assert.equal(resolveHelpCommand(commands, '.movies')?.name, 'movie')
assert.equal(resolveHelpCommand(commands, 'series')?.name, 'tv')
assert.equal(resolveHelpCommand(commands, 'music')?.name, 'song')
assert.equal(resolveHelpCommand(commands, 'does-not-exist'), null)

const anime = commandHelpText(ctx, resolveHelpCommand(commands, 'anime'), commands)
assert(anime.includes('1-10'))
assert(anime.includes('Manga instant reply'))
assert(anime.includes('Add to Library'))
assert(anime.includes('KayoAnime (primary)'))
assert(anime.includes('AnimeSogo (fallback)'))
assert(anime.includes('720 · document'))

const youtube = commandHelpText(ctx, resolveHelpCommand(commands, 'yt'), commands)
assert(youtube.includes('YouTube URL/video ID directly'))
assert(youtube.includes('--doc'))
assert(youtube.includes('.yt https://youtu.be/'))

const menu = commandHelpText(ctx, resolveHelpCommand(commands, 'menu'), commands)
assert(menu.includes("Josia's menu"))
assert(menu.includes('separate from .help'))
assert(menu.includes('.nami'))
assert(menu.includes('.mimi'))

const nami = commandHelpText(ctx, resolveHelpCommand(commands, 'nami'), commands)
assert(nami.includes('Anime + Manga'))
assert(nami.includes('two-image'))

const mimi = commandHelpText(ctx, resolveHelpCommand(commands, 'mimi'), commands)
assert(mimi.includes('Music + Movies + TV'))
assert(mimi.includes('two-image'))

const tweet = commandHelpText(ctx, resolveHelpCommand(commands, 'tweet'), commands)
assert(tweet.includes('tweet-like image card'))
assert(tweet.includes('tiny transparent Night watermark'))
assert(tweet.includes('.tweet dark'))

const post = commandHelpText(ctx, resolveHelpCommand(commands, 'post'), commands)
assert(post.includes('instagram'))
assert(post.includes('facebook'))
assert(post.includes('story'))
assert(post.includes('tiny transparent Night watermark'))

const profile = commandHelpText(ctx, resolveHelpCommand(commands, 'pf'), commands)
assert(profile.includes('User profile'))
assert(profile.includes('.profile @user'))
assert(profile.includes('Library stats'))

const generic = commandHelpText(ctx, resolveHelpCommand(commands, 'calc'), commands)
assert(generic.includes('Calculator'))
assert(generic.includes('.calculate'))
assert(generic.includes('How it works'))

const index = helpIndexText(ctx, commands)
assert(index.includes('*Night Help*'))
assert(!index.includes('MSCC'))
assert(index.includes('Personality menus: .menu · .nami · .mimi'))
assert(index.includes('.anime'))
assert(index.includes('.youtube'))
assert(index.includes('Use .help <command>'))

console.log('PASS comprehensive help pages, aliases, dynamic sources, and distinct personality menus')
