import { SourceRegistry } from './source-registry.js'
import gutenberg from './sources/books/gutenberg.js'
import standardEbooks from './sources/books/standard-ebooks.js'
import openLibrary from './sources/books/openlibrary.js'
import novelBuddy from './sources/books/novelbuddy.js'

const registry = new SourceRegistry({
  rootUrl:new URL('./sources/', import.meta.url),
  storage:{ brandForCapability:() => 'MSCC' },
})
await registry.load()

const ids = registry.list('books').map(source => source.id).sort()
const expected = ['gutenberg','novelbuddy','openlibrary','standard-ebooks'].sort()
if (ids.join('|') !== expected.join('|')) {
  throw new Error('Unexpected books source set: ' + ids.join('|'))
}
if (registry.mode('books') !== 'user-choice') {
  throw new Error('Books must remain user-selectable by source.')
}

const gutRows = gutenberg._test.editionRows({
  languages:['en'],
  formats:{
    'application/epub+zip':'https://example.test/book.epub',
    'text/plain; charset=utf-8':'https://example.test/book.txt',
  },
})
if (gutRows.map(row => row.format).join('|') !== 'EPUB|TXT') {
  throw new Error('Gutenberg edition parsing failed.')
}

const seHtml = `
<a href="/ebooks/charlotte-bronte">Charlotte Brontë</a>
<a href="/ebooks/charlotte-bronte/jane-eyre"><span>Jane Eyre</span></a>
`
const seItems = standardEbooks._test.parseSearch(seHtml)
if (seItems.length !== 1 || seItems[0].title !== 'Jane Eyre') {
  throw new Error('Standard Ebooks search parsing failed.')
}
const seEditions = standardEbooks._test.editionsFor(seItems[0])
if (!seEditions.some(row => row.id === 'epub' && row.url.endsWith('charlotte-bronte_jane-eyre.epub'))) {
  throw new Error('Standard Ebooks download URL generation failed.')
}

const iaRows = openLibrary._test.archiveFormats('public-item', {
  files:[
    { name:'book.epub', size:'123456' },
    { name:'book.pdf', size:'456789' },
    { name:'secret.epub', size:'123', private:true },
    { name:'book_meta.xml', size:'10' },
  ],
})
if (iaRows.length !== 2 || iaRows.some(row => /secret|meta/.test(row.url))) {
  throw new Error('Open Library public archive filtering failed.')
}

const nbItems = novelBuddy._test.searchItems({
  data:{ items:[{ id:'123', name:'Example Novel', url:'/novel/example-novel', slug:'example-novel' }] },
})
if (nbItems.length !== 1 || nbItems[0].id !== '123' || !nbItems[0].url.startsWith('https://novelbuddy.me/')) {
  throw new Error('NovelBuddy search result parsing failed.')
}
const next = novelBuddy._test.nextData('<script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{"initialManga":{"id":"123"}}}}</script>')
if (next?.props?.pageProps?.initialManga?.id !== '123') {
  throw new Error('NovelBuddy __NEXT_DATA__ parsing failed.')
}

console.log('PASS books/novels sources: ' + expected.join(' | '))
