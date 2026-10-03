import { createNyoraBridgeSource } from './_common.js'

export default createNyoraBridgeSource({
  id:"mangapill",
  name:"MangaPill",
  aliases:["Manga Pill"],
  fallbackOrder:27,
  description:"MangaPill via Nyora’s live Kotatsu parser catalog, with MSCC CBZ delivery.",
})
