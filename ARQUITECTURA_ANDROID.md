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

## Compilación C (esta entrega): conectar con la computadora
Funciona junto con **JorCam 1.0.2 de PC** (la app sola no hace nada en este modo).
- **Cómo funciona:** la app hace lo mismo que la página del teléfono de la PC: le dice a la PC qué cámaras tiene, escucha sus órdenes (abrir una cámara, grabar, pausar, reanudar, detener) y manda sus eventos. La PC controla todo.
- **Video:** el teléfono graba por su cuenta (MP4 con audio) y, **al detener, lo manda a la PC** (más seguro que transmitir en vivo). Si descartas la grabación desde la PC, el video no se envía.
- **Audio:** el micrófono del teléfono es solo la referencia para sincronizar; el audio definitivo es el del micrófono de la PC (la V8), como hasta ahora.
- **Pausa:** igual que con Chrome, el teléfono no se pausa; la PC recorta esos intervalos.
- **USB:** depuración USB y cable (la PC ya hace `adb reverse`); la app se conecta a 127.0.0.1, sin código.
- **Wi-Fi:** en el panel de la PC se activa "Conexión por Wi-Fi", que muestra y anuncia la dirección y un código de 6 números nuevo cada vez; la app los pide. Solo se pueden usar 4 rutas del servidor (`/hola`, `/orden`, `/evento`, `/chunk`) y solo con el código; tras 15 intentos fallidos, el Wi-Fi se apaga solo. La conexión es de la red local, sin cifrar.
- **Cámaras:** la app ofrece todas las cámaras del teléfono (la trasera principal primero); la PC elige y cambia entre ellas como con Chrome.
- **Si la app pasa a segundo plano grabando:** se detiene lo grabado y se manda a la PC.
- **Módulos nuevos:** ModoPC.kt; cambios en CamaraRecorder.kt (listar cámaras y abrir una por identificador), MainActivity.kt y el manifiesto (permite http en la red local).

## Compilación D (esta entrega): vista previa para la PC
Funciona junto con **JorCam 1.0.3 de PC**.
- La app copia lo que ve la cámara con PixelCopy (sin agregar nada a la cámara ni a la grabación, así no se arriesga el video) y manda a la PC unos 4 fotogramas por segundo, de 640x360 en JPEG.
- **La PC los endereza** (usa el giro del sensor que manda la app) y los voltea en espejo si la cámara es la frontal. Si el fotograma llega mal orientado, el botón "Girar la vista previa" del panel lo corrige y queda guardado.
- Si la PC no quiere vista previa (desactivada, o está activa la cámara de la laptop), la app deja de mandar fotogramas durante unos segundos y vuelve a preguntar.
- Cambios: ModoPC.kt (envío de fotogramas), CamaraRecorder.kt (dos propiedades), MainActivity.kt (una línea). No cambia el Gradle.

## Compilación E (esta entrega): calidad máxima y vista previa vertical
Funciona junto con **JorCam 1.0.4 de PC**.
- **Grabación para la PC en máxima calidad:** 1920x1080 a 30 cuadros fijos; **HEVC a 10 Mbps** si el teléfono lo permite (misma nitidez que H.264 con mucho menos peso) y, si no, H.264 a 16 Mbps. La app anuncia "Grabando en HEVC" o "en H.264". La PC vuelve a codificar el video final, así que este archivo es solo el material de origen.
- **Modo directo:** sin cambios (H.264 a 5 Mbps al grabar y 4 Mbps el resultado), que fue lo que probaste y te gustó.
- **Vista previa ya derecha:** la app pide el fotograma con la forma vertical de la pantalla (540 de ancho) y manda "giro 0"; la PC no tiene que girarlo.
- Cambios: CamaraRecorder.kt (perfil de calidad), ModoPC.kt y MainActivity.kt (una línea). No cambia el Gradle.

## Límites y riesgos
1. No está compilada ni probada en un teléfono. Esperar errores en la primera compilación.
2. El Moto E40 es de gama baja: procesar 20 minutos puede ser lento y calentar el teléfono.
3. La grabación vertical se guarda como 1920x1080 con la marca de rotación (así lo hacen los teléfonos); los reproductores la muestran vertical.
4. El visor se ve completo en vertical con franjas negras arriba y abajo en pantallas muy altas.
5. Elegir un micrófono depende de que el teléfono lo acepte; el USB suele funcionar, el Bluetooth no está garantizado.
6. Google Play: cuenta personal nueva exige 12 testers por 14 días y tarjeta no prepago; mientras tanto, APK directo.
