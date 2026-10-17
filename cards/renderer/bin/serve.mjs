import http from 'node:http';
import fs from 'node:fs/promises';
import path from 'node:path';
import {ROOT} from '../src/node-assets.mjs';
const folder=path.join(ROOT,'dist');
const types={'.html':'text/html; charset=utf-8','.svg':'image/svg+xml','.png':'image/png','.jpg':'image/jpeg','.json':'application/json'};
const port=Number(process.env.PORT??4173);
http.createServer(async(req,res)=>{
 try{
  const pathname=decodeURIComponent(new URL(req.url,'http://localhost').pathname);
  const file=path.resolve(folder,'.'+(pathname==='/'?'/index.html':pathname));
  if(!file.startsWith(folder+path.sep))throw new Error('Outside static root');
  const bytes=await fs.readFile(file);res.writeHead(200,{'Content-Type':types[path.extname(file)]??'application/octet-stream','X-Content-Type-Options':'nosniff'});res.end(bytes);
 }catch{res.writeHead(404);res.end('Not found');}
}).listen(port,'127.0.0.1',()=>console.log(`NIGHT studio: http://127.0.0.1:${port}`));
