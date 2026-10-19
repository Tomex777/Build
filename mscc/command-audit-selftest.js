import assert from 'node:assert/strict'
import {
  createCommandAuditManager,
  checkCommandRoutes,
  runSafeCommandSmokes,
  renderAudit,
  discoverAuditInventory,
  parseTestAllRequest,
} from './command-audit.js'

// Owner's bare .testall must execute real safe WhatsApp tests; offline is explicit.
assert.deepEqual(parseTestAllRequest([]),{mode:'live',confirm:false})
assert.deepEqual(parseTestAllRequest(['live']),{mode:'live',confirm:false})
assert.deepEqual(parseTestAllRequest(['confirm']),{mode:'live',confirm:true})
assert.deepEqual(parseTestAllRequest(['live','confirm']),{mode:'live',confirm:true})
assert.deepEqual(parseTestAllRequest(['audit']),{mode:'audit',confirm:false})
assert.deepEqual(parseTestAllRequest(['run']),{mode:'audit',confirm:false})
assert.deepEqual(parseTestAllRequest(['failed','confirm']),{mode:'failed',confirm:true})
assert.deepEqual(parseTestAllRequest(['status']),{mode:'status',confirm:false})
assert.deepEqual(parseTestAllRequest(['delete']),{mode:'invalid',confirm:false})

// Every command and alias must be resolvable without invoking the command body.
const neverExecute = {name:'unsafe',aliases:['danger'],usage:'.unsafe',async run(){throw Error('destructive run')}}
const reg = {commands:new Map([['unsafe',neverExecute],['danger',neverExecute]]),canonical:[neverExecute]}
const routeRows=[{scope:'public',command:neverExecute,suites:[]}]
const routeResults=await checkCommandRoutes({commands:routeRows,publicRegistry:reg,privateRegistry:{commands:new Map(),canonical:[]}})
assert.equal(routeResults.get('public:unsafe'),'')
const failed=await checkCommandRoutes({commands:routeRows,publicRegistry:{commands:new Map(),canonical:[]},privateRegistry:reg})
assert.match(failed.get('public:unsafe'),/routing\/help/)

const sample={name:'about',async run(ctx){await ctx.reply('NIGHT diagnostics')}}
assert.equal((await runSafeCommandSmokes([{scope:'public',command:sample}])).get('about'),'pass')
const messages=renderAudit({
  commands:[
    {scope:'public',command:{name:'about'},suites:[]},
    {scope:'public',command:{name:'untested'},suites:[]},
    {scope:'public',command:{name:'broken'},suites:['suite.js']},
  ],
  suites:['suite.js'],
  routes:new Map([['public:about',''],['public:untested',''],['public:broken','']]),
  smokes:new Map([['about','pass']]),
  outcomes:new Map([['suite.js',{ok:false,detail:'test error'}]]),
})
assert(messages.join('').includes('🟢 .about'))
assert(messages.join('').includes('⚪ .untested'))
assert(messages.join('').includes('❌ .broken'))
assert(messages.join('').includes('NOT'))

const sent=[]
let inFlight=0, highWater=0
const manager=createCommandAuditManager({
  discover:async()=>({
    commands:[{scope:'public',command:{name:'untested'},suites:[]}],
    suites:['a-selftest.js','b-selftest.js','c-selftest.js'],
  }),
  checkRoutes:async()=>new Map([['public:untested','']]),
  smoke:async()=>new Map(),
  runSuite:async()=>{inFlight++;highWater=Math.max(highWater,inFlight)
    await new Promise(resolve=>setTimeout(resolve,10))
    inFlight--;return {ok:true}
  },
})
assert.equal(manager.start({send:async text=>sent.push(text)}),true)
assert.equal(manager.start({send:async text=>sent.push(text)}),false)
assert.match(manager.status(),/in progress/i)
for(let i=0; i<100 && !sent.some(v=>v.includes('FULL COMMAND AUDIT'));i++) {
  await new Promise(resolve=>setTimeout(resolve,10))
}
assert.equal(highWater,2)
assert(sent.join(' ').includes('3/3'))
assert(sent.some(v=>v.includes('⚪ .untested')))
assert.equal(manager.cancel(),false)
assert.match(manager.status(),/No NIGHT audit/)

// Inventory must include all discoverable public + private commands.
// Neither the audit nor its regression routing executes side-effecting commands.
const inventory=await discoverAuditInventory()
assert(inventory.commands.some(row=>row.scope==='public' && row.command.name==='testall'))
assert(inventory.commands.some(row=>row.scope==='private' && row.command.name==='testall'))
assert(inventory.commands.length>=50)
assert(inventory.suites.length>=20)
const realRoutes=await checkCommandRoutes(inventory)
assert.equal(realRoutes.size,inventory.commands.length)
assert.deepEqual([...realRoutes.entries()].filter(([,reason])=>reason),[])

console.log('PASS NIGHT complete command audit: registry routing, labels, worker limit, WhatsApp messages')
