import fs from 'node:fs/promises';
import path from 'node:path';
import {ROOT,dataURI} from './node-assets.mjs';
import {loadCharacterAssets} from './character-assets.mjs';
export async function loadNoctisAssets(character){
 const a=await loadCharacterAssets(character);
 const [condensed,black]=await Promise.all(['BebasNeue.ttf','ArchivoBlack.ttf'].map(f=>fs.readFile(path.join(ROOT,'assets/fonts',f))));
 return {...a,fonts:{...a.fonts,condensed:dataURI(condensed,'font/ttf'),black:dataURI(black,'font/ttf')}};
}
