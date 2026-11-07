# Despliegue FaceLit-2026 en Hostinger

Esta configuración publica `https://facelit.tech/` a partir de los repositorios
`FaceLit-2026/frontend`, `FaceLit-2026/backend`, `FaceLit-2026/db` y
`FaceLit-2026/microservice`. La configuración de infraestructura se versiona en
`backend/deploy/hostinger`.

El VPS recibe un paquete por SSH. La procedencia se registra mediante URL,
rama y SHA de cada repositorio, etiquetas de imagen y un manifiesto. El VPS
no requiere credenciales de acceso a GitHub ni un remoto Git configurado.

## Disposición del espacio local

```text
Proyecto-Final-v2/
  frontend/
  backend/deploy/hostinger/
  db/
  microservice/
  docs/
  output/                         # paquetes generados; fuera de los repositorios
```

## Publicar una versión

1. Revisar los cambios en ramas de trabajo e integrarlos en `main` de los
   repositorios nuevos. Actualizar los cuatro `main` locales. Los repositorios
   originales de la organización anterior conservan su versión.
2. Desde `Proyecto-Final-v2`, ejecutar:

   ```sh
   python backend/deploy/hostinger/package.py --fetch
   ```

   El empaquetador exige que `main` local coincida con `origin/main`. Usa
   `git archive` de los commits confirmados, rechaza archivos privados y genera
   el archivo comprimido, su SHA-256, `release.env` con revisiones públicas y
   `release-manifest.json`. Un paquete `--preview` sirve para revisión y
   construcción; el controlador impide activarlo.
3. Transferir el paquete por SSH/SCP, comprobar su SHA-256 y extraerlo en un
   directorio nuevo `/opt/facelit-v2/releases/<releaseId>`. Comprobar antes que
   los nombres del archivo comprimido no escapen de ese directorio.
4. En el VPS, ejecutar como root:

   ```sh
   python3 /opt/facelit-v2/releases/<releaseId>/backend/deploy/hostinger/deploy.py prepare
   ```

   Se reutilizan las credenciales activas en `/etc/facelit/production.env`, con
   permisos `0600` y directorio `0700`. En la primera migración se obtiene esa
   configuración de los contenedores activos. El paquete no contiene ese
   archivo. Se valida Compose, el estado previo y un respaldo PostgreSQL;
   luego se construyen imágenes identificadas por SHA y se valida Liquibase.
5. Revisar las comprobaciones antes de activar:

   ```sh
   python3 /opt/facelit-v2/releases/<releaseId>/backend/deploy/hostinger/deploy.py activate
   ```

   La activación requiere fuentes `main`. Detiene consumidores, genera otro
   respaldo, conserva los volúmenes existentes, aplica las migraciones
   revisadas e inicia PostgreSQL, FastAPI, Spring Boot y Caddy. Las imágenes
   anteriores quedan disponibles para recuperar el servicio si falla.
6. Comprobar el dominio, los archivos JavaScript, los servicios internos, la
   consulta autenticada de un perfil y la autenticación SMTP. La prueba de
   correo no envía mensajes. Se usa una sesión coordinadora ya existente para
   la consulta autenticada; si no hay ninguna, iniciar sesión para habilitarla.
7. Conservar el manifiesto y el resultado sin secretos. El encabezado público
   `X-FaceLit-Release` identifica la versión activa. Al terminar se actualiza
   `/opt/facelit-v2/current` al directorio de la versión verificada.

## Datos, modelos y recuperación

Los volúmenes y la red se declaran como externos con sus nombres existentes
`facelit-production_*`. Es una migración del VPS actual: si falta un volumen,
se aborta en vez de crear una base vacía. Solo Caddy publica 80/443.

El modelo facial se conserva en `facelit-production_facial_models`, montado
en `/home/facelit/.insightface`, que corresponde al usuario del contenedor.

Los respaldos y registros completos quedan privados bajo
`/var/lib/facelit-deploy/<releaseId>`. La recuperación de contenedores utiliza
las imágenes y configuración anteriores. Volver a esas imágenes no revierte
el esquema; una restauración de PostgreSQL requiere revisar compatibilidad y
autorizar su impacto sobre los datos. El controlador nunca elimina volúmenes
ni reinicializa registros del sistema.

```sh
python3 /opt/facelit-v2/current/backend/deploy/hostinger/deploy.py verify
python3 /opt/facelit-v2/current/backend/deploy/hostinger/deploy.py backup
```

Las credenciales reales permanecen fuera de Git y de los paquetes. El archivo
`.env.example` describe únicamente los nombres de configuración requeridos.
