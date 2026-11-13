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

## Compilación B (siguiente): subtítulos y título (Pro)
- Subtítulos: whisper.cpp dentro de la app (compilado con el NDK de Android; modelo pequeño que se baja con reanudación).
- Texto encima del video con Media3 (título 5 segundos arriba, subtítulos abajo, sin fondo, con contorno).
- Detección de rostro con ML Kit (sin internet) para ubicar el título sin tapar la cara.
- Ajustes de color, tamaño y fuente.
Se deja para una compilación aparte porque necesita código nativo (C++) y no se puede comprobar sin compilar: si falla, no debe bloquear la A.

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
