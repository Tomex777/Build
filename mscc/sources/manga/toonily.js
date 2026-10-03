import { createMadaraSource } from './_madara.js'

export default createMadaraSource({
  id:'toonily',
  name:'Toonily',
  baseUrl:'https://toonily.com',
  mangaSubString:'serie',
  fallbackOrder:29,
  aliases:['Toonily.com'],
  cookies:'toonily-mature=1',
  searchCardSelector:'div.page-item-detail.manga, div.page-item-detail, .c-tabs-item__content',
})
