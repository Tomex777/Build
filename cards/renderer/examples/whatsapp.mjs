/** Return a Baileys-compatible payload; this example never sends a message. */
import {renderCard} from '../src/index.mjs';
import {loadAssets} from '../src/node-assets.mjs';
import sharp from 'sharp';
export async function buildWhatsAppCard(card,rasterizer){
 const assets=await loadAssets(card),svg=renderCard(card,assets);
 const image=await sharp(await rasterizer.render(svg,{width:1140})).flatten({background:'#061c24'}).jpeg({quality:94,mozjpeg:true}).toBuffer();
 return {image,mimetype:'image/jpeg',caption:`NIGHT · ${card.name} / ${card.edition}\nArt: ${assets.title} — ${assets.artist}\n${assets.sourceUrl}\n${assets.license}: ${assets.licenseUrl}\nCropped and framed by NIGHT. Card image: ${assets.outputLicense} (${assets.outputLicenseUrl}).`};
}
// Create one rasterizer for an isolated rendering worker, reuse it, close at shutdown.
// Keep rendering outside the WhatsApp event loop; send the returned payload in your bot.
