/**
 * Socket options for each independently-paired WhatsApp account.
 *
 * IMPORTANT: leave shouldSyncHistoryMessage unset. Baileys' own default
 * rejects FULL history but accepts INITIAL_BOOTSTRAP and RECENT payloads,
 * which are needed for LID/PN mapping and normal companion initialization.
 * syncFullHistory:false still avoids requesting the full chat archive.
 *
 * Socket + auth-state lifecycle isolation is handled by the account manager:
 * never reuse one account's auth, keys or socket for another account.
 */
export function createAccountSocketOptions({ auth, logger, browser, getMessage, version }) {
  if (!auth?.creds || !auth?.keys) throw new Error('Each WhatsApp account requires its own auth state')
  const options = {
    auth,
    logger,
    browser,
    markOnlineOnConnect: false,
    syncFullHistory: false,
    generateHighQualityLinkPreview: false,
    getMessage,
  }
  if (Array.isArray(version)) options.version = version
  return options
}
