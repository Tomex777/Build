import { createNyoraBridgeSource } from './_common.js'

export default createNyoraBridgeSource({
  id:"comix",
  name:"Comix",
  aliases:["Comix.to","Comix.ws"],
  fallbackOrder:20,
  description:"Comix via Nyora’s live Kotatsu parser catalog, with MSCC CBZ delivery.",
})
