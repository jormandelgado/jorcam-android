# JorCam 1.0.2 — Guía de empaquetado, lanzamiento y venta

## 1. Compatibilidad
- **Windows 11 de 64 bits:** probado en tu laptop (Dell Latitude).
- **Windows 10 de 64 bits:** debería funcionar, pero **no se ha probado**. Necesita Microsoft Edge o Chrome
  (Edge viene con Windows 10 actualizado) y, para instalar FFmpeg solo, `winget`; en Windows 10 puede faltar
  (se instala desde la Microsoft Store como "Instalador de aplicación"). No se admite Windows de 32 bits.
- **Android (app futura):** Android 8 en adelante para conectar con la PC; el "modo bolsillo" se recomienda
  desde Android 10 y con 4 GB de RAM o más (ver PLAN_ANDROID.md).

## 2. Qué hace cada comando de empaquetado
- `python -m pip install pyinstaller` (ya hecho): instala PyInstaller, que mete Python, OpenCV y numpy
  dentro de una carpeta con `JorCam.exe`. Por eso un cliente NO necesita instalar Python.
- `construir_jorcam.ps1` hace dos pasos:
  1. `python -m PyInstaller --noconfirm --clean --onedir --noconsole --name JorCam --icon jorcam.ico ...`
     crea `dist\JorCam\JorCam.exe` (sin ventana negra, con tu ícono y con `clave_publica.txt` adentro).
  2. `ISCC.exe jorcam.iss` (Inno Setup) crea `Output\JorCam-Setup-1.0.2.exe`, el instalador con acceso directo.
- Al terminar de instalar, `preparar_componentes.ps1` baja FFmpeg, ADB y el modelo de voz de sus fuentes oficiales.
  Si se corta internet, se vuelve a ejecutar y continúa donde quedó.
- Comando de construcción (desde la carpeta `construccion`):
  `powershell -ExecutionPolicy Bypass -File construir_jorcam.ps1`

## 3. Proceso completo de lanzamiento (en orden)
1. Probar en tu laptop: abrir `jorcam.py`, comprobar ventana, micrófono, cámaras, cerrar, gratis y Pro.
2. Construir el instalador y probarlo en tu laptop.
3. **Probarlo en una PC limpia** (otra computadora o Windows Sandbox). Es la prueba que más importa.
4. Subir el instalador a VirusTotal para ver si hay falsos positivos y anotar su huella SHA-256:
   `Get-FileHash "Output\JorCam-Setup-1.0.2.exe" -Algorithm SHA256`
5. Crear los lotes de claves (carpeta `privado`): `python generar_licencias.py generar M 100` y `... A 100`.
6. Crear los dos productos en Payhip y subir las claves (ver sección 5).
7. Publicar el instalador en una versión (Release) de un repositorio **público** de GitHub.
8. En tu página: página de JorCam con descarga, botones de compra, términos, privacidad, reembolsos, soporte.
9. Compra de prueba real con tu propio correo; activar la clave en una PC; comprobar que se activa.
10. Anunciar.

## 4. Cómo se conecta todo
```
 jormandelgado.com (GitHub Pages)
   ├─ botón "Descargar JorCam" ──► GitHub Release (repositorio público): JorCam-Setup-1.0.2.exe  (gratis, sin clave)
   └─ botón "Comprar pase" ─────► Payhip (checkout): paga el cliente ──► Payhip le manda la CLAVE por correo
                                                                          │
 El cliente instala JorCam ─► sección Licencia ─► pega la clave ─► JorCam la valida SIN internet (firma Ed25519)
```
- GitHub (repositorio **privado**): solo tu código fuente. El público: página web y el instalador.
- Payhip y tu página NO se enlazan técnicamente: tu página solo tiene botones que llevan al producto de Payhip.
- GitHub Pages, según sus términos, no sirve para cobrar: el cobro ocurre en Payhip, no en tu página.

## 5. Flujo de compra y activación (respuestas)
- **¿Descarga primero y compra después?** Sí: el instalador es gratis. Sin clave funciona la versión gratuita;
  con clave se abren las funciones Pro. El cliente puede probar antes de pagar.
- **¿Subo el instalador a Payhip?** No. Va en tu página / GitHub. A Payhip solo subes un archivo pequeño de
  instrucciones (Payhip exige un archivo por producto) y el archivo de claves.
- **Configuración en Payhip:** dos productos tipo Digital Product: "JorCam Pase mensual" ($8) y
  "JorCam Pase anual" ($29). En cada uno: Advanced Options → Generate license keys → claves precargadas →
  subir `claves_mensual_*.txt` o `claves_anual_*.txt` (una por línea, máximo 100 caracteres: las tuyas miden 98).
  Limita las ventas al número de claves subidas: si se acaban, Payhip vende pero no entrega clave.
- **¿Cómo recibe el cliente la clave?** Payhip la envía en el correo de compra y la muestra en la página de descarga.
- **¿Cómo se valida?** El cliente la pega en Licencia → el programa comprueba la firma con la clave pública que
  lleva adentro. No hay internet ni servidor. El plan (M/A) viene dentro de la clave; el conteo de 30 o 365 días
  empieza al activarla y se guarda en un archivo y en el registro de Windows.
- **Si se acaban las claves:** genera otro lote y súbelo a Payhip.
- **Las 3 claves de prueba** que generaste NO las subas a Payhip.

## 6. Límites conocidos (decídelos antes de vender)
- La clave no está atada a una computadora: puede usarse en varias, y en otra PC el conteo empieza de nuevo.
  Mejora futura: pedir a Payhip la "integración alternativa" para verificar en línea (contact@payhip.com).
- Un reembolso no desactiva la clave (vale hasta que venza). Dilo en tus términos.
- Es un pase de 30/365 días con pago único, no una suscripción con renovación automática.
- Sin firma de código, Windows muestra una advertencia de SmartScreen: explícalo en la página de descarga
  ("Más información" → "Ejecutar de todos modos") y publica la huella SHA-256 del instalador.
- Probado en mi entorno: licencias, funciones gratis/Pro, selector de micrófono y cámara, cierre, descartar,
  guardado con título y subtítulos. NO probado: el `.exe`, el instalador, los scripts de PowerShell,
  la ventana de aplicación en Windows, Windows 10 y la PC limpia.

## 7. Páginas que debe tener tu sitio
Descarga y compra · Términos de uso y licencia (duración de los pases, sin reembolso tras activar, claves
personales) · Política de privacidad ("los videos se procesan en tu PC y no se envían a ningún servidor") ·
Reembolsos · Soporte y contacto · Avisos de software de terceros (FFmpeg, OpenCV, Python, Whisper).
Revisa con un abogado: licencia de FFmpeg (la versión que usas incluye componentes GPL), ADB y marca en el SAPI.

## 8. Respaldo (hazlo ya)
- Carpeta completa `Documents\Proyecto JorCam` (versiones, construcción, recursos, privado).
- `C:\Users\Usuario\CamaraVertical\ajustes.json` y `registro.txt` (el modelo de voz se vuelve a bajar).
- El instalador terminado y su huella.
- **Copia aparte, en USB, de la carpeta `privado` (clave privada).** Nunca la subas ni se la des a una IA.
- Paquete sin `privado` para pasarle el contexto a otra plataforma:
  `Get-ChildItem "$env:USERPROFILE\Documents\Proyecto JorCam" -Exclude privado | Compress-Archive -DestinationPath "$env:USERPROFILE\Documents\JorCam-sin-privado.zip" -Force`
- Códigos de recuperación de GitHub, Payhip y tu dominio.

## 9. Novedades de la 1.0.2 (PC)
- **App de Android:** nuevo ajuste "Cómo usar el teléfono" (Chrome, como hasta ahora, o la app JorCam). Con la app, la PC no abre Chrome.
- **Conexión por Wi-Fi:** botón en el panel que abre un puerto (8766) con un código de 6 números nuevo cada vez; solo acepta 4 rutas y tras 15 intentos fallidos se apaga.
  Solo se abre si lo activas; la primera vez Windows puede preguntar por el firewall.
- **Recorte de silencios gratis:** la agresividad y el aire antes y después ya no son Pro.
- **Videos con marca de rotación** (los de la app de Android): ahora se miden bien.
- **Al descartar**, la app de Android no manda el video.
- Probado aquí con un teléfono simulado (eventos, órdenes, subida del MP4 y guardado del video final); NO probado con la app real, el `.exe` ni el firewall de Windows.
