# Hoja de ruta de JorCam

## PC v1.0.2 (registrado)
1. **Vista previa en vivo en la ventana** (para encuadrarse y que un operador vea el video), conviviendo con la accesibilidad:
   - Cámara de la laptop: mostrar la imagen que ya recibe la página (hoy está oculta fuera de pantalla).
   - Cámara del teléfono: la imagen vive en la página del teléfono; hay que mandar fotogramas reducidos (unos 5 a 8 por segundo,
     360p) a la laptop para mostrarlos. Es un envío extra que se probará con cuidado para no restar cuadros a la grabación.
   - Accesibilidad: la imagen llevará una etiqueta corta ("Vista previa de la cámara") y no se leerá cada vez que cambie;
     habrá un interruptor "Mostrar vista previa" en Ajustes y un atajo de teclado.
2. Alinear la versión de PC con Android: los parámetros del recorte de silencios (agresividad y aire) pasarían a ser gratuitos; hoy en la PC siguen siendo Pro. **Confirmado por Jorman:** el recorte de silencios y todos sus parámetros son gratuitos en las dos plataformas.
3. Emojis en el título (hoy la casilla existe, pero la fuente puede no dibujarlos).
4. Clave atada a la computadora o verificación en línea (pedir a Payhip la integración alternativa).
5. Probar en Windows 10 y en una PC limpia; firma de código si se justifica.

## Android
- Compilación A: modo bolsillo básico (este paquete).
- Compilación B1: título, nombre del archivo y detección de rostro (Pro).
- Compilación B2: subtítulos con whisper.cpp y revisión por frases (Pro) — entregada, pendiente de prueba.
- Compilación C: conexión con la PC por USB y Wi-Fi.

## Android B2: revisión de subtítulos estilo TikTok
Una pantalla de revisión con un cuadro de texto independiente por cada frase detectada ("Subtítulo 1 de 12, del segundo 3 al 5"), más el título
editable arriba, y los botones Continuar y Cancelar.
