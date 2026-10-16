const assert=require('node:assert/strict')
const {generateKeyPairSync,verify}=require('node:crypto')
const {createHandler}=require('./api/account.js')
const {createHash}=require('node:crypto')
const {publicKey,privateKey}=generateKeyPairSync('ed25519')
const env={NIGHT_PLAY_ORIGIN:'https://night-play-combined-preview.vercel.app',NIGHT_PLAY_MSCC_URL:'https://102.37.119.250',NIGHT_PLAY_SIGNING_KEY:privateKey.export({type:'pkcs8',format:'pem'})}
let calls=[]
const response=(status,body)=>({ok:status===200,status,json:async()=>body})
const handler=createHandler({env,fetcher:async(url,options)=>{
  assert.equal(url.href,'https://102.37.119.250/api/night-play/rpc')
  const headers=options.headers
  const message=['night-play-v1',headers['x-night-timestamp'],headers['x-night-nonce'],'POST','/api/night-play/rpc',createHash('sha256').update(options.body).digest('hex')].join('\n')
  assert.equal(verify(null,Buffer.from(message),publicKey,Buffer.from(headers['x-night-signature'],'base64url')),true)
  const body=JSON.parse(options.body);calls.push(body)
  if(body.action==='health')return response(200,{ok:true,service:'night-play',version:1})
  if(body.action==='link')return response(200,{code:'ABCD-EFGH-JKLM',command:'.minigames link ABCD-EFGH-JKLM',expiresAt:Date.now()+300000})
  return response(200,{ok:true})
}})
async function request(method='GET',body,extra={},fn=handler){
  const req={method,body,headers:{origin:env.NIGHT_PLAY_ORIGIN,'content-type':'application/json',...extra},socket:{remoteAddress:'127.0.0.1'}}
  const headers={};const res={setHeader:(key,value)=>headers[key]=value,end:raw=>{res.body=JSON.parse(raw)}}
  await fn(req,res)
  return {status:res.statusCode,headers,body:res.body}
}
;(async()=>{
  assert.deepEqual((await request()).body,{linked:false,profile:null})
  assert.equal(calls[0].action,'health')
  const link=await request('POST',{action:'link',session:'attacker-controlled',client:'forged',backend:'https://evil.example'})
  assert.equal(link.status,200)
  assert.match(link.headers['Set-Cookie'],/^__Host-night_play=[A-Za-z0-9_-]{43}; Path=\/; HttpOnly; Secure; SameSite=Strict; Max-Age=2592000$/)
  assert.notEqual(calls.at(-1).session,'attacker-controlled')
  assert.notEqual(calls.at(-1).client,'forged')
  assert.equal((await request('POST',{action:'link'},{origin:'https://evil.example'})).status,403)
  assert.equal((await request('POST',{action:'link'},{'content-type':'text/plain'})).status,415)
  assert.equal((await request('POST','{')).status,400)
  assert.equal((await request('POST',{action:'health'})).status,400)
  assert.equal((await request('POST',{action:'link',padding:'x'.repeat(3000)})).status,400)
  const cookie=link.headers['Set-Cookie'].split(';')[0]
  const logout=await request('POST',{action:'logout'},{cookie})
  assert.match(logout.headers['Set-Cookie'],/Max-Age=0$/)
  assert.equal(calls.at(-1).session,cookie.split('=')[1])
  assert.equal((await request('GET',undefined,{},createHandler({env:{}}))).status,503)
  assert.equal((await request('GET',undefined,{},createHandler({env,fetcher:async()=>{throw Error('network')}}))).status,503)
  assert.equal((await request('GET',undefined,{},createHandler({env,fetcher:async()=>response(200,{ok:true,service:'cortex'})}))).status,503)
  console.log('PASS NIGHT Play gateway: signed backend requests, secure cookie, origin/CSRF, input validation, trusted routing, logout and backend outages')
})().catch(error=>{console.error(error);process.exitCode=1})
