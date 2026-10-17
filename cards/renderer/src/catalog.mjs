import fs from 'node:fs/promises';
import path from 'node:path';
import sharp from 'sharp';
import {hash,licensePolicy} from './discovery.mjs';
/** Register an acquired original. Integrity and pixel checks run again before normalization. */
export async function registerArtwork(manifestPath,{root,id}={}){
 if(!root||!/^[a-z][a-z0-9-]{0,70}$/.test(id??''))throw new Error('Provide an asset root and a lowercase artwork id');
 const m=JSON.parse(await fs.readFile(manifestPath,'utf8'));
 if(m.status!=='acquired-needs-composition-review'||!licensePolicy(m.licenseUrl)||!m.sha256||path.basename(m.filename??'')!==m.filename)throw new Error('Use an unmodified acquisition manifest');
 const bytes=await fs.readFile(path.join(path.dirname(manifestPath),m.filename));
 if(hash(bytes)!==m.sha256)throw new Error('Acquired artwork hash does not match its manifest');
 const catalogPath=path.join(root,'assets/catalog.json');
 let catalog;try{catalog=JSON.parse(await fs.readFile(catalogPath,'utf8'))}catch(e){if(e.code==='ENOENT')catalog=[];else throw e}
 if(catalog.some(x=>x.id===id))throw new Error('Artwork id already exists; choose a new id');
 const {data,info}=await sharp(bytes,{limitInputPixels:120000000}).resize({width:6000,height:6000,fit:'inside',withoutEnlargement:true}).webp({quality:96,alphaQuality:100}).toBuffer({resolveWithObject:true});
 const localPath=`assets/art/${id}.webp`;
 const entry={...m,id,localPath,width:info.width,height:info.height,sha256:hash(data),originalSha256:m.sha256,changes:'Resized and converted to WebP. Card framing/cropping is a further adaptation.',visualApproval:false};
 await fs.mkdir(path.join(root,'assets/art'),{recursive:true});
 await fs.writeFile(path.join(root,localPath),data,{flag:'wx'});
 const pending=catalogPath+'.pending';await fs.writeFile(pending,JSON.stringify([...catalog,entry],null,2));await fs.rename(pending,catalogPath);
 return entry;
}
