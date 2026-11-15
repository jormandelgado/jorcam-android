# JorCam Android — arquitectura de esta compilación (A) y lo que sigue

Android 10 o superior (minSdk 29), objetivo Android 11 (targetSdk 30), pensado para un teléfono tipo Moto E40. Pantalla siempre
encendida y siempre vertical (no hay modo de pantalla apagada).

## Módulos (carpeta android/app/src/main/java/com/jormandelgado/jorcam)
| Archivo | Qué hace |
|---|---|
| MainActivity.kt | Pantalla: visor a pantalla completa, controles, avisos, flujo grabar/pausar/detener/descartar/cargar |
| CamaraRecorder.kt | Cámara con Camera2 y grabación con MediaRecorder: 1920x1080 con marca de rotación vertical, 30 cuadros fijos si se puede, micrófono elegible |
| Silencios.kt | Detección de voz (mismo algoritmo de la PC: umbral relativo a tu voz, ruidos cortos fuera, aire antes y después) |
| Recorte.kt | Corta y une los tramos con voz con Media3 Transformer |
| Licencia.kt | Claves JC- firmadas con Ed25519, verificadas sin internet (BouncyCastle); mismas claves que la PC |
| Almacen.kt | Guardar en Películas/JorCam, duración, reproducir, ver los videos |
| Anunciador.kt | Avisos por voz con el lector de pantalla (TalkBack, Jieshuo) o con la voz propia de JorCam |
| Ajustes.kt | Ajustes guardados en el teléfono |

## Gratis y Pro
Gratis: grabar, pausar, detener, descartar, cambiar de cámara, micrófono, quitar silencios (valores por defecto), reproducir, ver videos.
Pro: Cargar video (máximo 20 minutos), ajustes de agresividad y aire de los silencios; y en la compilación B, título y subtítulos.

## Compilación A (esta): incluida
Visor en vivo a pantalla completa (con un botón para ocultar los controles) · vertical 1080x1920 · selección de micrófono y de cámara con
aviso "Cámara frontal activa / trasera activa" · pausar / descartar · recorte de silencios y unión continua · guardado en Películas/JorCam ·
licencia offline idéntica a la PC · Cargar video (Pro) · reproducir último video · ver videos · compilación con GitHub Actions.

## Compilación B1 (esta entrega): título, nombre del archivo y detección de rostro
- Título (Pro): se escribe antes de grabar; aparece los primeros 5 segundos, **arriba de la cabeza**, solo letras con contorno, sin fondo.
- El título es también el **nombre del archivo** .mp4 (sin emojis ni caracteres inválidos), para identificar cada video con el lector de pantalla.
- Detección de rostro con ML Kit (sin internet) en varios cuadros del video; el título se acomoda para no tapar la cara y la letra se achica si no cabe.
- Ajustes del título (Pro): color, fuente (clásica, con serifas, gruesa) y tamaño.
- Módulos nuevos: Estilos.kt, Rostro.kt, TextoSobreVideo.kt (el texto se dibuja encima con Media3 al recortar el video).
- El código de los subtítulos ya está previsto en TextoSobreVideo (lista de subtítulos con tiempos y su posición debajo de la barbilla); en B1 va vacía.

## Compilación B2 (esta entrega): subtítulos automáticos (Pro)
- **Motor de voz:** whisper.cpp dentro de la app (código en C++ compilado con el NDK, solo para teléfonos de 64 bits). GitHub Actions lo descarga en cada compilación (versión fija v1.7.4).
- **Modelo de voz:** se baja una sola vez desde internet (es lo único para lo que la app usa internet), con reanudación si se corta; hay tres tamaños (tiny 75 MB, base 142 MB, small 466 MB). Después todo funciona sin internet.
- **Cómo transcribe:** solo los tramos con voz (los mismos del recorte de silencios), así los tiempos coinciden con el video final.
- **Texto:** se quitan etiquetas como [música], se agregan ¿ y ¡ cuando faltan, una sola línea por subtítulo (medida con la fuente real), y se reparte el tiempo según el largo de cada línea.
- **Posición:** debajo de la barbilla, usando la misma detección de rostro del título, sin tapar la cara.
- **Revisión (estilo TikTok):** una pantalla con un cuadro de edición independiente por frase ("Subtítulo 3 de 12, del segundo 8 al 10"), el título editable arriba, y los botones "Continuar con estos subtítulos" y "Guardar sin subtítulos". Una frase vacía se borra.
- **Ajustes (Pro):** activar o no los subtítulos, revisar antes de guardar, color, fuente y tamaño; y el modelo de voz.
- **Módulos nuevos:** Whisper.kt, Modelo.kt, Audio16k.kt, Subtitulos.kt, RevisionSubtitulos.kt, y en cpp/: CMakeLists.txt y whisper_jni.cpp.
- **Riesgos:** (1) es la parte más difícil de compilar a ciegas: puede fallar en el paso del código C++; (2) la velocidad en el Moto E40 es desconocida: la app anuncia cuánto tardó la transcripción; si es muy lento, elige el modelo "tiny"; (3) Whisper puede equivocarse con ruido o con palabras poco comunes: por eso existe la revisión.

## Compilación B2.1 (esta entrega): correcciones y mejoras de la revisión
- **Pantalla de revisión propia** (ya no es un cuadro de diálogo): los botones "Continuar con estos subtítulos" y "Guardar sin subtítulos" están arriba, fuera de la lista; el botón Atrás equivale a "Guardar sin subtítulos". Mientras está abierta, el resto de la app queda oculto para el lector de pantalla.
- **Escuchar cada tramo:** debajo de cada cuadro hay un botón "Reproducir este tramo" que reproduce solo ese fragmento del video original.
- **Emojis:** se conservan en el título (solo se quitan del nombre del archivo) y en los subtítulos. Casilla "Agregar emojis automáticos a los subtítulos (Pro)", dentro de Ajustes generales, desactivada por defecto. Es un buscador de palabras clave, **no entiende el sentido de la frase**: pone como mucho un emoji al final de la línea, nunca en dos líneas seguidas ni repite el mismo.
- **Selector de modo** al abrir la app (después de los permisos): grabación directa o conectar con la computadora (este segundo todavía avisa que llega en la próxima compilación). El botón "Cambiar de modo de trabajo" está en Ajustes generales.

## Compilación C (siguiente): conectar con la computadora
Diseño propuesto, tomado de cómo ya habla la página del teléfono con JorCam de PC (órdenes, eventos y trozos de video):
- **El teléfono graba el video por su cuenta** (H.264 con audio, como en el modo directo) y, al detener, **lo manda a la PC**. Es más seguro que transmitir en vivo: no depende de que el Wi-Fi sea estable durante la grabación.
- **La PC graba el audio con su micrófono** (como hoy) y hace todo lo demás igual: sincronía, silencios, título, subtítulos.
- **USB:** igual que ahora (depuración USB y `adb reverse`); la app se conecta a la PC por 127.0.0.1.
- **Wi-Fi:** la PC muestra y anuncia su dirección y un código de 6 números; la app los pide y se conecta. Windows preguntará una vez por el permiso del firewall.
- **La PC controla** (Grabar, Pausar, Detener, Cambiar cámara); la app muestra la cámara en pantalla y el estado.
- Requiere una versión nueva de JorCam de PC (1.0.2) y una compilación nueva de la app.

## Límites y riesgos
1. No está compilada ni probada en un teléfono. Esperar errores en la primera compilación.
2. El Moto E40 es de gama baja: procesar 20 minutos puede ser lento y calentar el teléfono.
3. La grabación vertical se guarda como 1920x1080 con la marca de rotación (así lo hacen los teléfonos); los reproductores la muestran vertical.
4. El visor se ve completo en vertical con franjas negras arriba y abajo en pantallas muy altas.
5. Elegir un micrófono depende de que el teléfono lo acepte; el USB suele funcionar, el Bluetooth no está garantizado.
6. Google Play: cuenta personal nueva exige 12 testers por 14 días y tarjeta no prepago; mientras tanto, APK directo.
