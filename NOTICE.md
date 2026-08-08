# Software de terceros

Este proyecto usa componentes de otros. Aquí está de dónde vienen y bajo qué
condiciones, que hay que mantener si se redistribuye.

## ncnn

Motor de inferencia de Tencent. Es lo que ejecuta los modelos sobre Vulkan.

- https://github.com/Tencent/ncnn
- Licencia BSD 3-Clause
- Versión 20260526, binarios precompilados para Android

No está en el repositorio. Lo descarga `tools/traer_dependencias.ps1`.

## rife-ncnn-vulkan

Implementación de RIFE sobre ncnn, de nihui. El código en `app/src/main/cpp/rife/`
viene de aquí.

- https://github.com/nihui/rife-ncnn-vulkan
- Licencia MIT
- RIFE original: https://github.com/hzwer/arXiv2020-RIFE

**Está modificado.** Los cambios están marcados con comentarios en el código:

- `warp.cpp`: `opt.use_shader_pack8` ya no existe en ncnn moderno.
- `rife.cpp`: daba por hecho el soporte de fp16 e int8 en vez de preguntarlo a
  la GPU, y el camino rápido de descarga se elige mirando justo esas banderas.
- `rife.cpp`: `to_pixels` escribía tres bytes por píxel sin mirar cuántos
  canales había.
- `rife.cpp`: sin comprobar que los shaders compilaran, un fallo dejaba el
  SPIR-V vacío y `create()` leía de un puntero nulo.

Los pesos del modelo (`rife-v4.6`) tampoco están en el repositorio.

## waifu2x-ncnn-vulkan

Implementación de waifu2x sobre ncnn, de nihui. El código en
`app/src/main/cpp/waifu2x/` viene de aquí, sin modificar.

- https://github.com/nihui/waifu2x-ncnn-vulkan
- Licencia MIT
- waifu2x original: https://github.com/nagadomi/waifu2x

Los modelos usados son `models-cunet`, que es el entrenado para dibujo.

## El icono

El gato del icono es un diseño propio del autor de la app.
