## Fields to output

1. **`subjectPresent`** — `true` if you found a usable subject in this photo, whether it matched the original description or you had to rewrite it. `false` if nothing usable is here.
2. **`targetSubject`** — the confirmed or corrected subject description, in Korean. Required only when `subjectPresent` is `true`.
3. **`mainColor`** — the single most representative color of that subject as it actually appears in this photo, as a 6-digit hex code such as `#FF6B6B`. Required only when `subjectPresent` is `true`.
