# NIGHT: PDF, QR and voice tools

Nine commands have been added as public commands (subject to existing public-command policies).

| Command | Usage |
| --- | --- |
| .pdfmerge | Reply to each PDF with `.pdfmerge add`; send `.pdfmerge finish`. `.pdfmerge status` and `.pdfmerge clear` are available. |
| .pdfsplit | Reply to a PDF with `.pdfsplit 1-3,5` (extract pages) or `.pdfsplit every 5` (separate files). |
| .img2pdf | Reply to an image with `.img2pdf`; for multiple images reply to each with `.img2pdf add`, then `.img2pdf finish`. |
| .pdf2img | Reply to a PDF with `.pdf2img 1-3` or `.pdf2img all` for PDFs with five or fewer pages. |
| .pdfcompress | Reply to a PDF with `.pdfcompress`. Uses the `/ebook` Ghostscript profile and returns the original if compression did not help. |
| .denoise | Reply to an audio clip or WhatsApp voice note with `.denoise`. |
| .clearaudio | Reply to audio with `.clearaudio`, `.clearaudio natural`, or `.clearaudio strong`. `.cleanvoice` is an alias. |
| .extractaudio | Reply to a video with `.extractaudio`. Produces MP3. |
| .scanqr | Reply to an image with `.scanqr`. Decodes locally; never opens URLs. |

## Deployment requirements

* Node 24 and the dependencies declared in `package.json`: `pdf-lib`, `jsqr` and existing `@napi-rs/canvas`.
* `ffmpeg` for audio filtering/extraction; `pdftoppm` (Ubuntu: `poppler-utils`) for PDF page rendering; `gs` (Ubuntu: `ghostscript`) for PDF compression.
* Run `npm run check:media-tools` before deployment; full `npm run check` also executes it.

The audio commands use FFmpeg, not paid AI. They cannot reconstruct inaudible speech. Voice notes remain voice notes when the input was marked as a WhatsApp voice note. Extracted audio is re-encoded as MP3 for broad compatibility.

## Safety and performance

* Conversion work is serialized by a bounded queue (one active job, two waiting); PDF and QR processing run inside a separate worker thread. Network message handling stays responsive during conversion.
* PDF input limit: 10 MB per file, 180 pages. Merge: 2–8 PDFs, at most 180 pages combined. Split: up to 40 selected pages or eight separate output files. Image-to-PDF: at most 8 images. PDF-to-image: up to 5 pages per call.
* Audio/video inputs are limited to 20 MB and 180 seconds processed per call. Conversion output is limited to 19 MB.
* Multi-file collection requires explicit `.add` on each quoted message because WhatsApp does not supply multiple earlier attachments in one command. Per-user/chat/account batches expire after 15 minutes and are never persisted or shared.
* Input buffers are bounded (18 MB per batch, 36 MB across staged batches), as are document worker heap and process timeouts.
* PDF and image parsing still consumes CPU/RAM; avoid enabling very large documents on a memory-constrained instance without monitoring resource usage.


## Three additional commands (same unmerged feature branch)

- \`.stripmeta\`: reply to an image; re-encodes pixels as JPEG/PNG to remove EXIF, GPS coordinates, XMP, software comments and similar embedded metadata. It cannot erase information visible in the pixels, nor private data already extracted by someone else. Depending on format, JPEG is lossy.
- \`.silencecut\`: reply to audio; conservatively shortens low-amplitude gaps (normal mode), with \`.silencecut gentle\` (keeps more quiet speech) or \`.silencecut aggressive\` (cuts more, can remove quiet words). **No AI is involved.** FFmpeg measures audio loudness, not speech presence: loud traffic, music, fans or other noises may remain. Apply \`.denoise\` separately if needed. Voice notes remain WhatsApp voice notes.
- \`.subtitles [auto|en|fr|es|...]\`: reply to audio/video; return an SRT document with Whisper's timestamped segments for up to the first 90 seconds. Nothing is automatically burned into the video. This uses **local** whisper.cpp speech recognition (an AI model running on the server, without a paid API). Optional runtime: \`whisper-cli\` (or custom \`MSCC_WHISPER_BIN\`) and an installed model path in \`MSCC_WHISPER_MODEL\`, e.g. \`/opt/mscc/tools/whisper/ggml-base.bin\`. The model is **not automatically downloaded** as part of the production deployment. If unavailable, the command reports the missing local model clearly. Recognition may be wrong when speech is quiet, overlaps, or background noise is strong. A small CPU model can take noticeable processing time.

All three are run through the same bounded media queue; SRT is a companion subtitle file, not a modified video.
