# JorCam para Android — cómo compilarlo con GitHub (paso a paso, primera vez)

Lo que vas a hacer, en palabras simples: subes este proyecto a GitHub; un robot de GitHub (Actions) lo convierte en un
archivo `.apk` (la app instalable); tú lo bajas y lo instalas en tu Moto E40. No necesitas instalar Android Studio.

## 0. Antes de empezar
- Esta compilación es el **modo bolsillo** (grabar y procesar en el teléfono). No incluye todavía los subtítulos ni el título
  (Pro), ni la conexión con la PC. Ver ARQUITECTURA_ANDROID.md y ROADMAP.md.
- **Nadie ha compilado este código todavía** (yo no tengo el SDK de Android). Es normal que la primera compilación falle:
  si pasa, sigue el paso 7 y mándame el texto del error.

## 1. Descomprimir (usa solo `jorcam-android.zip`; ignora tus otros zips)
`Expand-Archive -Path "$env:USERPROFILE\Downloads\jorcam-android.zip" -DestinationPath "$env:USERPROFILE\Documents\Proyecto JorCam" -Force`
Queda la carpeta `Documents\Proyecto JorCam\jorcam-android`.

## 2. Poner tu clave pública (la de las licencias)
`Copy-Item "$env:USERPROFILE\Documents\Proyecto JorCam\privado\clave_publica.txt" "$env:USERPROFILE\Documents\Proyecto JorCam\jorcam-android\android\app\src\main\res\raw\clave_publica.txt" -Force`
(Es la clave PÚBLICA: no es secreta. Si no haces este paso, la app abre pero solo en versión gratuita.)

## 3. Instalar Git (el programa que sube archivos a GitHub)
`winget install --id Git.Git -e --accept-source-agreements --accept-package-agreements`
Cierra PowerShell, ábrelo de nuevo y comprueba con `git --version`.

## 4. Decirle a Git quién eres (una sola vez)
`git config --global user.name "Jorman Delgado"`
`git config --global user.email "TU_CORREO_DE_GITHUB"`

## 5. Crear el repositorio en GitHub (en la página)
github.com → botón "+" → New repository → nombre `jorcam-android` → marca **Private** → NO marques "Add a README" →
Create repository. Anota tu usuario de GitHub.

## 6. Subir el proyecto
`cd "$env:USERPROFILE\Documents\Proyecto JorCam\jorcam-android"`
`git init -b main`
`git add .`
`git commit -m "Primera version de JorCam para Android"`
`git remote add origin https://github.com/TU_USUARIO/jorcam-android.git`
`git push -u origin main`
La primera vez se abre una ventana del navegador para iniciar sesión en GitHub: acéptala.

## 7. Ver la compilación
En el repositorio: pestaña **Actions** → "Compilar app de Android" → la ejecución más reciente. Tarda varios minutos.
- **Check verde:** abre la ejecución → sección **Artifacts** → `JorCam-android-apk` → se baja un zip con `app-release.apk`.
- **X roja:** abre la ejecución → clic en el trabajo `apk` → en el paso "Compilar el APK" busca las líneas que
  empiezan con `e:` o `error:`. Con lector de pantalla es más fácil: en la página de la ejecución, menú "..." →
  "Download log archive", descomprime y abre el texto; copia las últimas 60 líneas y mándamelas.

## 8. Instalar en el teléfono
Pasa el `.apk` al Moto E40 (cable USB, o envíatelo por Telegram/WhatsApp Web). Ábrelo desde Archivos → permite
"instalar apps de esta fuente" cuando lo pida → Instalar → Abrir → acepta los permisos de cámara y micrófono.
**Aviso:** sin llave propia, cada compilación se firma con una llave distinta y Android no deja actualizar encima:
desinstala la versión anterior antes de instalar la nueva. Para evitarlo, ver el paso 11.

## 9. Primera prueba en el teléfono
1. Abre la app: debe verse la cámara y decir "Cámara trasera activa".
2. "Cambiar cámara": debe decir "Cámara frontal activa" y luego "Cámara trasera activa".
3. Grabar 20 segundos con pausas largas; Detener y guardar; espera los avisos de progreso.
4. Comprueba que el video quedó en Películas → JorCam y que los silencios se recortaron.
5. Prueba el micrófono (incluido uno externo), Descartar, Reproducir último video y Ver los videos guardados.
6. Con una clave real: Licencia → pegar → Activar. Luego prueba Cargar video (Pro, máximo 20 minutos).
7. Anota los cuadros por segundo que notas y si el teléfono se calienta.

## 10. Cambiar el código después
Cuando yo te dé archivos nuevos, cópialos encima y ejecuta: `git add .` , `git commit -m "Descripcion"` , `git push`.

## 11. (Opcional) Firmar con tu propia llave, para poder actualizar sin desinstalar
`winget install --id Microsoft.OpenJDK.17 -e` (cierra y abre PowerShell) y luego
`keytool -genkeypair -v -keystore jorcam.jks -alias jorcam -keyalg RSA -keysize 2048 -validity 10000`
(te pregunta contraseñas y datos; **guarda el archivo y las contraseñas en tu USB y en `privado`**).
Convierte la llave a texto: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("jorcam.jks"))`
En GitHub → Settings → Secrets and variables → Actions → New repository secret, crea:
`KEYSTORE_BASE64` (ese texto), `KEYSTORE_PASSWORD`, `KEY_ALIAS` (jorcam) y `KEY_PASSWORD`. (Opcional: `CLAVE_PUBLICA_HEX`.)

## 12. Publicar el APK en Releases
`git tag android-v0.1.0` y `git push origin android-v0.1.0`. El robot lo publica en Releases (si el repositorio es privado,
el Release también lo es; para descargas públicas usa tu repositorio público de descargas).

## 13. Actualizar a la compilación B1 (si ya hiciste los pasos 1 a 8)
1. Descomprime `jorcam-android-B1.zip` encima de tu carpeta (acepta reemplazar). Ya trae tu clave pública y el workflow sin el paso que fallaba.
2. `cd "$env:USERPROFILE\Documents\Proyecto JorCam\jorcam-android"`
3. `git add .` , `git commit -m "Compilacion B1: titulo y nombre del archivo"` , `git push`
4. Actions → espera el check verde → baja el APK. **Desinstala antes la app vieja** (a menos que ya hayas hecho el paso 11).
5. Prueba: activa tu licencia, escribe un título, graba 15 segundos hablando de frente con pausas, y revisa que el archivo se llame como el título,
   que el título esté arriba de la cabeza y no la tape, y que se vea durante 5 segundos. Pídele a una persona que vea el resultado, o mándamelo a revisar.

## 14. Actualizar a la compilación B2 (subtítulos)
1. Descomprime `jorcam-android-B2-cambios.zip` encima de tu carpeta (acepta reemplazar). Trae tu `build.gradle.kts` con los cambios para el motor de voz
   y el workflow con el paso nuevo que descarga whisper.cpp. Si habías cambiado algo más en el workflow, revísalo después de copiar.
2. `cd "$env:USERPROFILE\Documents\Proyecto JorCam\jorcam-android"`
3. `git add .` , `git commit -m "B2: subtitulos con Whisper"` , `git push`
4. Actions: esta compilación tarda más (compila el motor de voz). Si falla, copia las líneas con `error` (las de CMake, ninja o whisper son las importantes) y mándamelas.
5. Instala el APK (tu llave firmada permite actualizar encima, si hiciste el paso 11).
6. Prueba con Wi-Fi: Licencia Pro activa → graba 30 segundos hablando de frente, con pausas → Detener y guardar. La primera vez descarga el modelo (142 MB, avisa cada 10 por ciento).
   Luego transcribe, te muestra la pantalla de revisión (un cuadro por frase) y al continuar escribe el texto y guarda.
7. Anota y mándame: cuánto tardó la transcripción (la app lo anuncia), si los subtítulos coinciden con lo que dijiste, si quedan debajo de la barbilla sin tapar la cara,
   y si la pantalla de revisión se lee bien con tu lector.

## 15. Actualizar a la compilación B2.1
1. Descomprime `jorcam-android-B2.1-cambios.zip` encima de tu carpeta (acepta reemplazar).
2. `cd "$env:USERPROFILE\Documents\Proyecto JorCam\jorcam-android"`
3. `git add .` , `git commit -m "B2.1: revision de subtitulos, emojis y selector de modo"` , `git push`
4. Cuando termine el check verde, instala el APK.
5. Pruebas: (a) al abrir debe salir la pantalla para elegir el modo, y "Grabación directa" abre la cámara; (b) graba con título y subtítulos: en la pantalla de revisión los dos botones deben estar arriba, el botón Atrás debe funcionar,
   y cada frase debe tener su botón "Reproducir este tramo" que suene solo esa parte; (c) prueba un título con emojis y la casilla de emojis automáticos en Ajustes generales;
   (d) mira si los emojis se ven bien sobre el video, con contorno y sin deformarse.

## 16. Compilación C: conectar con la computadora (necesita JorCam PC 1.0.2)
**En la PC**
1. Copia `jorcam.py` (versión 1.0.2) a tu carpeta `construccion` y pruébalo: `python "$env:USERPROFILE\Documents\Proyecto JorCam\construccion\jorcam.py"`.
2. Abre Ajustes → "Cómo usar el teléfono" → "Con la app JorCam para Android". **Se aplica al reiniciar JorCam**: ciérralo y ábrelo de nuevo.
   (Con esa opción, la PC ya no abre Chrome en el teléfono.) Si todo va bien, reconstruye el instalador (`construir_jorcam.ps1`): sale `JorCam-Setup-1.0.2.exe`.
**Por cable USB**
3. Conecta el teléfono con depuración USB ANTES de abrir JorCam en la PC. La PC dice: "Modo app de Android. Abre JorCam en el teléfono...".
4. En el teléfono: actualiza la app (compilación C) → "Conectar con la computadora" → "Conectar por cable USB". La PC anuncia el teléfono y la cámara.
**Por Wi-Fi**
5. En el panel de la PC abre "Conexión con la app JorCam para Android" → "Activar conexión por Wi-Fi". La PC anuncia y escribe la dirección y el código.
   Windows puede preguntar por el firewall: permite el acceso en **redes privadas**.
6. En el teléfono (en la misma red Wi-Fi): "Conectar con la computadora", escribe la dirección y el código, "Conectar por Wi-Fi".
   Si no conecta: revisa que no sea una red de invitados (muchas aíslan los equipos), prueba otra de las direcciones que muestra el panel, y que el firewall no lo bloquee.
**Prueba**
7. Desde la PC: Grabar, Pausar, Reanudar, Detener y guardar; Cambiar cámara; Descartar. Habla 20 segundos con pausas.
   Comprueba que el audio del video final sea el de la PC (V8), que no haya desfase y que el video salga vertical de 1080x1920.
8. Anota: cuánto tarda el envío del video al detener (por USB y por Wi-Fi), y cualquier aviso extraño de la PC.

## 17. Compilación D: vista previa en la PC (con JorCam PC 1.0.3)
1. Descomprime `jorcam-android-D-cambios.zip` encima de tu carpeta (3 archivos de código y las guías; no toca el Gradle).
2. `cd "$env:USERPROFILE\Documents\Proyecto JorCam\jorcam-android"` y luego `git add .` , `git commit -m "D: vista previa para la PC"` , `git push`. Instala el APK nuevo.
3. En la PC, instala JorCam 1.0.3 (ver GUIA_LANZAMIENTO.md, sección 10).
4. Pruebas con el teléfono por USB: la imagen debe aparecer en la ventana de la PC en uno o dos segundos.
   - Si sale de lado o al revés, pulsa "Girar la vista previa" hasta que quede derecha (se guarda) y **dime cuántas veces la giraste**: así corrijo el valor por defecto.
   - Con la cámara frontal debe verse en espejo.
   - Graba un video y compara con uno de antes: los cuadros por segundo y la calidad no deben empeorar. Si empeoran, desactiva la vista previa (Alt+9) y avísame.

## 18. Compilación E: calidad máxima y vista previa vertical (con JorCam PC 1.0.4)
1. Descomprime `jorcam-android-E-cambios.zip` encima de tu carpeta (3 archivos de código y las guías; no toca el Gradle).
2. `cd "$env:USERPROFILE\Documents\Proyecto JorCam\jorcam-android"` y luego `git add .` , `git commit -m "E: calidad maxima y vista previa vertical"` , `git push`. Instala el APK nuevo.
3. Pruebas con la PC por USB: (a) al grabar, la app debe decir "Grabando en HEVC" (o "en H.264" si el teléfono no tiene HEVC; dime cuál); (b) la imagen del teléfono en la PC debe verse **derecha y bien proporcionada** en la columna vertical, sin tocar nada;
   (c) graba **1 minuto hablando de frente** y anota cuánto pesa el video final de la PC en cada calidad (Liviana, Equilibrada y Alta), y si notas diferencia de nitidez;
   (d) comprueba que los cuadros por segundo siguen fluidos y que el teléfono no se calienta más de la cuenta.
