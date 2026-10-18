# Parpadeo V7 — activos y prompts

Modo: generador integrado `image_gen`, edición de imagen de referencia con transparencia. No se usó CLI ni API externa. Generadas tres poses en paralelo, una llamada por pose. Ningún bitmap fue recortado, retocado, compuesto o reescalado mediante Python.

Petición del usuario: añadir un parpadeo un poco lento, conservando el movimiento V6.

Objetivo de edición: `assets-v4/cabeza.png`, RGBA 1254 × 1254. Cada salida conserva el lienzo completo. El proyecto debe mostrar solamente dos regiones oculares de cada salida para conservar cabello, cejas, boca y contorno originales.

## Archivos seleccionados

- `C:/Users/PC/Downloads/royer-method-work/referencias/test2xd-20261017/assets-v7/ojos-65.png` ← `C:/Users/PC/.codex/generated_images/01a14bcd-f0d9-7580-8e96-22c0ec393c12/exec-9d37cf3b-d52b-4801-bca1-2c7556f19db9.png`
- `C:/Users/PC/Downloads/royer-method-work/referencias/test2xd-20261017/assets-v7/ojos-30.png` ← `C:/Users/PC/.codex/generated_images/01a14bcd-f0d9-7580-8e96-22c0ec393c12/exec-69d0e541-75e4-475c-9e32-57db79968d19.png`
- `C:/Users/PC/Downloads/royer-method-work/referencias/test2xd-20261017/assets-v7/ojos-cerrados.png` ← `C:/Users/PC/.codex/generated_images/01a14bcd-f0d9-7580-8e96-22c0ec393c12/exec-dacb7a5e-3cc7-4c0e-bffa-b14f57977ab5.png`

Copia de los tres activos en el workspace, excluida de git: `C:/Users/PC/.codex/worktrees/head-bop-test2xd/royer-method/output/headbop-test2xd/assets-v7/`.

## Verificación de los activos

- Tres imágenes RGBA de 1254 × 1254, canal alfa 0…255. No se depende del fondo negro de la vista previa.
- Inspección visual: dos poses con apertura ocular decreciente y pose final cerrada, pestañas dibujadas sobre párpados opacos. Sin nueva expresión, letras ni objetos.
- Comparación SIFT sobre rostro excluyendo ojos: alineación de las poses intermedias aproximadamente 1 px. Pose cerrada presenta una diferencia de registro pequeña, aproximadamente 2 px y 0.17 grados; no es una comparación de render final.
- UV oculares previstas: izquierda `[0.410,0.630,0.595,0.775]`, derecha `[0.675,0.505,0.800,0.690]`. Debe verificarse en el motor que no asome el ojo original y que la transición de piel no forme un rectángulo visible.

## Prompts exactos

### ojos-65

```text
Use case: precise-object-edit.
Asset type: aligned animation frame for a slow blink; FULL HEAD RGBA image, edited from the input target.
Input image 1 is the edit target. It is a 1254 x 1254 transparent PNG of an orange-haired anime catgirl's tilted head.
Change ONLY the eyelids and eye apertures, in the original exact locations. Preserve the entire original head composition, position, scale, tilt, silhouette, orange hair, eyebrows, mouth, cheek blush, ears, skin shading and transparent background. No movement, no face reshaping, no color drift, no repainting elsewhere. Keep this exact full square canvas: 1254 x 1254. The left-screen eye is lower than the right-screen eye because the head is tilted; do NOT level the eyes.
The original eye areas in normalized canvas coordinates are approximately left eye x .410 to .595, y .630 to .775; right eye x .675 to .800, y .505 to .690. Edits belong there only.
The new eyelid skin must be opaque and fully cover the parts of the original iris/sclera that would be behind the lids. Never draw a lid line on top of a still-open eye. Keep the original anime linework, warm eyelid skin tone and subtle original shading. Both eyes move together in a relaxed blink, not a wink or a smile. No text, border, extra objects, watermarks or new facial expression. Preserve genuine alpha transparency outside the head.
Blink pose: BOTH eyes remain approximately 65 percent open, with the upper eyelids lowered 35 percent towards the original lower lids. The lower lids stay almost in their original places. The upper lash contours descend along the original tilted eye shapes. Still show the original amber irises through the reduced opening. This is a neutral intermediate blink pose, not sleepy and not angry.
```

### ojos-30

```text
Use case: precise-object-edit.
Asset type: aligned animation frame for a slow blink; FULL HEAD RGBA image, edited from the input target.
Input image 1 is the edit target. It is a 1254 x 1254 transparent PNG of an orange-haired anime catgirl's tilted head.
Change ONLY the eyelids and eye apertures, in the original exact locations. Preserve the entire original head composition, position, scale, tilt, silhouette, orange hair, eyebrows, mouth, cheek blush, ears, skin shading and transparent background. No movement, no face reshaping, no color drift, no repainting elsewhere. Keep this exact full square canvas: 1254 x 1254. The left-screen eye is lower than the right-screen eye because the head is tilted; do NOT level the eyes.
The original eye areas in normalized canvas coordinates are approximately left eye x .410 to .595, y .630 to .775; right eye x .675 to .800, y .505 to .690. Edits belong there only.
The new eyelid skin must be opaque and fully cover the parts of the original iris/sclera that would be behind the lids. Never draw a lid line on top of a still-open eye. Keep the original anime linework, warm eyelid skin tone and subtle original shading. Both eyes move together in a relaxed blink, not a wink or a smile. No text, border, extra objects, watermarks or new facial expression. Preserve genuine alpha transparency outside the head.
Blink pose: BOTH eyes remain approximately 30 percent open, upper eyelids lowered 70 percent towards the original lower lids. The lower lids stay almost in their original places. Only narrow amber eye apertures remain, following each original tilted eye. Keep eyebrows unchanged. This is an intermediate closing blink pose.
```

### ojos-cerrados

```text
Use case: precise-object-edit.
Asset type: aligned animation frame for a slow blink; FULL HEAD RGBA image, edited from the input target.
Input image 1 is the edit target. It is a 1254 x 1254 transparent PNG of an orange-haired anime catgirl's tilted head.
Change ONLY the eyelids and eye apertures, in the original exact locations. Preserve the entire original head composition, position, scale, tilt, silhouette, orange hair, eyebrows, mouth, cheek blush, ears, skin shading and transparent background. No movement, no face reshaping, no color drift, no repainting elsewhere. Keep this exact full square canvas: 1254 x 1254. The left-screen eye is lower than the right-screen eye because the head is tilted; do NOT level the eyes.
The original eye areas in normalized canvas coordinates are approximately left eye x .410 to .595, y .630 to .775; right eye x .675 to .800, y .505 to .690. Edits belong there only.
The new eyelid skin must be opaque and fully cover the parts of the original iris/sclera that would be behind the lids. Never draw a lid line on top of a still-open eye. Keep the original anime linework, warm eyelid skin tone and subtle original shading. Both eyes move together in a relaxed blink, not a wink or a smile. No text, border, extra objects, watermarks or new facial expression. Preserve genuine alpha transparency outside the head.
Blink pose: BOTH eyes fully CLOSED, relaxed and neutral. Replace each eye aperture with the original pale skin tone and render a single natural, gently curved dark lash seam near the lower original eye line, following its tilted perspective. No iris or sclera remains visible. There is subtle soft eyelid shading; eyebrows and mouth remain EXACTLY as original. No happy squint, no smile, no tears.
```

