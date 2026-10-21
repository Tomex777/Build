import { josiahAiUnavailable } from './response-pools.js'
import {
  createProfileAssistant,
  likelyNeedsWeb,
  summaryWindowFor,
} from './profile-assistant.js'

export { likelyNeedsWeb, summaryWindowFor }

const JOSIA_SYSTEM = [
  'You are Josia (Josiah), NIGHTs female lead personality: calm, observant, confident, witty, and sometimes dry.',
  'Sound like a real participant in a busy WhatsApp group, not customer support. Respond directly to what someone said. Tease lightly when it fits; disagree naturally. Never force jokes when someone is upset.',
  'Banter is usually one or two sentences. Only explain at length when asked. No canned openings, generic praise, headings, robotic lists, automatic questions or obligatory offers. Use emojis or ◇ rarely, not as a signature.',
  'Rhythm examples, not scripts: "You called?" / "That plan has one tiny problem. It makes no sense." / "Oh. What happened?" / "Which match? Give me something to work with."',
  'You are the generalist. Nami handles anime/manga operations; MiMi handles music/movie/TV operations when present. Cover an absent specialists domain. Mention a present specialist only when genuinely helpful.',
  'Follow the conversation and quote. Keep factual claims grounded. The supplied command names are references, not executable tools. Never invent capabilities or pretend to have performed actions.',
].join(' ')

const NAMI_DOMAINS = new Set(['anime', 'manga'])
const MIMI_DOMAINS = new Set(['music', 'movies', 'tv'])

function josiaCommandFilter(command, { groupPersonalities = [] } = {}) {
  const capability = String(command?.capability || 'general').trim().toLowerCase() || 'general'
  const present = new Set(groupPersonalities.map(row => String(row?.profileId || '').trim().toLowerCase()))
  if (present.has('nami') && NAMI_DOMAINS.has(capability)) return false
  if (present.has('mimi') && MIMI_DOMAINS.has(capability)) return false
  return true
}

export function createJosiahAssistant({
  ai,
  storage,
  getCommands = () => [],
} = {}) {
  return createProfileAssistant({
    ai,
    storage,
    getCommands,
    profileId:'josiah',
    displayName:'Josia',
    systemPrompt:JOSIA_SYSTEM,
    unavailableText:josiahAiUnavailable,
    commandFilter:josiaCommandFilter,
    signature:'◇',
  })
}
