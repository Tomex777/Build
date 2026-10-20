# TAC Redemption full-text and recording source outreach

Status: **RESEARCH / NOT SENT / NO PERMISSION RECEIVED**. Created 20 October 2026.

## What is needed to complete the app

A small native Android TAC hymnbook, packaged as `com.night.tachymns`, currently has all 800 numbered Redemption English **first-line index entries**, but only 25 readable texts, of which only 18 have working Yorùbá and English drafts. No third-party recording is cleared for redistribution. The plan is an offline text catalogue + optional per-track authorized audio downloads. The separate Àkọkún section (1–305) has its own numbering and is not being confused with Redemption.

1. Rights-holder-approved **full Redemption lyrics in Yorùbá and English**, with correct numbered first lines, verses, choruses, and diacritics, plus permission to distribute those texts inside a free mobile app and provide public updates.
2. If possible, a legally supplied **text dataset, scans or a reproducible book master** to verify exact numbered layout, including variant tunes/verses.
3. Original audio masters with express permission to stream and provide optional offline downloads; name the vocal performer, arranger, language and allowed territories. Direct video/audio extraction from YouTube is not an acceptable source.
4. Credits, copyright notices, redistribution licence scope, revocation/contact process, and quality review workflow.

## Potential contacts — confirm authority first

- The Apostolic Church Nigeria Evangelism Office, contact page: https://apostoliccrusade.com/contact/ (published contact **info@apostoliccrusade.com**). This is a possible institutional route to the **correct hymnbook/choir licensing contact**; its mailbox is not proof of ownership of the hymnbook or recordings.
- The Apostolic Church North America, Cincinnati ministry resource site: https://tacona-cincy.org/tac-redemption-hymnal — linked 20-page PDF is a **directory LISTING of files** `001.mp3` through `800.mp3`, plus alternate arrangements. It does not contain the MP3 recordings themselves. It may help identify an actual catalog owner or rights contact.
- Rickfords Hill Publishing is identified by Hymnary (https://hymnary.org/hymnal/RH2006?page=8) as publisher of the **English 2006 Redemption Hymnal resetting**, derived from the 1951 edition. Verify whether and how its rights relate to the specific TAC Nigeria Yorùbá/English version.
- The Apostolic Church Students' Fellowship of Nigeria, https://tacsfon.org/contact/, may be a secondary route to an authorized national church content editor, not an established rights holder.

## Proposed message (draft; do not send without the user's approval)

Subject: Permission and official bilingual text/audio resources for TAC hymnbook Android app

Hello,

We are developing a lightweight Android hymnbook intended to help members read The Apostolic Church Nigeria hymns offline in Yorùbá and English. Redemption and Àkọkún are stored as separate collections, and recordings will be optional streaming/downloads rather than inflating the initial app size.

We would appreciate being directed to the correct copyright or publishing representative. In particular, is an authorized and verified digital copy of the bilingual Redemption hymnbook (verses and choruses, with numbering and accents) available for us to license for distribution in a free Android app?

We are also interested in recordings of these exact hymns. We would only stream/download audio where the relevant recording owner has granted written authorization. We can preserve credits and attribution and agree to a review and corrections process.

Could you let us know who controls the relevant textual and sound recording rights, and whether you are open to discussing an appropriate licence or data collaboration?

Thank you.

## Usage rules

- No contact has been made. Do not represent a directory listing, hymn titles, or a public YouTube link as a licence.
- Keep source files, correspondence and licence evidence outside the public repository if they include private data; store only a reference identifier and approved sanitized metadata in released catalogue.
- Do not import verses in bulk from competing apps or sites without independent rights confirmation and printed-edition verification.
- Automated imports go through `tools/redemption_import.py`, and CI rejects missing rights or unverified printed editions.
