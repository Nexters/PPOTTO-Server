## Choosing the sticker photo and subject

Pick a source photo and a subject that a user would intuitively find pretty, cool, cute, or impressive.

### A good candidate

- The subject is clear and large enough, has good lighting and color, and has an appealing composition or pose.
- The subject's silhouette and meaning survive as an independent element even with the background removed.
- It symbolically represents the theme well, with clear emotion or personality.

### Never pick

- **A `targetSubject` that is not literally visible in the `sourcePhotoId` photo you chose.** Even a plausible-sounding or thematically fitting description is a critical failure if it is not really in that photo: the downstream cutout step will have nothing to isolate and will fail.
- Subjects that are blurry, dark, or too small, so they would look poor after cutout.
- Subjects where multiple objects overlap in a complex way with ambiguous boundaries.
- Scenes where the background itself is essential, so removing it would weaken the meaning.
- The entire captured scene as one "subject" — a whole table spread, a whole group of people, or any wide area that is not actually a landscape. Describing it as "an entire table full of various foods" means the cutout ends up looking almost identical to the original photo.
- A photo that does not look like an actual camera photo: a screenshot, an app or UI screen, an icon, an illustration, a 3D render, or anything that already looks synthetic or previously AI-generated. The cutout tool refuses such images ("I can't edit an already-generated image"), so prefer another candidate in this theme if one exists.
- A photo where more than one separate, individually sticker-worthy object is prominent in frame — two people who could each stand alone, several separately plated dishes, multiple pets each looking at the camera. The background-removal step keeps every foreground object it detects, not just the one named in `targetSubject`, so any other prominent object will show up in the final sticker alongside it. Only pick such a photo if the named subject is the sole prominent object, or every other visible object is naturally attached to it, such as a hand holding it.

### How specific to be

Write `targetSubject` specifically enough that the cutout target is unambiguous, like "a person in red clothes smiling" or "a yellow character doll on a desk".

If the scene is a table with multiple foods or objects, or a scene with multiple people, pick one small independently-isolable thing within it and narrow the description accordingly: "a single piece of sushi on a plate", "a single red flower on the table". The exception is landscape and scenery themes, where the whole scene itself is the intended subject.
