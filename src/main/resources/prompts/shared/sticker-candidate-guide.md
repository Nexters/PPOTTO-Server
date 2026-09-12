Pick a sticker source photo and subject that a user would intuitively find pretty, cool, cute, or
impressive.
Criteria for a good sticker candidate:
- The subject is clear and large enough, has good lighting and color, and has an appealing composition or
  pose
- The subject's silhouette and meaning survive well as an independent element even with the background
  removed
- It symbolically represents the theme well, with clear emotion or personality
Avoid:
- Describing a targetSubject that is not actually, literally visible in the sourcePhotoId photo you chose —
  even a plausible-sounding or thematically fitting description is a critical failure if it's not really in
  that photo, because the downstream cutout step will have nothing to isolate and will fail
- Subjects that are blurry, dark, or too small, so they'd look poor after cutout
- Subjects where multiple objects overlap in a complex way with ambiguous boundaries
- Scenes where the background itself is essential, so removing it would weaken the meaning
- Lumping the entire captured scene (an entire table spread, an entire group of people, or any wide area
  that isn't actually a landscape) into a single "subject" — for example, describing it as "an entire table
  full of various foods" means the cutout would end up looking almost identical to the original photo
- Picking a sourcePhotoId photo that itself doesn't look like an actual camera photo — e.g. a screenshot, an
  app/UI screen, an icon, an illustration, a 3D render, or an image that otherwise looks already synthetic or
  previously AI-generated/edited. The downstream cutout tool refuses to process such images ("I can't edit an
  already-generated image"), so if another candidate photo exists in this theme, prefer that one instead
- Picking a sourcePhotoId photo where more than one separate, individually sticker-worthy object is
  prominent in frame (e.g. two people who could each stand alone, several separately plated dishes, multiple
  pets each looking at the camera) — the downstream background-removal step keeps every foreground object it
  detects, not just the one named in targetSubject, so any other prominent object left in the photo will show
  up in the final sticker alongside it. Only pick such a photo if the named subject is the sole prominent
  object in frame, or every other visible object is naturally attached to it (e.g. a hand holding it);
  otherwise prefer a candidate photo where the subject is alone
Write targetSubject specifically enough that the cutout target is unambiguous, like "a person in red clothes
smiling" or "a yellow character doll on a desk". If the scene is a table with multiple foods/objects, or a
scene with multiple people, pick just one small, independently-isolable thing within it and narrow the
description accordingly, like "a single piece of sushi on a plate" or "a single red flower on the table"
(the exception is landscape/scenery themes, where the whole scene itself is the intended subject).
