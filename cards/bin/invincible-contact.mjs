#!/usr/bin/env node
// Correct the generic BLEACH header on NIGHT contact sheets for this collection.
// Only contact-sheet label pixels change; 3000×4800 master exports stay untouched.
import fs from 'node:fs/promises';
import path from 'node:path';
import sharp from 'sharp';
import {inside,atomic} from '../src/catalogue.mjs';

const folder=inside(process.argv[2]??'dist/invincible');
const names=(await fs.readdir(folder)).filter(x=>/^contact-\d+\.png$/.test(x));
if(!names.length)throw Error('No actual NIGHT contact sheets to relabel');
for(const name of names){
 const file=path.join(folder,name),original=await fs.readFile(file);
 const meta=await sharp(original).metadata();
 if(meta.width!==1744||meta.height!==1250)throw Error('NIGHT contact sheet geometry changed');
 const overlay=Buffer.from('<svg xmlns="http://www.w3.org/2000/svg" width="530" height="65"><rect width="530" height="65" fill="#10151a"/><text x="32" y="47" font-family="sans-serif" font-size="25" fill="#f0e7d5">NIGHT / INVINCIBLE</text></svg>');
 await atomic(file,await sharp(original).composite([{input:overlay,left:0,top:0}]).png().toBuffer());
 console.log('Relabeled actual phone proof sheet',name);
}
