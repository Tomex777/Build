import {
  normalizeWikiPage,
  normalizeWikiSearch,
} from './utils/wikipedia.js'

const search = normalizeWikiSearch({
  query:{
    search:[
      { title:'Nigeria', pageid:21383, snippet:'Country in West Africa' },
      { title:'History of Nigeria', pageid:144214, snippet:'History' },
    ],
  },
})
if (search.length !== 2) throw new Error('Wikipedia search normalization failed')
if (search[0].title !== 'Nigeria' || search[0].pageid !== 21383) throw new Error('Wikipedia top result normalization failed')

const page = normalizeWikiPage({
  query:{
    pages:[{
      pageid:21383,
      title:'Nigeria',
      extract:'Nigeria is a country in West Africa.',
      fullurl:'https://en.wikipedia.org/wiki/Nigeria',
      thumbnail:{ source:'https://upload.wikimedia.org/example.jpg' },
    }],
  },
})
if (!page) throw new Error('Wikipedia page normalization failed')
if (page.title !== 'Nigeria') throw new Error('Wikipedia title normalization failed')
if (!page.extract.includes('West Africa')) throw new Error('Wikipedia extract normalization failed')
if (page.url !== 'https://en.wikipedia.org/wiki/Nigeria') throw new Error('Wikipedia URL normalization failed')
if (!page.thumbnail.startsWith('https://')) throw new Error('Wikipedia thumbnail normalization failed')

console.log('PASS Wikipedia normalization')
