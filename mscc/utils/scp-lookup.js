// Source-linked SCP encyclopedia lookup. Cards/art remain in the separate Cards project.
// Does not scrape or redistribute unverified images. SCP-derived text is CC BY-SA 3.0.
import { load } from 'cheerio'

const cache = new Map()
const MAX_BYTES = 1_200_000
export const normalizeScpId = value => {
  const cleaned = String(value || '').trim().toUpperCase().replace(/^SCP[-\s]*/, '')
  if (!/^\d{1,4}$/.test(cleaned)) return ''
  const number = Number(cleaned)
  return number >= 1 && number <= 9999 ? `SCP-${String(number).padStart(3,'0')}` : ''
}
const simpleText = text => String(text || '').replace(/[\u00a0\u200b-\u200d]/g,' ').replace(/\s+/g,' ').trim()
const excerpt = (str,length=410) => {
  const s=simpleText(str)
  if (s.length<=length)return s
  const cut=s.slice(0,length)
  const last=cut.lastIndexOf(' ')
  return cut.slice(0,last>length*.6?last:length).replace(/[,;: ]+$/,'')+'…'
}

export function parseScpPage(html,id) {
  const $ = load(html)
  const content = $('#page-content')
  if (!content.length) return null
  content.find('script,style,iframe,.licensebox,.license-box,.page-tags,.rate-box-with-credit-button').remove()
  const paragraphs = content.find('p').map((_,element)=>simpleText($(element).text())).get()
  const full=simpleText(content.text())
  const joined=paragraphs.join(' ')
  const itemMatch=joined.match(/\bItem\s*#:\s*(SCP[-\s]?\d{1,4})/i)
  if (!itemMatch) return null // don't treat a 404 or generic site page as an SCP dossier
  const klass=joined.match(/\bObject Class:\s*([a-z][a-z\- ]{1,35})(?=\s*(?:Special Containment Procedures:|Description:)|[\n.;]|$)/i)
    || full.match(/\bObject Class:\s*([a-z\- ]{2,28})/i)
  const className=klass ? simpleText(klass[1]).replace(/\s+special.*$/i,'').slice(0,35) : 'Unclassified'
  let description=paragraphs.find(p=>/^Description\s*:/i.test(p))
  if (description) description=description.replace(/^Description\s*:\s*/i,'')
  else {
    const index=joined.search(/\bDescription\s*:/i)
    description=index<0?'':joined.slice(index).replace(/^Description\s*:\s*/i,'').split(/\b(?:Addendum|Incident Log|Special Containment Procedures):/i)[0]
  }
  return {
    id, objectClass:className || 'Unclassified',
    description:excerpt(description || 'Open the official dossier for its full description.'),
    url:'https://scp-wiki.wikidot.com/'+id.toLowerCase(),
    // An author's exact credit is maintained in the article's licensing/citation footer.
    credit:'Original article and credited creators: SCP Wiki',
    license:'CC BY-SA 3.0',
  }
}
export async function fetchScp(value, {request=fetch, now=Date.now} = {}) {
  const id=normalizeScpId(value)
  if (!id) throw new Error('Use .scp <number>, for example .scp 682.')
  const cached=cache.get(id)
  if(cached && cached.expiresAt>now())return cached.data
  const url='https://scp-wiki.wikidot.com/'+id.toLowerCase()
  const response=await request(url,{headers:{accept:'text/html','user-agent':'NIGHT-Collectibles/1.0 (SCP source-linked reference)'},signal:AbortSignal.timeout(6500)})
  if (response.status===404) return null
  if (!response.ok) throw new Error('The SCP Wiki could not be reached right now.')
  const length=Number(response.headers?.get?.('content-length')||0)
  if(length>MAX_BYTES)throw new Error('SCP article too large to fetch safely.')
  const html=await response.text()
  if(html.length>MAX_BYTES)throw new Error('SCP article exceeded the safe size limit.')
  const entry=parseScpPage(html,id)
  if(!entry)return null
  if(cache.size>100)cache.clear()
  cache.set(id,{data:entry,expiresAt:now()+15*60_000})
  return entry
}
export function scpMessage(entry) {
  return [
    `☢️ *${entry.id} — FOUNDATION DOSSIER*`,
    `🔐 Object class: *${entry.objectClass}*`,
    '',
    '📖 *Description*',
    entry.description,
    '',
    '📁 Full official file:',
    entry.url,
    '',
    '© SCP Wiki / credited article creators · CC BY-SA 3.0',
    'Author and media credits: see the original page.',
  ].join('\n')
}
