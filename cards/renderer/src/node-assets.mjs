import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
import {fileURLToPath} from 'node:url';
export const ROOT=path.dirname(path.dirname(fileURLToPath(import.meta.url)));
export const dataURI=(b,mime)=>`data:${mime};base64,${Buffer.from(b).toString('base64')}`;
export async function loadAssets(card){
 const catalog=JSON.parse(await fs.readFile(path.join(ROOT,'assets/catalog.json'),'utf8'));
 const art=catalog.find(a=>a.id===card.artwork);if(!art)throw new Error('Artwork is not in the validated catalog');
 const bytes=await fs.readFile(path.join(ROOT,art.localPath));
 if(art.sha256&&crypto.createHash('sha256').update(bytes).digest('hex')!==art.sha256)throw new Error('Artwork integrity check failed');
 const [display,sans,italic]=await Promise.all(['Display.ttf','Sans.ttf','Italic.ttf'].map(f=>fs.readFile(path.join(ROOT,'assets/fonts',f))));
 const shareAlike=/\/licenses\/by-sa\//.test(art.licenseUrl);
 const outputLicense=shareAlike?art.license:'CC BY 4.0';
 const outputLicenseUrl=shareAlike?art.licenseUrl:'https://creativecommons.org/licenses/by/4.0/';
 return {...art,outputLicense,outputLicenseUrl,art:dataURI(bytes,'image/webp'),artWidth:art.width,artHeight:art.height,fonts:{display:dataURI(display,'font/ttf'),sans:dataURI(sans,'font/ttf'),italic:dataURI(italic,'font/ttf')}};
}
