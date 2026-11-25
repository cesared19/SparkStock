# SparkStock

Inventario de dispositivos de la suite de SparkyTC (https://github.com/cesared19/SparkyTC): visores VR que se prestan a testers y equipo de IT asignado a personas. Se despliega junto a SparkyTC y usa sus cuentas.

**Idioma:** comunícate con el usuario en español. El código, la interfaz, el README y los mensajes de commit van en inglés.

**Repositorio:** trabaja y abre PRs solo en https://github.com/cesared19/SparkStock.

## Arquitectura

- **Backend:** Spring Boot 3 + Kotlin (JDK 21) en `service/`, paquete `com.sparkstock.service`, configuración `sparkstock.*` (`SparkStockProperties`). Migraciones con Flyway en `service/src/main/resources/db/migration` (`V<n>__descripcion.sql`; nunca se edita una ya publicada).
- **Cliente:** Remix + React + Tailwind en `client/`. Todas las llamadas pasan por el servidor Remix (`requireUser` / `requireAdmin` en `utility/API.server.ts`); la cookie es `__sparkstock` (no `__session`, que es la de SparkyTC: las cookies se comparten entre puertos del mismo host). Mismos colores y modo oscuro que SparkyTC (`tailwind.css`); el tema sigue al sistema.
- **Login con SparkyTC:** no hay contraseñas propias. `AuthService.login` reenvía usuario y contraseña a `/api/v1/auth/login` de SparkyTC y el token de SparkyTC es la sesión de SparkStock. `authenticate` vuelve a validar con `/api/v1/auth/me` cada `session-cache-seconds` (300) y guarda una copia de la cuenta en `users` (el ID es el de SparkyTC). `GET /api/v1/users` (admins) copia todas las cuentas desde `/api/v1/admin/users` con la sesión del admin. SparkyTC manda las fechas en milisegundos.
- **Modelo:** `device_types` (uso `POOL` = se presta, `ASSIGNED` = lo asigna un admin; campos extra en JSONB con `FieldDefinition`; `coverage_field` = el campo de versión para la matriz de cobertura; `next_number` para las etiquetas `<PREFIJO>-0001`), `devices` (campos extra en JSONB, normalizados por `FieldValues`; `holder_id`, `due_at`; `@Version`) y `device_events` (historial, no se edita). Las transiciones están en `DeviceService` y bloquean la fila (`findByIdForUpdate`).
- **API de integración:** `/api/integration/v1` con `STOCK_INTEGRATION_TOKEN` (`IntegrationTokenInterceptor`), solo lectura, para que SparkyTC liste los dispositivos de un tester (`IntegrationDeviceDTO`, `ref` = asset tag).
- **Deploy:** `docker-compose.yml` con servicios `stock-*` (para no chocar con los de SparkyTC en la red compartida). `stock-backend` se une a la red de SparkyTC (`SPARKYTC_NETWORK`) y llama a `http://backend-service:8080`. Respaldo antes de cada `up` y diario con los scripts de `deploy/backup` (copiados de SparkyTC).
- **Importación (desde 0.2.0):** `ImportService` (`POST /api/v1/import/devices`, admins) recibe filas ya mapeadas por el cliente (`routes/admin.import.tsx`, `utility/CSV.ts`; columnas adivinadas por nombre, p. ej. el CSV de la database de Confluence "Testing Devices": ID, Device Name, Model, Brand, Serial Number, Type, Firmware, Attachments, Project Tag). Todo o nada; la vista previa (`dryRun`) corre la misma importación y hace rollback. Empata por `external_id` y luego por serie; los tipos desconocidos se crean como POOL con campos `version` (cobertura) y `accessories`; "N/A" cuenta como vacío. Nunca cambia estado ni quién tiene el equipo.
- **Proyecto:** `devices.project_code` (código corto del proyecto de SparkyTC, p. ej. VR) y `devices.external_id` (V2).
- **Recursos:** el backend corre con `-Xmx256m -XX:+UseSerialGC` (`STOCK_JAVA_OPTIONS`): ~300 MB de RAM, sin CPU en reposo.
- **Recordatorios:** `ReminderService` (cron `sparkstock.reminders.cron`, 9:00) manda a la campana de SparkyTC (`SparkyTCClient.notify`, `POST /api/system/v1/notifications` con `STOCK_INTEGRATION_TOKEN`) préstamos por vencer (una vez por préstamo), atrasados (uno por día) y garantías a los admins (uno por dispositivo y fecha); el `sourceRef` evita duplicados. `POST /api/v1/reminders/send` (admins) los manda ya. Desactivados en las pruebas (`application-test.properties`).
- **Etiquetas QR:** el PDF se genera en el navegador (`utility/Labels.ts`, jsPDF + qrcode). El QR lleva `PUBLIC_URL/t/<tag>` (`routes/t.$tag.tsx`), que se abre con la cámara del teléfono, sin HTTPS.

## Cómo trabajar

- Antes de hacer push: `cd service && ./gradlew test` (Testcontainers + un SparkyTC falso por HTTP en `FakeSparkyTC.kt`) y `cd client && npx tsc && npm run lint && npm run build`. Si Docker no corre, inícialo con `dockerd`.
- Formato del cliente: Prettier con `--use-tabs --single-quote --no-semi`.
- Revisa los cambios de interfaz a 390 px, 820 px y escritorio, en claro y oscuro.
- Igual que en SparkyTC: no crear nada en lote en GitHub (issues, etiquetas, comentarios); el usuario decide cuándo abrir y fusionar PRs.

## Pendiente (por fases)

1. SparkyTC: dispositivo por resultado (modo Off/Opcional/Obligatorio por proyecto) usando `/api/integration/v1/devices`.
2. Avisos (atrasos, garantías) a la campana de SparkyTC.
3. Confluence Cloud: una página por dispositivo (la importación por CSV ya existe).
4. Matriz de cobertura por proyecto y build.
