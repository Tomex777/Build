import { wikipediaArticle } from './utils/wikipedia.js'

const article = await wikipediaArticle('Nigeria')
if (!article) throw new Error('Live Wikipedia lookup returned no article')
if (!article.title) throw new Error('Live Wikipedia article has no title')
if (!article.extract || article.extract.length < 80) throw new Error('Live Wikipedia article summary is too short')
if (!/^https:\/\/en\.wikipedia\.org\//.test(article.url)) throw new Error('Live Wikipedia article URL is invalid')

console.log(JSON.stringify({
  ok:true,
  title:article.title,
  extractLength:article.extract.length,
  url:article.url,
  hasThumbnail:Boolean(article.thumbnail),
  matchCount:article.matches?.length || 0,
}, null, 2))
