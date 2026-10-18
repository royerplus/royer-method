# Parpadeo V8 · poses 90, 80 y 55

Modo: generador de imágenes integrado (`image_gen`), edición de imágenes locales inspeccionadas con `view_image`; `transparent_background=true`. No se modificaron imágenes con Python ni se tocaron los originales.

## Selección final

| Pose | Archivo seleccionado del generador | Destino |
|---|---|---|
|90% abierta|`exec-ab667a68-9eb2-4ded-b070-82298d3f0ff5.png`|`assets-v8/ojos-90.png`|
|80% abierta|`exec-6aba3b81-e01e-46d4-9bbd-07a0b9e4029b.png`|`assets-v8/ojos-80.png`|
|55% abierta|`exec-cf469f4c-1c83-45cb-a760-9e9755c2cca1.png`|`assets-v8/ojos-55.png`|

Carpeta original del generador: `C:/Users/PC/.codex/generated_images/01a14bd8-5500-7c22-a047-fa9184322557/`.

Destino externo completo: `C:/Users/PC/Downloads/royer-method-work/referencias/test2xd-20261017/assets-v8/`.
Copia de proyecto ignorada por Git: `C:/Users/PC/.codex/worktrees/head-bop-test2xd/royer-method/output/headbop-test2xd/assets-v8/`.

## Validación y límites

Los tres PNG son RGBA 1254×1254, alpha 0–255. Se inspeccionaron visualmente. Las aperturas son poses reales distintas, no simple transparencia. Altura del componente naranja del iris, en rectángulos fijos de análisis izquierdo/derecho: base81/95px,90=75/87,80=72/83,65V7=64/80,55=52/60,30V7=47/59. Es una medida aproximada de iris visible, no una calibración exacta de porcentaje de apertura.

La invariancia pixel a pixel NO se cumple; hay pequeñas modificaciones en otras zonas de la cabeza. La capa de parpadeo del motor usa exclusivamente los recortes de ojos UV izquierda[.410,.630,.595,.775], derecha[.675,.505,.800,.690]; no debe sustituirse la cabeza entera. Borde inferior del iris derecho base/90≈829px,80/65≈818px,55/30≈815px: deriva de10–14px que ya existe en los65/30 deV7. No se ha afirmado registro perfecto ni validado reproducción/render desde este subtrabajo. La primera variante90 quedó ampliada y se descartó. La primera55 resultó demasiado cercana a65 y se descartó. Se conservan los originales generados, no se borraron.

## Prompts exactos

### ojos-90.png · primera llamada (descartada)

Referencias: `assets-v4/cabeza.png`, `assets-v7/ojos-65.png`.

```text
Use case: precise-object-edit. Asset type: frame of an existing anime eyelid blink sprite. Input image 1 is the sole edit target: the exact 1254 x 1254 RGBA head. Image 2 is a supporting alignment and style reference of the same head with eyes 65% open. Edit ONLY the upper eyelids and newly covered eye skin of image 1, producing a VERY subtle 90% open blink in-between (10% closed). Both upper lash contours must lower only a small real amount from the fully-open original; the visible eye aperture height must be about 90% of image 1 and clearly MORE OPEN than image 2. Keep the lower eyelid contours fixed in their original positions, keep iris colors, pattern, gaze and underlying iris size exactly, occluding only a thin upper strip with opaque matching skin. Do not squeeze or scale the iris or the face. Neutral blink, not sleepy, smiling, angry or squinting. Exact registration is essential: preserve the same canvas 1254x1254, scale, tilted pose and head position; absolutely keep the eyebrows, mouth, cheeks, hair, ears, skin shading outside the eye patches, silhouette and all transparency unchanged. Do not recenter, redraw or move the head, no background, no text, no new features. True transparent background, full original head in its original position. This is ONE frame only, no sheet.
```

### ojos-90.png · reintento seleccionado

Referencias: `assets-v4/cabeza.png`.

```text
Use case: precise-object-edit. This is a MINUSCULE eyelid-only pixel-registered edit of the provided transparent 1254x1254 PNG. The single provided image is the edit target; retain the exact input pixel registration. Do not enlarge, rotate, move, recenter, rescale or regenerate the head. Keep every pixel outside the eyes exactly unchanged, with identical hair/ears silhouette and transparent margins. Change ONLY the top lash lines and matching skin directly above the eyes: lower each upper eyelid by about TEN PERCENT of its eye opening. Result should be 90% open, almost the same as the source but top10% of the irises slightly occluded. Lower eyelids stay completely fixed, preserve gaze, pupil and iris size/details. The head tilted pose, eyebrows, mouth, blush, ears and all hair are exactly unchanged. A subtle neutral blink animation in-between frame, not sleepy or smiling. Transparent RGBA background must be preserved. ONE full-head PNG, same 1254x1254 canvas, exact same framing and margins. Absolutely no head reillustration or camera change.
```

### ojos-80.png · primera llamada (seleccionada)

Referencias: `assets-v4/cabeza.png`, `assets-v7/ojos-65.png`.

```text
Use case: precise-object-edit. Asset type: frame of an existing anime eyelid blink sprite. Input image 1 is the sole edit target: the exact 1254 x 1254 RGBA head, fully open eyes. Image 2 is the same aligned head at 65% open, supporting pose reference. Edit ONLY the upper eyelids and newly covered eye skin of image 1, producing an 80% open blink in-between (20% closed). Lower both upper lash contours by a modest but clearly visible real amount: each visible eye aperture height must be about 80% of image 1, halfway between fully open and the more closed image 2. Keep the lower eyelid contours fixed in their original positions, keep original iris colors, pattern, gaze and underlying iris size, occluding upper portions with opaque matching skin. Do not squeeze or scale the iris or the face. This is a neutral blink; no sleepy expression, smile, anger, or change in gaze. Exact registration is essential: preserve the same canvas 1254x1254, scale, tilted pose and head position; absolutely keep eyebrows, mouth, cheeks, hair, ears, skin shading outside the eye patches, silhouette and transparency unchanged. Do not recenter, redraw or move the head, no background, no text, no new features. True transparent background, full original head in its original position. ONE frame only, no sheet.
```

### ojos-55.png · primera llamada (descartada)

Referencias: `assets-v4/cabeza.png`, `assets-v7/ojos-65.png`, `assets-v7/ojos-30.png`.

```text
Use case: precise-object-edit. Asset type: frame of an existing anime eyelid blink sprite. Input image 1 is the sole edit target: exact 1254 x 1254 RGBA head with fully open eyes. Image 2 is the same head with eyelids 65% open, image 3 with eyelids 30% open. Produce an aligned 55% OPEN eyelid in-between, distinctly more closed than image 2 and more open than image 3. Edit ONLY the upper eyelids and skin newly covering the top of the eye: lower the upper lash contours until each visible eye aperture height is 55% of the fully open original. Keep the lower eyelids exactly fixed, original iris color/pattern/gaze and underlying iris size, hiding upper portions behind opaque matching skin. Do not compress or stretch the iris or face. Neutral eyelid closure for a natural blink; do not draw a smiling, sleepy, angry or squinting expression. Exact registration is essential: same 1254x1254 canvas, scale, tilted pose, head position, original eyebrow positions, hair, ears, mouth, cheeks, skin shading outside eye patches, silhouette and all transparent regions must remain unchanged. No head recentering or redraw, no background, text or extra features. True transparent background, full original head in exact original position. ONE frame only, no sprite sheet.
```

### ojos-55.png · reintento seleccionado

Referencias: `assets-v7/ojos-65.png`, `assets-v7/ojos-30.png`.

```text
Use case: precise-object-edit. The first image is the EDIT TARGET, a transparent1254x1254 head with eyes partway through a blink (roughly65%open). The second image is the same head more closed (roughly30%open), only a supporting eyelid pose reference. Make one intermediate frame between these TWO eyelid poses: about two-thirds as open as the first image and visibly more open than the second. IMPORTANT: the eyes in the result must be visibly MORE CLOSED than the first image, with upper lashes descending about 12pixels toward the lower lashes on the eye at lower left and about 12pixels down/right on the eye at upper right. The upper lash contours are being lowered in a neutral natural blink. Lower eyelid/lower lash positions must stay fixed, original iris sizes stay fixed behind the eyelids, and cover newly closed upper iris portions with opaque skin matching the surrounding face. Do not stretch or flatten an entire eye; occlude the upper portion with the new top lid. Edit ONLY pixels directly around the eyes. Keep the entire rest of the first image unchanged: same1254x1254RGBAcanvas, exact tilted head pose, scale, position, margins, hair, ears, eyebrows, mouth, facial colors and transparency. Do not enlarge, recenter, move or redraw the head, no smiling or sleepy expression, no text or background. One single transparent PNG animation frame.
```

