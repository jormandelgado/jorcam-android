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

## Compilación B2 (siguiente): subtítulos (Pro)
- whisper.cpp dentro de la app (compilado con el NDK; modelo que se baja con reanudación; se transcribe cada tramo con voz).
- Una línea por subtítulo, ¿ y ¡ automáticos, revisión del texto antes de guardar (como en la PC).
- Se hace aparte porque necesita código en C++ que no se puede comprobar sin compilar.

## Compilación C: Modo 1 (cámara para la PC)
USB y Wi-Fi, con código de emparejamiento, selección de cámara y micrófono del teléfono. Requiere cambios también en JorCam de PC
(un receptor de video).

## Límites y riesgos
1. No está compilada ni probada en un teléfono. Esperar errores en la primera compilación.
2. El Moto E40 es de gama baja: procesar 20 minutos puede ser lento y calentar el teléfono.
3. La grabación vertical se guarda como 1920x1080 con la marca de rotación (así lo hacen los teléfonos); los reproductores la muestran vertical.
4. El visor se ve completo en vertical con franjas negras arriba y abajo en pantallas muy altas.
5. Elegir un micrófono depende de que el teléfono lo acepte; el USB suele funcionar, el Bluetooth no está garantizado.
6. Google Play: cuenta personal nueva exige 12 testers por 14 días y tarjeta no prepago; mientras tanto, APK directo.
