# TAC Redemption Hymnal: acquisition leads (20 October 2026)

## Objective and status

Acquire the **exact The Apostolic Church Nigeria Yorùbá Redemption** full verses for 800 independently numbered hymns (and later Àkọkún 1–305). Current project contains **18 Yorùbá draft hymn texts**, **59 Yorùbá first lines identified**, and **35 extra *cross-hymnal candidate* first lines** in `english-first-yoruba-matches.json`. Candidates are NOT completed TAC texts. Never mark an indexed title or English-source lyric as a confirmed TAC Yorùbá verse.

## Priority 1: Ask TAC Nigeria for a legitimate edition / digital master and rights

Academic bibliography identifies specific editions:

- **2001**: *Iwe Orin Ijo Aposteli Naa [The Apostolic Church Redemption Hymnal with Appendix]*, Lagos: **Victory Publishers**.
- **1977**: *The Apostolic Church Redemption Hymnal with Appendix*, Lagos: **The Apostolic Church LAWNA Literature Committee**.

Source: Oluwole T. Okewande, *Convergence of form and content between indigenous and Christian songs and beliefs of the Yoruba in south-western Nigeria*, *Studies in African Languages and Cultures* 56 (2022), bibliography: https://wuw.pl/data/include/cms/Studies_in_African_2022_56.pdf

Officially identified TAC Nigeria Evangelism Office (for forwarding the request to whoever controls publications / copyright, NOT assumed to itself hold rights):
- info@apostoliccrusade.com
- +234 805 474 8215; 01-293-1267
- National HQ: Olorunda Ketu, Lagos
- https://apostoliccrusade.com/contact/

Ask for: current printed Yorùbá edition; exact Redemption numbering and separate Àkọkún appendix; digital PDF/Word/InDesign/CSV/JSON if available; the owning publisher/translation committee; **written authorization for distributing full hymn text inside a publicly installable offline Android application**, including proof of any restrictions and credit line. Buying a book or viewing a site alone does not establish redistribution rights.

## Priority 2: Request licensed data from existing TAC app developers

1. **deLTree Technologies, TAC Hymns**, Google Play listing
   - https://play.google.com/store/apps/details?id=com.deltreetech.tachymns
   - deltreetechnologies@gmail.com | +2348066793736
   - App advertises a TAC Nigerian electronic hymnal with number / first-line search. **Do not claim it contains all Yorùbá lyrics without checking**.
2. **Ngene Michael O, The Apostolic Hymns (Offline)**:
   - https://play.google.com/store/apps/details?id=com.flawlessconcepts.tac_hymns
   - droidsmike@gmail.com
   - Google Play advertises 800+ TAC hymns; iOS release notes explicitly say 'Yoruba Version' was added in version 2.0 (March 2025): https://apps.apple.com/us/app/the-apostolic-hymns-offline/id6476892330
   - Ask if publisher / church permits an editable dataset export or a separate text licensing agreement.
3. **Bolu Bajomo, Redemption Hymnal**, iOS:
   - https://apps.apple.com/ng/app/redemption-hymnal/id6467019391
   - iOS release notes explicitly state Yorùbá translations were added in 2023. Use App Store developer contact page; direct email was not verified.
   - Ask whether the developer controls the text rights and whether a permissioned dataset is available.

Do NOT extract / decompile proprietary app databases or scrape protected app endpoints without authorization. Do not treat another app's availability as a licence.

## Priority 3: Cross-hymnal identification only, not a source of TAC-exact full text

- Hymnize: https://hymnize.com/about — English/Yorùbá CAC and other denominational hymnals. **Does not list TAC**.
- Iwe Orin Mimo: https://iom.webbuild.org.ng/hymns.php — 600 Anglican English/Yorùbá correspondences; numbering/text differ from TAC.
- Christ Apostolic Church digitizations are **CAC, not TAC**. A commercial downloadable CAC Word file is NOT an authorized copy of TAC's translation.
- Existing research: `data/research/english-first-yoruba-matches.json` lists only unverified first lines and explicit source provenance.
- Avoid falsely assuming English first-line equality means identical Yorùbá wording in the TAC edition.

## Intake and verification workflow (after a source and licence are obtained)

1. Register source title, year, provider, permissions, edition, page ranges, and whether the set is Redemption or Àkọkún.
2. Secure a source file (licensed editable text preferred; otherwise properly authorized print scans). Preserve the original unmodified.
3. Produce numbered UTF-8 NFC JSON with exact `title_yo`, `verses_yo`, stanza breaks, and choruses, crosschecked to the *printed TAC* number. Human Yorùbá proofreaders verify tone marks and subdots; English equivalents assist discovery only.
4. Use `tools/redemption_import.py` to validate church-approved bilingual batches with source pages, reviewer name, permissions reference and explicit printed-edition verification; then `tools/build_asset.py`.
5. Run content tests and Android instrumentation; spot-check real rendering, navigation and search before releasing. Keep unlicensed candidate research separate from shipping assets.

## Ready-to-send message (NOT sent)

**Subject:** Request for TAC Nigeria Yorùbá Redemption Hymnal digital text and reuse permission

Hello,

I am developing an offline Android reader for The Apostolic Church Nigeria hymnbook. I want to reproduce the complete Yorùbá Redemption hymns 1–800 faithfully, alongside the separately numbered Àkọkún 1–305, preserving stanza structure and tone marks.

Could you direct me to the Literature Committee / rights holder or current publisher of *Ìwé Orin Ìjọ Àpọ́sítélì* / the TAC Redemption Hymnal? Is there an authorized electronic version (Word, PDF, CSV, JSON, or publisher files) that can be licensed for inclusion in a free offline Android app? If permission is possible, please specify the credit line, conditions, and whether distribution of the full lyrics is approved. I can provide a sample and further project details.

Thank you.
