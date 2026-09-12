Slumber 0.8.5 unbuilt source checkpoint — song library / tutor pass

STATUS: SOURCE/PATCH ONLY. No Gradle, APK build, emulator, Codespaces, or GitHub Actions run was used for this checkpoint.

Baseline
- Exact Slumber 0.8.5 source restored from Actions artifact run 34645245543 (`slumber-0.8.5-exit-recreation`).
- Target branch: `pianohub-ci` only. Never `main`.

Restore the source change
1. Restore the exact baseline source from run 34645245543.
2. Decode the main checkpoint patch:
   base64 -d slumber_085_song_library_unbuilt.patch.gz.b64 | gzip -dc > slumber_085_song_library_unbuilt.patch
3. From the baseline source root:
   patch -p1 < slumber_085_song_library_unbuilt.patch
4. Decode and apply the OpenScore follow-up patch:
   base64 -d slumber_085_song_library_openscore_update.patch.gz.b64 | gzip -dc > slumber_085_song_library_openscore_update.patch
   patch -p1 < slumber_085_song_library_openscore_update.patch

Main patch integrity
- Base64 text SHA-256: a4599b5eb76b0f927279f229f499d54227ea6924ae4497f65b9922b7002b99d8
- Decoded gzip SHA-256: 51c25dd5e6b798d7df40b3d08b2479cb2e7a7286f817d69bc85340f155ca12d9
- Uncompressed patch SHA-256: 4822813b4d72f844528ce245750bd9e5f40bbaf5a4c674cab90b7944e6d30dbe

OpenScore follow-up integrity
- Base64 text SHA-256: 5985136d3f57a296be7ae629d66be1cd19b7764d8deebce8c88c4b5c4f0bbb63
- Decoded gzip SHA-256: 316881d661a85d8e77180a6280f10d6da4c20f6345b0b6afe5421851323e10bc
- Uncompressed patch SHA-256: 270e7e3d89d72c2fd7ea88881b69f81893e247145de941ac4acdd760711478f2

Static source checks only
- PDMX builder Python compile: PASS
- PDMX synthetic self-test: PASS
- OpenScore Lieder builder Python compile: PASS
- OpenScore Lieder synthetic self-test: PASS
- Main patch dry-run against exact baseline: PASS
- OpenScore follow-up patch dry-run/apply against the prior checkpoint: PASS
- Follow-up changed files compared byte-for-byte with the working source: zero differences

Android/Gradle/runtime verification: NOT RUN by explicit owner request.
