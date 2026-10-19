import { liveCommandAudit, authorizeTest, isGroupTest } from './command-live-audit.js'
import { readdir } from 'node:fs/promises'
import { spawn } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { dispatchCommand, loadCommands } from './command-registry.js'

const ROOT = fileURLToPath(new URL('./', import.meta.url))
const PUBLIC_URL = new URL('./commands/', import.meta.url)
const PRIVATE_URL = new URL('./private-commands/', import.meta.url)
const MAX_MESSAGE = 2800

// Explicit regression coverage. A related passing unit suite is NOT proof of
// a successful WhatsApp send or external provider response.
const SUITE_LINKS = {
  anime:['anime-sources-selftest.js'], manga:['manga-flow-selftest.js','manga-sources-selftest.js'],
  movie:['movie-flow-selftest.js'], tv:['tv-flow-selftest.js'],
  song:['music-flow-selftest.js','music-sources-selftest.js'],
  lyrics:['lyrics-selftest.js'], youtube:['youtube-video-selftest.js','youtube-dlp-selftest.js'],
  apk:['apk-flow-selftest.js'], book:['book-flow-selftest.js'],
  course:['course-flow-selftest.js'], library:['media-library-selftest.js'],
  help:['command-help-selftest.js'], squidgame:['squid-game-selftest.js'],
  wordchain:['wordchain-scp-selftest.js'], scp:['wordchain-scp-selftest.js'],
  sticker:['sticker-rounding-selftest.js','sticker-metadata-selftest.js'],
  sfx:['sfx-selftest.js'], tiktok:['social-download-selftest.js','tiktok-fallback-selftest.js'],
  dl:['social-download-selftest.js','tiktok-fallback-selftest.js'],
  meme:['meme-selftest.js'], darkmeme:['meme-selftest.js'],
  checkers:['checkers-selftest.js'], chess:['chess-selftest.js'],
  ludo:['ludo-selftest.js'], tictactoe:['tictactoe-selftest.js'],
  poll:['committed-commands-selftest.js'], profile:['profile-command-selftest.js'],
  getpp:['profile-presentation-selftest.js'],
  tweet:['social-card-selftest.js'], post:['social-card-selftest.js'],
  guesssong:['party-games-selftest.js'], guessanime:['party-games-selftest.js'],
  speedtype:['party-games-selftest.js'], removebg:['media-tools-selftest.js'],
  bestframe:['extended-media-selftest.js'],
  reverse:['extended-media-selftest.js'], stabilize:['extended-media-selftest.js'],
  emojimix:['extended-media-selftest.js'], getexif:['extended-media-selftest.js'],
  ssweb:['extended-media-selftest.js'], denoise:['media-tools-selftest.js'],
  extractaudio:['extended-media-selftest.js'],
  img2pdf:['extended-media-selftest.js'], pdf2img:['extended-media-selftest.js'],
  pdfmerge:['extended-media-selftest.js'], pdfsplit:['extended-media-selftest.js'],
  scanqr:['extended-media-selftest.js'], upscale:['upscale-media-selftest.js'],
}
const SMOKES = {
  ping:{ args:[], matches:text => text.trim().length > 2 },
  about:{ args:[], matches:text => /night/i.test(text) },
  calc:{ args:['2+2'], matches:text => /4/.test(text) },
}

export async function discoverAuditInventory() {
  const [publicRegistry, privateRegistry, files] = await Promise.all([
    loadCommands(PUBLIC_URL, {capabilityFromDirectory:true}),
    loadCommands(PRIVATE_URL),
    readdir(ROOT),
  ])
  const suites = files.filter(file => /^[\w-]+-selftest\.js$/i.test(file)).sort()
  const suiteSet = new Set(suites)
  const commands = [
    ...publicRegistry.canonical.map(command => ({scope:'public',command})),
    ...privateRegistry.canonical.map(command => ({scope:'private',command})),
  ].sort((a,b) => a.scope.localeCompare(b.scope) || a.command.name.localeCompare(b.command.name))
  for (const row of commands) {
    row.suites = [...new Set([...(SUITE_LINKS[row.command.name] || []),
      row.command.name + '-selftest.js'])].filter(name => suiteSet.has(name))
  }
  return { commands, suites, publicRegistry, privateRegistry }
}

export async function checkCommandRoutes({ commands, publicRegistry, privateRegistry }) {
  const results = new Map()
  for (const {command,scope} of commands) {
    const registry = scope === 'private' ? privateRegistry : publicRegistry
    const prefixes = [command.name, ...(command.aliases || [])]
    let failure = ''
    for (const alias of prefixes) {
      const replies = []
      const ctx = {
        privateControl:true,
        publicCommandsEnabled:true,
        isPublicOwner:true,
        isSupremeOwner:true,
        isSessionOwner:true,
        isGroupAdmin:true,
        botProfile:{id:command.profileOnly || 'josiah'},
        reply:async text => { replies.push(String(text)); return true },
      }
      try {
        // "help" is intercepted by the registry BEFORE any command.run().
        // This safely tests every command and alias without touching chats,
        // admin settings, media, downloads, game state or third-party APIs.
        const handled = await dispatchCommand(registry,'.' + alias + ' help',ctx,{scope,prefix:'.'})
        if (!handled || !replies.some(text => text.includes('.' + command.name))) {
          failure = 'routing/help failed: ' + alias
          break
        }
      } catch (error) { failure = 'routing error: ' + alias; break }
    }
    results.set(scope + ':' + command.name, failure)
  }
  return results
}

export async function runSafeCommandSmokes(commands) {
  const result = new Map()
  for (const {command,scope} of commands) {
    if (scope !== 'public' || !Object.hasOwn(SMOKES,command.name)) continue
    const {args,matches} = SMOKES[command.name]
    const replies = []
    let timer
    try {
      await Promise.race([
        command.run({
          args,rawArgs:args.join(' '),publicPrefix:'.',
          botProfile:{id:'josiah',displayName:'NIGHT'},
          appVersion:'diagnostic',
          reply:async text => { replies.push(String(text)); return true },
        }),
        new Promise((_,reject) => { timer = setTimeout(() => reject(new Error('timeout')),1500) }),
      ])
      result.set(command.name,replies.some(matches) ? 'pass' : 'fail')
    } catch { result.set(command.name,'fail') }
    finally { clearTimeout(timer) }
  }
  return result
}

export function runAuditSuite(file,{signal,timeoutMs=45000}={}) {
  return new Promise(resolve => {
    if (signal?.aborted) return resolve({ok:false,cancelled:true,detail:'cancelled'})
    let settled = false
    let timedOut = false
    let stderr = ''
    let child
    let timer
    const started = Date.now()
    const abort = () => { if (child && !child.killed) child.kill('SIGKILL') }
    const finish = value => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      signal?.removeEventListener('abort',abort)
      resolve({...value,ms:Date.now()-started})
    }
    try {
      // Never inherit Azure keys, session tokens, DB configuration, etc.
      child = spawn(process.execPath,['--max-old-space-size=128',file],{
        cwd:ROOT,
        env:{
          PATH:process.env.PATH || '/usr/bin:/bin',
          HOME:'/tmp',TMPDIR:'/tmp', NODE_ENV:'test',CI:'true',
        },
        stdio:['ignore','ignore','pipe'],
      })
      child.stderr.on('data',part => { stderr=(stderr+part.toString()).slice(-500) })
      child.on('error',error => finish({ok:false,detail:error.message}))
      child.on('close',code => finish({
        ok:code === 0 && !timedOut && !signal?.aborted,
        cancelled:Boolean(signal?.aborted),
        detail:signal?.aborted ? 'cancelled' : timedOut ? 'timeout' :
          code === 0 ? '' : 'exit '+code+(stderr ? ': '+stderr.replace(/\s+/g,' ').slice(-100) : ''),
      }))
      timer=setTimeout(() => {timedOut=true; abort()},timeoutMs)
      signal?.addEventListener('abort',abort,{once:true})
      if (signal?.aborted) abort()
    } catch(error) { finish({ok:false,detail:error?.message || 'process spawn error'}) }
  })
}

function splitMessages(lines,max=MAX_MESSAGE) {
  const messages=[]
  let buffer=''
  for (const raw of lines) {
    const line=String(raw).slice(0,max-1)
    if (buffer && buffer.length+line.length+1>max) {
      messages.push(buffer)
      buffer=''
    }
    buffer+=(buffer ? '\n' : '')+line
  }
  if (buffer) messages.push(buffer)
  return messages
}

export function renderAudit({ commands,suites, routes,smokes,outcomes }) {
  const tally={smoke:0,regression:0,route:0,failed:0}
  const detail=[]
  for (const {command,scope,suites:linked} of commands) {
    const routeFailure=routes.get(scope+':'+command.name)
    const broken=linked.filter(file => outcomes.get(file) && !outcomes.get(file).ok)
    const checked=linked.filter(file => outcomes.get(file)?.ok)
    const smoked=scope === 'public' ? smokes.get(command.name) : undefined
    let icon,category
    if (routeFailure || broken.length || smoked === 'fail') {icon='❌';category='failed'}
    else if (smoked === 'pass') {icon='🟢';category='smoke'}
    else if (checked.length) {icon='🧪';category='regression'}
    else {icon='⚪';category='route'}
    tally[category]++
    detail.push(icon+' .'+command.name+(scope === 'private' ? ' [control]' : '')+
      (routeFailure ? ' — '+routeFailure : '')+
      (broken.length ? ' — '+broken.join(', ') : ''))
  }
  const suiteFailures=[...outcomes.entries()].filter(([,v])=>!v.ok && !v.cancelled)
  const lines=[
    '🌙 *NIGHT — FULL COMMAND AUDIT*',
    'Commands: '+commands.length+' | Regression suites: '+
      [...outcomes.values()].filter(v=>v.ok).length+'/'+suites.length+' passed',
    '🟢 Executed safe command logic: '+tally.smoke,
    '🧪 Linked regression passed: '+tally.regression,
    '⚪ Registered + route tested only: '+tally.route,
    '❌ Failed: '+tally.failed,
    '',
    '*Every registered command*',
    ...detail,
    '',
    '*Important:* 🟢 = safe local function test; 🧪 = associated unit test passed; ⚪ = routing/help works but operation not verified; ❌ = test failed.',
    'This run DOES NOT fake provider or WhatsApp success. Media delivery, external sources, games, group/admin and account mutations require separate live controlled tests.',
  ]
  if (suiteFailures.length) lines.push('','*Failing regression suites*',
    ...suiteFailures.map(([name,result])=>'❌ '+name+(result.detail?' ('+result.detail+')':'')))
  return splitMessages(lines)
}

export function createCommandAuditManager({
  discover=discoverAuditInventory,
  checkRoutes=checkCommandRoutes,
  smoke=runSafeCommandSmokes,
  runSuite=runAuditSuite,
}={}) {
  let active=null
  const status=()=>!active ? 'No NIGHT audit running. Send .testall to start.' :
    '🌙 NIGHT audit: '+active.done+'/'+active.total+' suites; '+
    active.failed+' failures.'+(active.controller.signal.aborted?' Cancelling…':' In progress.')
  const cancel=()=>{
    if (!active || active.controller.signal.aborted) return false
    active.controller.abort()
    return true
  }
  const start=({send})=>{
    if (active) return false
    const job={done:0,total:0,failed:0,controller:new AbortController()}
    active=job
    const safeSend=async message=>{
      try { await send(message) }
      catch(error) {console.warn('[NIGHT testall] report send failed:',error?.message||'unknown')}
    }
    setImmediate(async()=>{
      try {
        const snapshot=await discover()
        job.total=snapshot.suites.length
        const [routes,smokes]=await Promise.all([
          checkRoutes(snapshot),
          smoke(snapshot.commands),
        ])
        await safeSend('🌙 *NIGHT COMMAND AUDIT STARTED*\n'+snapshot.commands.length+
          ' registered commands (public + control).\n'+job.total+
          ' local regression suites queued with max 2 workers. Unsafe operations are never executed.')
        const outcomes=new Map()
        let cursor=0
        const worker=async()=>{
          while(!job.controller.signal.aborted && cursor<snapshot.suites.length){
            const suite=snapshot.suites[cursor++]
            const result=await runSuite(suite,{signal:job.controller.signal})
            outcomes.set(suite,result)
            job.done++
            if (!result.ok && !result.cancelled) job.failed++
            if (job.done%20===0 && !job.controller.signal.aborted)
              await safeSend('🧪 NIGHT audit progress: '+job.done+'/'+job.total+
                ' suites; '+job.failed+' failures.')
          }
        }
        await Promise.all([worker(),worker()])
        if (job.controller.signal.aborted) {
          await safeSend('🛑 NIGHT command audit cancelled at '+job.done+'/'+job.total+
            ' suites. Unfinished tests are not counted as passed.')
          return
        }
        const reports=renderAudit({...snapshot,routes,smokes,outcomes})
        for(let i=0;i<reports.length;i++) {
          await safeSend((reports.length>1 ? '*Report '+(i+1)+'/'+reports.length+'*\n':'')+reports[i])
        }
      } catch(error) {
        await safeSend('❌ NIGHT command audit stopped: '+String(error?.message||error).slice(0,220))
      } finally {if(active===job) active=null}
    })
    return true
  }
  return {start,cancel,status,busy:()=>Boolean(active)}
}

export const commandAudit=createCommandAuditManager()

export async function executeTestAll(ctx) {
  // Enforce owner identity in both the private-control and public route.
  if (ctx?.isSupremeOwner !== true) {
    return ctx.reply('Only the verified NIGHT supreme owner can run command audits.')
  }
  const action=String(ctx.args?.[0]||'').trim().toLowerCase()
  const confirm=String(ctx.args?.[1]||'').trim().toLowerCase()==='confirm'
  if (action==='details') {
    const filter=String(ctx.args?.[1]||'').trim()
    if (filter && !/^\.*[a-z][\w-]{0,40}$/i.test(filter)) {
      return ctx.reply('Use .testall details or .testall details <command>.')
    }
    const lines=liveCommandAudit.details(filter)
    // Each message stays well under WhatsApp's practical text length.
    let buffer=''
    for (const line of lines) {
      if (buffer && buffer.length+line.length+1>2500) {
        await ctx.reply(buffer)
        buffer=''
      }
      buffer+=(buffer?'\n':'')+line
    }
    if (buffer) await ctx.reply(buffer)
    return undefined
  }
  if (action==='status') {
    return ctx.reply(commandAudit.busy() ? commandAudit.status() :
      liveCommandAudit.busy() ? liveCommandAudit.status() :
      commandAudit.status()+' '+liveCommandAudit.status())
  }
  if (action==='cancel') {
    const stopped=commandAudit.cancel() || liveCommandAudit.cancel()
    return ctx.reply(stopped
      ? '🛑 Cancellation requested. NIGHT will stop unfinished checks.'
      : 'No running NIGHT audit to cancel.')
  }
  if (action==='live' || action==='failed') {
    const auth=authorizeTest(ctx,'live',confirm)
    if (auth) return ctx.reply(auth.replace('.testall live confirm',
      action==='failed'?'.testall failed confirm':'.testall live confirm'))
    if (liveCommandAudit.busy() || commandAudit.busy()) {
      return ctx.reply('A NIGHT audit is already running. Use .testall status or .testall cancel.')
    }
    const started=liveCommandAudit.start(ctx,{failedOnly:action==='failed'})
    if (started===null) return ctx.reply('No failed live tests to retry yet. Run .testall live first.')
    return started ? undefined : ctx.reply('A live audit is already running.')
  }
  if (action && action!=='run') {
    return ctx.reply('Use .testall, .testall live, .testall failed, .testall details, .testall status, or .testall cancel.'+
      (isGroupTest(ctx)?'\nIn groups, live and failed tests require the final word confirm.':''))
  }
  if (commandAudit.busy() || liveCommandAudit.busy()) {
    return ctx.reply('A NIGHT audit is already running. Use .testall status or .testall cancel.')
  }
  if (!commandAudit.start({send:text=>ctx.reply(text)})) {
    return ctx.reply('An audit is already running. Use .testall status or .testall cancel.')
  }
  return undefined
}
