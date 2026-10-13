import assert from 'node:assert/strict'
import {
  addGroupSchedule, dueGroupSchedules, finishGroupSchedule,
  groupScheduleInfo, nextScheduleTime, removeGroupSchedule,
  scheduleDays, setGroupScheduleTimezone,
} from './group-schedules.js'
import { normalizeAnimeMatch, formatAnimeMatch, normalizeAudd, normalizeAcoustId,
  identifyAnime } from './media-recognition.js'
import joinreq, { requestRows, pickJoinRequests } from './commands/admin/joinreq.js'
import schedulegroup from './commands/admin/schedulegroup.js'

const data = new Map()
const storage = {
  sharedGet(ns,key) { return data.get(ns+'|'+key) || null },
  sharedSet(ns,key,value) { data.set(ns+'|'+key,structuredClone(value)); return value },
  sharedEntries(ns) { return [...data].filter(([key])=>key.startsWith(ns+'|'))
    .map(([key,value])=>({key:key.slice(ns.length+1),value})) },
}

assert.deepEqual(scheduleDays('weekdays'),[1,2,3,4,5])
assert.deepEqual(scheduleDays('sun,sat'),[0,6])
assert.throws(()=>scheduleDays('noday'),/Days/)
const now = Date.parse('2026-10-13T19:00:00Z')
assert.equal(nextScheduleTime('22:00',scheduleDays('weekdays'),'Africa/Lagos',now),
  Date.parse('2026-10-13T21:00:00Z'))
assert.equal(nextScheduleTime('07:00',scheduleDays('weekdays'),'Africa/Lagos',now),
  Date.parse('2026-10-14T06:00:00Z'))
assert.throws(()=>nextScheduleTime('25:00',[1],'Africa/Lagos',now),/24-hour/)
assert.equal(nextScheduleTime('03:00',[0],'America/New_York',Date.parse('2026-03-08T05:00Z')),
  Date.parse('2026-03-08T07:00Z'))
const group='123@g.us'
const row=addGroupSchedule(storage,{group,accountId:'bot-1',action:'close',time:'22:00',days:'weekdays',now})
assert.equal(groupScheduleInfo(storage,group).entries.length,1)
assert.throws(()=>addGroupSchedule(storage,{group,accountId:'bot-1',action:'close',time:'22:00',days:'weekdays',now}),/exists/)
assert.equal(dueGroupSchedules(storage,now).length,0)
assert.equal(dueGroupSchedules(storage,Date.parse('2026-10-13T21:00:01Z')).length,1)
finishGroupSchedule(storage,group,row.id,Date.parse('2026-10-13T21:00:01Z'))
assert.equal(dueGroupSchedules(storage,Date.parse('2026-10-13T21:00:01Z')).length,0)
setGroupScheduleTimezone(storage,group,'UTC')
assert.equal(groupScheduleInfo(storage,group).timezone,'UTC')
assert.equal(removeGroupSchedule(storage,group,row.id),true)
assert.equal(groupScheduleInfo(storage,group).entries.length,0)

const match=normalizeAnimeMatch({result:[{similarity:0.93,anilist:{id:42,title:{english:'Example'}},episode:3,from:123}]})
assert.equal(match.title,'Example')
assert.match(formatAnimeMatch(match),/02:03/)
assert.equal(normalizeAnimeMatch({result:[{similarity:0.20}]}),null)
const request=await identifyAnime(Buffer.from('test'), { fetcher:async(_,opts)=>{
  assert.equal(opts.method,'POST')
  return {ok:true,status:200,json:async()=>({result:[{similarity:0.93,anilist:42,episode:1,from:0}]})}
}})
assert.equal(request.episode,'1')
assert.equal(normalizeAudd({status:'success',result:{artist:'A',title:'B'}}).title,'B')
assert.equal(normalizeAudd({status:'success',result:null}),null)
assert.equal(normalizeAcoustId({results:[{score:0.99,recordings:[{title:'Song',artists:[{name:'Artist'}]}]}]}).artist,'Artist')
assert.equal(normalizeAcoustId({results:[{score:0.1,recordings:[{title:'Fake'}]}]}),null)

const rows=requestRows([{jid:'111@s.whatsapp.net'},{jid:'222@lid'},{jid:'bad@example.com'}])
assert.equal(rows.length,2)
assert.deepEqual(pickJoinRequests(rows,'1,2').map(r=>r.jid),['111@s.whatsapp.net','222@lid'])
assert.deepEqual(pickJoinRequests(rows,'999'),[])
let text=''
const ctx={groupKey:group,account:{sock:{
  groupRequestParticipantsList:async()=>rows.map(row=>({jid:row.jid})),
  groupRequestParticipantsUpdate:async(jid,action)=>{
    assert.equal(action,'approve')
    assert.deepEqual(jid,['111@s.whatsapp.net'])
    return [{jid:jid[0],status:'200'}]
  },
}},args:['accept','1'],isBotGroupAdmin:async()=>true,reply:v=>{text=v;return v}}
await joinreq.run(ctx)
assert.match(text,/approved: \*1\*/)
const adminCtx={...ctx,account:{id:'bot-1',sock:{}},groupScheduleStorage:storage,
  args:['close','22:00','weekdays']}
await schedulegroup.run(adminCtx)
assert.match(text,/scheduled/)
console.log('Recognition and group schedule regression checks passed.')
