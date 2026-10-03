import {
  parseBingImageHtml,
  parseDuckDuckGoHtml,
} from './utils/web-search.js'

const duck = `
<div class="result">
  <a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fpage">Example result</a>
  <a class="result__url">example.com/page</a>
  <div class="result__snippet">A useful example result.</div>
</div>
`
const web = parseDuckDuckGoHtml(duck, { limit:5 })
if (web.length !== 1) throw new Error('DuckDuckGo parser did not return one result')
if (web[0].url !== 'https://example.com/page') throw new Error('DuckDuckGo redirect URL was not unwrapped')
if (web[0].title !== 'Example result') throw new Error('DuckDuckGo title parse failed')
if (web[0].snippet !== 'A useful example result.') throw new Error('DuckDuckGo snippet parse failed')

const bing = `
<a class="iusc" m='{"murl":"https://images.example.com/photo.jpg","turl":"https://thumb.example.com/photo.jpg","purl":"https://example.com/photo","t":"Example photo"}'></a>
`
const images = parseBingImageHtml(bing, { limit:5 })
if (images.length !== 1) throw new Error('Bing image parser did not return one result')
if (images[0].imageUrl !== 'https://images.example.com/photo.jpg') throw new Error('Bing image URL parse failed')
if (images[0].pageUrl !== 'https://example.com/photo') throw new Error('Bing page URL parse failed')
if (images[0].title !== 'Example photo') throw new Error('Bing image title parse failed')

console.log('PASS web and image search parsers')
