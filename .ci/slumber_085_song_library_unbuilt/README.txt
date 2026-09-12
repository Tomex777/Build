Slumber 0.8.5 unbuilt source checkpoint — song library / tutor pass

STATUS: SOURCE/PATCH ONLY. No Gradle, APK build, emulator, Codespaces, or GitHub Actions run was used for this checkpoint.

Baseline
- Exact Slumber 0.8.5 source restored from Actions artifact run 34645245543 (`slumber-0.8.5-exit-recreation`).
- Target branch: `pianohub-ci` only. Never `main`.

Restore the source change
1. Restore the exact baseline source from run 34645245543.
2. Decode the checkpoint patch:
   base64 -d slumber_085_song_library_unbuilt.patch.gz.b64 | gzip -dc > slumber_085_song_library_unbuilt.patch
3. From the baseline source root:
   patch -p1 < slumber_085_song_library_unbuilt.patch

Patch integrity
- Base64 text SHA-256: a4599b5eb76b0f927279f229f499d54227ea6924ae4497f65b9922b7002b99d8
- Decoded gzip SHA-256: 51c25dd5e6b798d7df40b3d08b2479cb2e7a7286f817d69bc85340f155ca12d9
- Uncompressed patch SHA-256: 4822813b4d72f844528ce245750bd9e5f40bbaf5a4c674cab90b7944e6d30dbe

Static source checks only
- PDMX builder Python compile: PASS
- PDMX synthetic self-test: PASS
- Full patch dry-run against exact baseline: PASS
- Fresh patch application compared byte-for-byte with the working source: zero differences

Android/Gradle/runtime verification: NOT RUN by explicit owner request.
