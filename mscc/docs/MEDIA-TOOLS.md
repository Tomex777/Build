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



## Additional media commands (same unmerged feature branch)

- .stripmeta: strip EXIF, GPS and other embedded metadata by re-encoding JPEG/PNG pixels.
- .silencecut [gentle|normal|aggressive]: shorten quiet gaps in voice notes/audio. Has INTERNAL background-noise reduction before silence detection; optional local RNNoise model via MSCC_RNNOISE_MODEL. Without the model, FFmpeg's mild spectral denoiser is used. Loud traffic, overlapping voices and music may remain; no separate .voiceisolate command exists.
- .aspectratio <9:16|16:9|1:1|4:5> [blur|fit|crop]: reframe up to 45 seconds for status/reels. Default blurred-background fit; black padding or edge crop available. H.264/AAC MP4, 19MB maximum.
- Deliberately excluded: .subtitles/.srt, .captionstyle, .videomute, .collage.
- No paid AI key needed. RNNoise is optional and not automatically installed.
