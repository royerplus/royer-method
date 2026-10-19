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

## Ajustes de imagen del editor

Las fórmulas del sombreador `CAPA` (`editor/motor/Sombreadores.kt`) para
Nitidez, Claridad, Exposición, Contraste, Sombras/Luces, Saturación,
Vivacidad, Desvanecer y Grano vienen de estos proyectos, adaptadas a GLSL ES
1.00 y marcadas en el código:

- **Nitidez**: AMD FidelityFX CAS 1.20190610 (camino sin escalado).
  https://github.com/GPUOpen-Effects/FidelityFX-CAS. Licencia MIT,
  Copyright (c) 2017-2019 Advanced Micro Devices, Inc. **Está modificado:** de
  la mitad del mando en adelante se dobla lo que afila.
- **Brillo, Exposición, Saturación, Sombras/Luces y Desvanecer**: GPUImage
  (filtros Brightness, Exposure, Saturation, HighlightShadow y Levels).
  https://github.com/BradLarson/GPUImage. Licencia BSD 3-Clause, Copyright (c)
  2012, Brad Larson, Ben Cochran, Hugues Lismonde, Keitaroh Kobayashi, Alaric
  Cole, Matthew Clark, Jacob Gundersen, Chris Williams. **Está modificado:** la
  exposición va en luz lineal con hombro, la saturación con tope de gama,
  sombras y luces también hacia el otro lado y el desvanecer se apaga en el
  blanco.
- **Claridad**: la fórmula de Poly Haven imgvwr (`post_clarity.glsl`),
  https://github.com/Poly-Haven/imgvwr. Licencia MIT, Copyright (c) 2026 Greg
  Zaal. El anillo de muestras de una sola pasada es propio.
- **Grano**: forma de FilmGrain de SweetFX, https://github.com/CeeJayDK/SweetFX,
  licencia MIT, Copyright (c) 2014 CeeJayDK; y el hash "Hash without Sine" de
  David Hoskins, https://www.shadertoy.com/view/4djSRW, licencia MIT,
  Copyright (c) 2014 David Hoskins.
- **Contraste**: la curva de `-sigmoidal-contrast` de ImageMagick
  (`MagickCore/enhance.c`), reescrita. https://imagemagick.org/license/

Texto de la licencia MIT (vale para CAS, imgvwr, SweetFX y el hash):

> Permission is hereby granted, free of charge, to any person obtaining a copy
> of this software and associated documentation files (the "Software"), to
> deal in the Software without restriction, including without limitation the
> rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
> sell copies of the Software, and to permit persons to whom the Software is
> furnished to do so, subject to the following conditions:
>
> The above copyright notice and this permission notice shall be included in
> all copies or substantial portions of the Software.
>
> THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
> IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
> FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
> AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
> LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
> FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
> IN THE SOFTWARE.

Texto de la licencia BSD 3-Clause (GPUImage):

> Redistribution and use in source and binary forms, with or without
> modification, are permitted provided that the following conditions are met:
>
> - Redistributions of source code must retain the above copyright notice,
>   this list of conditions and the following disclaimer.
> - Redistributions in binary form must reproduce the above copyright notice,
>   this list of conditions and the following disclaimer in the documentation
>   and/or other materials provided with the distribution.
> - Neither the name of the GPUImage framework nor the names of its
>   contributors may be used to endorse or promote products derived from this
>   software without specific prior written permission.
>
> THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
> AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
> IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
> ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
> LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
> CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
> SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
> INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
> CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
> ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
> POSSIBILITY OF SUCH DAMAGE.

## El icono

El gato del icono es un diseño propio del autor de la app.
