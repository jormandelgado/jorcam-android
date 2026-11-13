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
