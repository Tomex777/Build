import fs from 'node:fs/promises';
import path from 'node:path';
import sharp from 'sharp';
import {atomic,digest} from '../src/catalogue.mjs';
import {collectionGallery} from '../src/gallery.mjs';

// Keep the offline download practical on phones. The 3000px masters and
// lossless phone proofs remain unchanged in the build folder.
export async function exportGallery(report,folder,destination){
  const images=new Map(),thumbs=new Map();
  for(const card of report.cards){
    const bytes=await fs.readFile(path.join(folder,card.preview));
    if(digest(bytes)!==card.previewSHA256)throw Error('Changed gallery image: '+card.id);
    const image=await sharp(bytes).jpeg({quality:86,chromaSubsampling:'4:4:4'}).toBuffer();
    const thumbnail=await sharp(path.join(folder,card.phone)).flatten({background:'#101419'}).jpeg({quality:88,chromaSubsampling:'4:4:4'}).toBuffer();
    images.set(card.id,'data:image/jpeg;base64,'+image.toString('base64'));
    thumbs.set(card.id,'data:image/jpeg;base64,'+thumbnail.toString('base64'));
  }
  await atomic(destination,collectionGallery(report,{imageFor:c=>images.get(c.id),thumbnailFor:c=>thumbs.get(c.id)}));
}
