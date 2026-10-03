import { createNyoraBridgeSource } from './_common.js'

export default createNyoraBridgeSource({
  id:"mangafire",
  name:"MangaFire",
  aliases:["Manga Fire"],
  fallbackOrder:23,
  description:"MangaFire via Nyora’s live Kotatsu parser catalog, with MSCC CBZ delivery.",
})
