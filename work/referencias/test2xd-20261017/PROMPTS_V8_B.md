# Head bop V8 — nuevas poses intermedias B

## Refinamiento seleccionado de ojos-10.png

La primera propuesta de 10% era demasiado similar a20% en el ojo izquierdo y se descartó. Se conserva su salida original del generador indicada arriba. La variante final sí presenta una rendija más estrecha en ambos ojos.

Modo integrado, transparencia; entradas: cabeza.png original, assets-v8/ojos-20.png como referencia, assets-v7/ojos-cerrados.png como referencia.

Salida seleccionada: `C:/Users/PC/.codex/generated_images/01a14bd8-ccec-7d91-99a2-6bf3861ac68e/exec-e4dac64d-65ff-4a22-beac-d2bf423a851b.png`

Se copió a ambas rutas finales ojos-10.png indicadas arriba.

Prompt exacto:

```text
Use case: precise-object-edit.
Asset type: an exactly aligned blink in-between sprite, 1254 by1254 RGBA.
Image1 is original cabeza.png edit target,Image2 is20%-open reference,Image3 fullyclosed reference.
Create a NEW10%-open frame by editing ONLY the original eyes inImage1. This should be visibly HALFWAY betweenImage2's20%-open eyes andImage3's fullyclosed eyes. There should be only a HAIRLINE tiny black-orange slit under each upper lash. Make BOTH eyes almost shut: the eye on the LEFT side of the canvas in particular needs much less visible iris thanImage2, only HALF its visible orange iris sliver. Retain only a trace of the original orange iris, not the large iris crescent inImage2. The new upper lashes have nearly reached the fixed lower lids; don't move the lower lids. Opaque matching skin covers the formerlyopen eye area above the newlashline. Preserve originaliris design and gaze hidden under lids, do not shrinkiris.
Avoid expression change. Not smiling,not sleepy,not squinting. Neutral blink approachingclosure. Preserve original lower-lid shape; same face tilt and lash direction.
LOCK position,1254-square canvas, head scale, faceoutline, nose,brows,mouth,hair,ears,colors,lighting. Preserve genuinealpha outsidehead and originalopaque skin. Do not reframe,shift,rotateor redraw head. Return fulloriginalhead with only eyelids changed.
Eyeboxes UV leftscreen[.410,.630,.595,.775],rightscreen[.675,.505,.800,.690].
```

Control: las tres seleccionadas son1254×1254RGBA y alpha0..255. Revisión visual de las posiciones de ojos y recortes; el tamaño del lienzo no cambió. En cajas interiores fijas de ambos ojos se verificó descenso estricto de píxeles de iris naranja en orden original>65>45>30>20>10>cerrado. Datos en `validacion-apertura-v8-b.json`; es una comprobación de orden relativo, no medición exacta del porcentaje de apertura. Las imágenes generadas pueden diferir ligeramente fuera de los ojos; el motor conserva cabeza original y recorta únicamente las zonas UV de ojos.

Modo: generador integrado `image_gen`; edición `precise-object-edit`; transparencia solicitada. Una llamada por pose. Sin CLI/API fallback ni edición de píxeles por Python. Los nombres indican la apertura objetivo, no una medición anatómica exacta.

Entradas para todas las llamadas, en este orden:

1. `assets-v4/cabeza.png` — objetivo de edición.
2. `assets-v7/ojos-65.png` — referencia.
3. `assets-v7/ojos-30.png` — referencia.
4. `assets-v7/ojos-cerrados.png` — referencia.

Se conserva el original y cada salida del generador. El motor consume sólo las zonas UV de ojos; el resto de la cabeza proviene del original.

## ojos-45.png

Salida original: `C:/Users/PC/.codex/generated_images/01a14bd8-ccec-7d91-99a2-6bf3861ac68e/exec-f5fdfbee-762d-4063-a826-43707eb4d06d.png`

Seleccionada: `C:/Users/PC/Downloads/royer-method-work/referencias/test2xd-20261017/assets-v8/ojos-45.png`

Copia de proyecto: `C:/Users/PC/.codex/worktrees/head-bop-test2xd/royer-method/output/headbop-test2xd/assets-v8/ojos-45.png`

Prompt exacto:

```text
Use case: precise-object-edit.
Asset type: an exact aligned 1254×1254 RGBA animation sprite, one blink in-between frame.
Input images: Image 1 is the edit target, original fully open eyes cabeza.png. Image 2 is the existing 65% open eye-pose reference. Image 3 is the existing 30% open pose. Image 4 is the fully closed pose.
Only edit BOTH EYES in Image 1. Keep the same original head, same exact 1254-square canvas, head position, angle, scale, proportions, silhouette, hair strands, ears, eyebrows, nose, mouth, iris colors, lower eyelid positions and lighting. The surrounding head must be unchanged; do not redraw the hairstyle or move any face feature.
This is a neutral blink frame, not a smiling or sleepy expression. The upper eyelids descend toward the FIXED lower eyelids along the original face tilt. They cover the original irises with opaque matching skin above the new lash line. The visible irises are clipped, not shrunk or moved. Keep original gaze and high-quality painted anime linework. Upper lashes follow original thickness and perspective. Eyes have different projected shapes because the head is tilted; preserve that.
The eyes are contained in these canvas UV boxes: left-screen eye [0.410,0.630,0.595,0.775], right-screen eye [0.675,0.505,0.800,0.690]. Do not move or enlarge them.
The area outside the head stays genuinely alpha-transparent. Do not add a colored backdrop, text, guides, outlines or new objects. Return the COMPLETE original head sprite with edited eyes.
Target blink opening: 45% OPEN (100 is original fully open; 0 is closed). This pose must be visibly BETWEEN Image 2 (65% open) and Image 3 (30% open), approximately mid-way. Keep around 45% of the original open-eye aperture, with the upper lash well below the 65% pose but above the 30% pose.
```

## ojos-20.png

Salida original: `C:/Users/PC/.codex/generated_images/01a14bd8-ccec-7d91-99a2-6bf3861ac68e/exec-159e2bca-8a3e-473d-9ce9-33ab6607f50f.png`

Seleccionada: `C:/Users/PC/Downloads/royer-method-work/referencias/test2xd-20261017/assets-v8/ojos-20.png`

Copia de proyecto: `C:/Users/PC/.codex/worktrees/head-bop-test2xd/royer-method/output/headbop-test2xd/assets-v8/ojos-20.png`

Prompt exacto:

```text
Use case: precise-object-edit.
Asset type: an exact aligned 1254×1254 RGBA animation sprite, one blink in-between frame.
Input images: Image 1 is the edit target, original fully open eyes cabeza.png. Image 2 is the existing 65% open eye-pose reference. Image 3 is the existing 30% open pose. Image 4 is the fully closed pose.
Only edit BOTH EYES in Image 1. Keep the same original head, same exact 1254-square canvas, head position, angle, scale, proportions, silhouette, hair strands, ears, eyebrows, nose, mouth, iris colors, lower eyelid positions and lighting. The surrounding head must be unchanged; do not redraw the hairstyle or move any face feature.
This is a neutral blink frame, not a smiling or sleepy expression. The upper eyelids descend toward the FIXED lower eyelids along the original face tilt. They cover the original irises with opaque matching skin above the new lash line. The visible irises are clipped, not shrunk or moved. Keep original gaze and high-quality painted anime linework. Upper lashes follow original thickness and perspective. Eyes have different projected shapes because the head is tilted; preserve that.
The eyes are contained in these canvas UV boxes: left-screen eye [0.410,0.630,0.595,0.775], right-screen eye [0.675,0.505,0.800,0.690]. Do not move or enlarge them.
The area outside the head stays genuinely alpha-transparent. Do not add a colored backdrop, text, guides, outlines or new objects. Return the COMPLETE original head sprite with edited eyes.
Target blink opening: 20% OPEN (100 is original fully open; 0 is closed). This pose is MORE CLOSED than Image 3 (30% open), with around 20% of the original open-eye aperture. Only a narrow orange iris crescent and tiny white sliver remain. It must be visibly more open than a nearly-closed 10% slit. Upper lash approaches the lower lid, keeping lower-lid pixels in the original place.
```

## ojos-10.png

Salida original: `C:/Users/PC/.codex/generated_images/01a14bd8-ccec-7d91-99a2-6bf3861ac68e/exec-8a051087-d68f-4a4a-8808-73342d530136.png`

Seleccionada: `C:/Users/PC/Downloads/royer-method-work/referencias/test2xd-20261017/assets-v8/ojos-10.png`

Copia de proyecto: `C:/Users/PC/.codex/worktrees/head-bop-test2xd/royer-method/output/headbop-test2xd/assets-v8/ojos-10.png`

Prompt exacto:

```text
Use case: precise-object-edit.
Asset type: an exact aligned 1254×1254 RGBA animation sprite, one blink in-between frame.
Input images: Image 1 is the edit target, original fully open eyes cabeza.png. Image 2 is the existing 65% open eye-pose reference. Image 3 is the existing 30% open pose. Image 4 is the fully closed pose.
Only edit BOTH EYES in Image 1. Keep the same original head, same exact 1254-square canvas, head position, angle, scale, proportions, silhouette, hair strands, ears, eyebrows, nose, mouth, iris colors, lower eyelid positions and lighting. The surrounding head must be unchanged; do not redraw the hairstyle or move any face feature.
This is a neutral blink frame, not a smiling or sleepy expression. The upper eyelids descend toward the FIXED lower eyelids along the original face tilt. They cover the original irises with opaque matching skin above the new lash line. The visible irises are clipped, not shrunk or moved. Keep original gaze and high-quality painted anime linework. Upper lashes follow original thickness and perspective. Eyes have different projected shapes because the head is tilted; preserve that.
The eyes are contained in these canvas UV boxes: left-screen eye [0.410,0.630,0.595,0.775], right-screen eye [0.675,0.505,0.800,0.690]. Do not move or enlarge them.
The area outside the head stays genuinely alpha-transparent. Do not add a colored backdrop, text, guides, outlines or new objects. Return the COMPLETE original head sprite with edited eyes.
Target blink opening: 10% OPEN (100 is original fully open; 0 is closed). This pose is NEARLY CLOSED, distinctly more closed than Image 3 (30% open) and 20% open. Leave only 10% of original eye aperture: a very thin dark/orange slit under each upper lash, almost touching the fixed lower eyelid. Not fully closed: a tiny original orange iris glint remains. Do not make a happy closed-eye arch.
```


