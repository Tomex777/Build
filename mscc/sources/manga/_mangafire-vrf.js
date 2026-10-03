const TABLE_1='yINlmUNho8VYJT+ibTIP+9ESiULpVEtMOoD6U6lRE0R/xwXo/Xp9NrUgC4cw/Lmo33vUyjUE40kUoEWIr/fxfNNcq2s79ShQ5NhNrFnJ4hXPwOu/SuXzIbuTQKGFvfm08E9jvCfqAtoDqvQq3dVWPQFmJjgvkISBeXY3BgANR+yVnjGbcxZ47d6kLNfZPIayTq3/YGySb1KuVZodWp/WGNAO5pfMcpaK53Hhs0allBszaMaxuouOwdxbwgxIw6YunSsXjI05Yi0j9j4eHKfSXR8Ifo/Od+8iamRfCXTyvm7NGRGYdcQ0ywcK/u6RXhrbcCm4t2eCtrDgQVecJGkQ+A=='
const KEY_1='0Ec58JOY3uBzJK9m3zqIOpdlF7UFiax9DmA='
const TABLE_2='IUFltCxD3Oc2cwCgkJffthaOg9cgPUb0LgW6H/VtfcF0kc5F25t+aWj6JH9VOhOaY0rAFdUxlDnl5BLNvwEJvQtP5qcw7vdb/K+chnbwnspSHT8mz5lqwz41TezG0hkO06FTjJZhsyNuFLDpD2ZZxQj/QIRcF90zpmQ7Byu483WsQqUE0C342HL+JXngRB6fRzxRyVTaKu83h7UYTJ0QMt6ixFh6S3F8gqkKwrGTL3jHNBsD45UnifK8+RGtishQV2K3rujLKEkiZxpr2dYcudFW4oFsDKhad3CLBvuyTqsCo4B7mL5IKQ1vXo/MOOvq1I1d8ar9X6Ttu5KF4fZgiA=='
const KEY_2='AAdjb1iPY8CiDmq9H34tKTBF8a3oDQ=='
const TABLE_3='NQHlu1/wVO5EmkwQymF810qqY2xG1k2obcas4Z9mCsPEIFl9pRIjFxbJ7ybMHbBckT5Ton85E0FOeHezbh/mjlEYpmpnlXOS8dgrqeq2KfxImTh1YK9y0PeMNhzA1OQzSY9brYOJq/l2QnE/hwOeZIhPixVSKIUlDb5vLcH6RWKxkIEMuP0bDwIqQ71AJJaEaMJL7A6YtyIwoRT+L5v4aZzodN/0+3nOGsfblFjgxSfPzVDjNFeNl5P26+kEC/8AHgdrpAbt3hHz3HrRN1Y6e+JHgF7ncFWnoF0y3THL1S71WgWGCa6KtSzTCCG58n68nTyj2T3Sshk7utqCtMi/ZQ=='
const KEY_3='DELOJgPsVaCcblDtTGMdHzM='

const stages=[
  [Buffer.from(TABLE_1,'base64'),Buffer.from(KEY_1,'base64'),0x5A],
  [Buffer.from(TABLE_2,'base64'),Buffer.from(KEY_2,'base64'),0x35],
  [Buffer.from(TABLE_3,'base64'),Buffer.from(KEY_3,'base64'),0xBA],
]

function stage(input,table,key,iv){
  const out=Buffer.alloc(input.length)
  let prev=iv
  for(let i=0;i<input.length;i++){
    prev=table[(input[i]^key[i%key.length]^prev)&0xff]
    out[i]=prev
  }
  return out
}

export function mangaFireSign(path){
  let data=Buffer.from(String(path),'utf8')
  for(const [table,key,iv] of stages) data=stage(data,table,key,iv)
  return data.toString('base64url')
}

export function signedMangaFireUrl(input){
  const url=new URL(input)
  if(!url.pathname.startsWith('/api/')) return url.href
  const entries=[...url.searchParams.entries()].sort((a,b)=>a[0].localeCompare(b[0]))
  let last=''
  let index=0
  const query=entries.map(([key,value])=>{
    let next=key
    if(key.endsWith('[]')){
      if(last!==key) index=0
      last=key
      next=key.replace('[]','['+(index++)+']')
    }
    return next+'='+value
  }).join('&')
  const signPath=url.pathname.replace(/^\/api/,'')+(query?'?'+query:'')
  url.search=''
  for(const [key,value] of entries) url.searchParams.append(key,value)
  url.searchParams.append('vrf',mangaFireSign(signPath))
  return url.href
}
