import { createNyoraBridgeSource } from './_common.js'

export default createNyoraBridgeSource({
  id:"mangahub",
  name:"MangaHub",
  aliases:["MangaHub.io","Manga Hub"],
  fallbackOrder:24,
  description:"MangaHub via Nyora’s live Kotatsu parser catalog, with MSCC CBZ delivery.",
})
