# Puente LSA

Aplicación Android accesible en Jetpack Compose para traducción en tiempo real de Lengua de Señas Argentina (LSA) con voz sintetizada, IA generativa (Firebase AI Logic), sincronización en la nube (Firestore), modo offline y compatibilidad con wearables.

## Compilación automática y descarga de la APK en GitHub

El repositorio incluye una rutina automática de GitHub Actions que compila la aplicación y deja lista la APK para descargar:

1. **Al publicar o hacer `push` a GitHub:** la compilación inicia automáticamente en la pestaña **Actions** del repositorio (`Compilar APK de Puente LSA`).
2. **Compilación manual:** dentro de **Actions** → **Compilar APK de Puente LSA** → **Run workflow**, podés iniciar la compilación manualmente cuando quieras.
3. **Descargar la APK:** al finalizar la ejecución, entrá al flujo completado y descargá el archivo desde la sección **Artifacts** (`Puente-LSA-APK-debug`).
4. **Publicación por tag (opcional):** si creás un tag con formato `v1.0.0`, también se crea un **GitHub Release** con la APK adjunta directamente.

### Compilación `release` firmada (opcional)

Por defecto se compila la APK `debug` usando `debug.keystore.base64` (con la misma firma ya registrada en Firebase para Google Sign-In). Si querés compilar una versión `release` desde **Run workflow**:

- Configurá estos **Repository Secrets** en **Settings → Secrets and variables → Actions**:
  - `RELEASE_KEYSTORE_BASE64`: tu keystore `.jks` codificado en Base64 (con alias `upload`).
  - `STORE_PASSWORD`: contraseña del keystore.
  - `KEY_PASSWORD`: contraseña de la clave `upload`.
