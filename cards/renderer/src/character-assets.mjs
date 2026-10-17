import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
import sharp from 'sharp';
import {ROOT, dataURI} from './node-assets.mjs';

/** Local, explicitly supplied artwork; never grants a license to a reference. */
export async function loadCharacterAssets(character) {
 const a = character.artwork;
 const filename = path.resolve(ROOT, a.localPath);
 if (!filename.startsWith(ROOT + path.sep)) throw new Error('Artwork must be inside the project');
 const bytes = await fs.readFile(filename);
 const sha256 = crypto.createHash('sha256').update(bytes).digest('hex');
 if (a.sha256 && sha256 !== a.sha256) throw new Error('Artwork integrity check failed');
 const metadata = await sharp(bytes).metadata();
 if (metadata.width !== a.width || metadata.height !== a.height) throw new Error('Artwork dimensions differ from its profile');
 const mime = {png:'image/png', jpeg:'image/jpeg', webp:'image/webp'}[metadata.format];
 if (!mime) throw new Error('Use PNG, JPEG, or WebP artwork');
 if (!a.outputLicense || !a.outputLicenseUrl) throw new Error('Supply the actual output rights; no license is inferred');
 const [display, sans, italic] = await Promise.all(['Display.ttf','Sans.ttf','Italic.ttf'].map(f => fs.readFile(path.join(ROOT,'assets/fonts',f))));
 return {...a, art:dataURI(bytes,mime), artWidth:a.width, artHeight:a.height,
  fonts:{display:dataURI(display,'font/ttf'), sans:dataURI(sans,'font/ttf'), italic:dataURI(italic,'font/ttf')}};
}
