# Online-only Yorùbá hymn sources — 20 October 2026

**Research method:** use verified 800-number English Redemption index, match each English original to bilingual web hymn sources, then compare candidate Yorùbá stanza/chorus text against the exact TAC print translation. The user's explicit preference is to source from the Internet, without contacting churches. This note describes what is *actually online*, not imaginary sources.

## Primary result: 621 public bilingual hymns available as JSON

- Live site: https://sdahymnalyoruba.com/
- **Full text data**: https://sdahymnalyoruba.com/hymns.json
- Public repository: https://github.com/fisayoafolayan/sdahymnalyorubaweb/blob/main/hymns.json
- Repo README: https://github.com/fisayoafolayan/sdahymnalyorubaweb/blob/main/README.md
- Repo license: https://github.com/fisayoafolayan/sdahymnalyorubaweb/blob/main/LICENSE

The source contains 621 entries with `english_title`, Yorùbá `title`, and complete structured stanza and chorus arrays. The maintainer explicitly says `hymns.json` may be used by third-party apps with credit; the repository is MIT licensed (retain license notices and validate rights/variants when publishing hymns).

**Computed crosswalk:** `data/research/sda-open-yoruba-crossref.json`. Matching by normalized **exact English title** against the TAC 800-index yielded **114 source-to-Redemption matches**, **102 distinct Redemption hymn numbers absent a prior Yorùbá title**. Of those 102, **86 are beyond** the previous 35 candidates. Different variants can share an English title, so match alternatives remain separate. No full lyrics copied into the matching index or the TAC Android assets. **Do not claim any of these are already TAC-edition verified.**

## Second source: 600 Anglican English/Yorùbá hymns online

- Fully browsable bilingual catalogue: https://iom.webbuild.org.ng/hymns.php
- Example English: https://iom.webbuild.org.ng/hymn/15/abide-with-me-fast-falls-the-eventide
- Example full Yorùbá text: https://iom.webbuild.org.ng/orin/15/wa-ba-mi-gbe-ale-fere-le-tan

The website shows 600 hymn pairs. It is Anglican, **not** TAC, so use the common English first line to locate candidate Yorùbá text, preserve the source number, and check wording against TAC. No bulk import or blanket redistribution permission verified here.

## Third source: 643 searchable Yorùbá Baptist hymns

- https://yorubabaptisthymns.com/hymns/
- 643 individual hymn pages with verse text on the public website. More alternates can be searched by Yorùbá first line once the English-first bilingual matches are known. Baptist numbering is independent; no TAC-specific verification.

## Fourth source: 997 Yorùbá CAC full texts as downloadable JSON

- https://github.com/ebena107/HymnFlow/blob/master/hymn-bundle/cac_yhb.json
- https://github.com/ebena107/HymnFlow/blob/master/hymn-bundle/cac_ghb.json
- Data from **Christ Apostolic Church**, not The Apostolic Church. CAC Yoruba data includes verses, chant/chorus fields and printed number but no reliable mapping to TAC by number. The project is GPL-2.0; underlying publishers' lyric rights must be considered separately before redistributing.

## Fifth source: standalone individual Yorùbá lyric pages

- https://orinihinrere.wordpress.com/2021/09/14/orinihinrere-yoruba-hymn-lyrics-wa-ba-mi-gbe-ale-fere-le-tan/
- https://orinihinrere.wordpress.com/2019/04/02/orinihinrere-yoruba-hymn-lyrics-gbogbo-aye-gbe-jesu-ga/

The latter resembles Redemption #2, the former #797; variants still need the TAC printed text check. Standalone webpages establish accessibility, not automatic rights to republish.

## A misleading PDF, inspected and discarded

- https://tacona-cincy.org/tac-redemption-hymnal links to a 20-page PDF named `Tac hymns.pdf`.
- The PDF is an **index of MP3 filenames**, not an 800-hymn lyrics PDF; it is unsuitable for importing verses.

## Next work

1. Expand English-first matching against Anglican's 600 numbered bilingual songs, then others, recording original source, target Redemption number, and confidence. Never copy a source's same-number hymn as if it corresponded.
2. Take paired source variants one hymn at a time, compare stanza counts and exact Yorùbá words with actual TAC usage/print. Where no verifiable TAC translation is accessible, record alternate variant as alternate, *not* a finished TAC hymn.
3. Preserve rights and attribution by source. Public accessibility alone doesn't mean every translation is freely redistributable.
4. When verified, create licensed numbered import entries for `approved-redemption.json`; do not change UI counters for references alone.
