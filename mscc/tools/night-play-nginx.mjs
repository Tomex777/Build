import { readFileSync, writeFileSync, statSync, chownSync, renameSync } from 'node:fs'
import { pathToFileURL } from 'node:url'

export function patchNginx(source) {
  const begin='    # NIGHT PLAY BEGIN', end='    # NIGHT PLAY END'
  const block=`${begin}
    location = /api/night-play/rpc {
        client_max_body_size 4k;
        proxy_pass http://127.0.0.1:8787;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto https;
        proxy_read_timeout 15s;
        proxy_send_timeout 15s;
    }
${end}`
  if (source.includes(begin)) {
    const escaped=source.indexOf(end)
    if (escaped<0 || source.indexOf(begin)!==source.lastIndexOf(begin)) throw new Error('Unexpected NIGHT Play ingress markers')
    return source.slice(0,source.indexOf(begin))+block+source.slice(escaped+end.length)
  }
  const matches=[...source.matchAll(/^\s*server_name ([0-9]{1,3}(?:\.[0-9]{1,3}){3});\s*$/gm)]
  if (matches.length!==1 || !source.includes('listen 443 ssl;')) throw new Error('Expected the existing Cortex IP HTTPS site')
  const anchor=matches[0], index=anchor.index+anchor[0].length
  return source.slice(0,index)+'\n'+block+'\n'+source.slice(index)
}
if (process.argv[1] && import.meta.url===pathToFileURL(process.argv[1]).href) {
  const path=process.argv[2]
  if (path!=='/etc/nginx/sites-available/cortex-agent') throw new Error('Only the existing Cortex HTTPS site is supported')
  const previous=readFileSync(path,'utf8'),next=patchNginx(previous)
  if (next!==previous) {
    const stat=statSync(path),temporary=path+'.night-play-next'
    writeFileSync(temporary,next,{mode:stat.mode&0o777})
    chownSync(temporary,stat.uid,stat.gid)
    renameSync(temporary,path)
  }
}
