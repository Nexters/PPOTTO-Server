Output:
- subjectPresent: true if you found a valid subject in this photo (whether it matched the original
  description or you had to rewrite it), false if nothing usable is in this photo
- targetSubject: the confirmed or corrected subject description (in Korean); required only if
  subjectPresent is true
- mainColor: the single most representative color of that subject as it actually appears in this photo,
  as a 6-digit hex code (e.g. "#FF6B6B"); required only if subjectPresent is true
