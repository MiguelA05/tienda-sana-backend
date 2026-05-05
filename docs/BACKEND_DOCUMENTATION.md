# Documentación técnica — Tienda Sana (Backend)

Este documento describe **la arquitectura, estructura del código, componentes**, endpoints expuestos y **reglas de negocio** observables en el backend.

> Seguridad: esta documentación **no incluye secretos**, valores de tokens, claves, ni URLs privadas. Usa variables de entorno y un gestor de secretos para producción.

## 1) Stack y dependencias relevantes

- **Lenguaje/Runtime**: Java 17
- **Framework**: Spring Boot 3.3.x
- **Persistencia**: MongoDB (Spring Data MongoDB + repositorios propios)
- **Seguridad**: Spring Security + JWT (filtro `TokenFilter`)
- **API Docs**: SpringDoc OpenAPI (Swagger)
- **Observabilidad**: Actuator + Prometheus
- **Pagos**: Mercado Pago SDK
- **Correo**:
  - `simple-java-mail` (SMTP)
  - Config adicional para **Resend** vía `RESEND_*` (propiedades presentes en `application.properties`)
- **Rate limiting**: Bucket4j + Ehcache/JCache
- **Cloudinary**: firma server-side para uploads
- **IA**: integración DeepSeek (config en properties)

## 2) Estructura del proyecto (capas)

`src/main/java/co/uniquindio/tiendasana/`:
- `controllers/`: controladores REST (públicos, cliente, admin, internos).
- `services/`:
  - `interfaces/`: contratos de servicios de dominio.
  - `implementations/`: implementaciones “core” (cuentas, ventas, reservas, catálogo, email, etc.).
  - `admin/`: servicios específicos del panel admin (catálogo admin, lotes, ledger de inventario, analítica).
  - `cloudinary/`, `mongo/` (mapeadores/servicios auxiliares).
- `repos/`:
  - `mongo/`: repositorios Spring Data (`ProductoDocumentRepository`, etc.).
  - repos “legacy” (clases repo con lógica propia y filtros in-memory).
- `model/`:
  - `documents/`: entidades del dominio (Cuenta, Reserva, VentaProducto, CarritoCompras, etc.).
  - `mongo/`: documentos/catálogos para admin e inventario (p.ej. lotes, ledger).
  - `enums/` y `vo/`: enums y value objects.
- `dto/`: DTOs de entrada y salida (público/cliente/admin).
- `config/`: seguridad, CORS, JWT utils, rate limiting, etc.
- `exceptions/`: excepciones globales y específicas.

`src/main/resources/`:
- `application.properties`: configuración y variables de entorno.

## 3) Configuración (application.properties)

Archivo: `src/main/resources/application.properties`

Puntos clave:
- `server.port=${PORT:8080}`: compatible con Render (inyecta `PORT`).
- `spring.config.import=optional:file:.env[.properties]`: permite `.env` local.
- Mongo: `spring.data.mongodb.uri=${MONGODB_URI:mongodb://localhost:27017/tienda_sana}`
- CORS: `cors.allowed-origins=${CORS_ALLOWED_ORIGINS:http://localhost:4200}`
- Reservas: `reservas.pending-hold-minutes=15`
- Inventario/ledger: `tiendasana.inventory.migration.run-on-startup=...`
- Mercado Pago:
  - `mercadopago.access-token`
  - `mercadopago.frontend-base-url`
  - `mercadopago.webhook-base-url`
- IA DeepSeek:
  - `deepseek.*` (enabled, api key, base url, model, timeout)
- Resend:
  - `resend.api.key`
  - `resend.from`

## 4) Seguridad: JWT + roles + autorización

### 4.1 SecurityFilterChain

Archivo: `config/SecurityConfig.java`
- `permitAll`:
  - `OPTIONS /**` (preflight)
  - `/actuator/health`, `/actuator/info`
  - `/api/public/**`
  - auth: `/api/auth/login`, `/api/auth/create-account`, etc.
  - Swagger: `/v3/api-docs/**`, `/swagger-ui/**`
- Reglas:
  - `/api/account/**` → autenticado
  - `/api/cliente/**` → autenticado (rol cliente validado por filtro)
  - `/api/admin/**` → `ROLE_ADMIN`
  - `anyRequest()` → autenticado

### 4.2 TokenFilter

Archivo: `config/TokenFilter.java`
- Lee `Authorization: Bearer <jwt>`.
- Valida/parsea JWT con `JWTUtils`.
- Si la ruta “requiere autenticación”, aplica:
  - `401` si no hay token
  - `403` si rol no permite ruta
  - pobla `SecurityContextHolder` (email como `subject`, rol como `ROLE_*`)

**Implicación práctica**: los controladores `CuentaController` y `ClienteController` extraen el email autenticado desde `SecurityContextHolder` y lo comparan con el email objetivo, evitando que un usuario manipule recursos de otro.

### 4.3 JWTUtils (detalle)

Archivo: `config/JWTUtils.java`
- Expiración: **8 horas**
- Clave/secret: debe provenir de **variable de entorno** en producción (no versionar secretos).

## 5) Contrato de respuesta (MessageDTO)

Muchos endpoints devuelven `MessageDTO<T>`:
- `error` (boolean)
- `reply` (payload)

Esto facilita un patrón uniforme de consumo desde el frontend.

## 6) Endpoints principales (por grupo)

> Nota: los paths exactos pueden variar; la lista aquí se basa en controladores observados en el código.

### 6.1 Públicos — `/api/public` (`PublicoController`)

- **Productos**:
  - `GET /productos/get-all/{pagina}`
  - `GET /productos/get-info/{id}`
  - `POST /productos/filter-products`
  - `GET /productos/get-types`
- **Mesas**:
  - `GET /mesas/get-all/{pagina}`
  - `GET /mesas/get-info/{id}`
  - `GET /mesas/get-reserved-slots/{id}`
  - `POST /mesas/filter-tables`
  - `GET /mesas/get-locality`
- **IA**:
  - `POST /ai/recommendations`
- **Webhooks Mercado Pago**:
  - `POST /venta/receive-notification`
  - `POST /reserva/receive-notification`

### 6.2 Autenticación — `/api/auth` (`AutenticacionController`)

- `POST /login`
- `POST /create-account`
- `PUT /send-recover/{email}`
- `PUT /change-password`
- `PUT /validate-account`
- `PUT /resend-validation/{email}`

### 6.3 Cuenta autenticada — `/api/account` (`CuentaController`)

- `PUT /update-account` (solo el mismo usuario)
- `DELETE /delete/{email}` (solo el mismo usuario)
- `GET /get/{email}` (mismo usuario o admin)

### 6.4 Cliente — `/api/cliente` (`ClienteController`)

Incluye:
- Carrito: add/edit/delete/clear, listar items.
- Ventas: crear orden, pagar, estado, cancelar, reembolso, historial.
- Reservas: crear, pagar, cancelar, info, historial, gestor de reservas.

### 6.5 Admin — `/api/admin/*`

Controladores observados:
- `AdminProductController` (`/api/admin/products`)
  - `GET /`
  - `POST /`
  - `PUT /{id}`
  - `DELETE /{id}` (desactivar)
  - `PATCH /{id}/status`
- `AdminProductLotController` (`/api/admin`)
  - `GET /lots`
  - `POST /lots`
  - `PUT /lots/{id}`
  - `DELETE /lots/{id}`
  - `POST /inventory/adjustment`
  - `GET /inventory`
- `AdminSupplierController` (`/api/admin/suppliers`)
- `AdminRestaurantTableController` (`/api/admin/tables`)
- `AdminAnalyticsController` (`/api/admin/analytics`)
- `AdminSalesController` (`/api/admin/sales/{ventaProductoId}/refund`)
- Cloudinary (también admin): `GET /api/admin/cloudinary/signature`

### 6.6 Internos — `/internal/*`

Pensados para diagnóstico/operación (ojo seguridad):
- `GET /internal/test-smtp` (`SmtpConnectivityController`): prueba TCP a `host:port` con timeout.
- `GET /internal/test-email` (`InternalEmailController`): prueba envío de correo.

**Recomendación**: proteger estos endpoints con auth/secret (evitar SSRF y abuso).

## 7) Reglas de negocio (resumen)

### 7.1 Cuentas (registro, activación, recuperación)

Implementación principal: `CuentaServiceImp`
- Registro crea cuenta `INACTIVA` con rol `CLIENTE`.
- Se genera **código de verificación** (vigencia ~15 minutos, según cuerpo de email).
- Recuperación de contraseña:
  - genera código, guarda en cuenta, expira a los 15 minutos.
- Eliminación: es **lógica** (estado `ELIMINADA`).
- Permisos:
  - usuario solo puede modificar/consultar su propia cuenta.
  - admin puede consultar cuentas de otros (según verificación en servicio/controlador).

### 7.2 Catálogo de productos (cliente)

`ProductoServiceImp`:
- Solo devuelve productos visibles al cliente: `active=true`, no out-of-stock y `stockQuantity > 0`.
- Stock se calcula y sincroniza con el **ledger de inventario** (ver 7.4).

### 7.3 Ventas y pagos (Mercado Pago)

`VentaProductoServiceImp`:
- Crea venta a partir de carrito.
- Aplica promoción si existe (descuento porcentual).
- Usa Mercado Pago para pagos y webhooks (recibir notificación).
- Reembolso:
  - existe endpoint admin dedicado y también flujos cliente (según servicio).
- Tras pago, se reduce stock (integración con producto + inventario).

### 7.4 Inventario por lotes + ledger (FIFO)

Servicios admin:
- `AdminProductLotService` (CRUD lotes + ajustes)
- `InventoryTransactionService` (ledger inmutable)

Reglas clave:
- **FIFO**: ventas descuentan unidades por lotes en orden de ingreso (entryDate).
- **Edición de lote**:
  - no se permite editar un lote si ya tuvo consumo (si su bucket ya fue usado).
  - inventario inicial (`OPENING_STOCK_SUPPLIER_ID`) no se modifica ni elimina desde inventario.
- **Anulación/eliminación**:
  - si el lote ya tuvo ventas, se marca como `voided` y, si queda saldo, se crea ajuste compensatorio.
  - se “repara” `productId` en transacciones huérfanas por `referenceId` para mantener contabilidad histórica.
- **Ajustes**:
  - IN: puede crear lote sintético `__ADJUSTMENT__` o ajustar un lote.
  - OUT: valida stock disponible y descuenta en FIFO si no se especifica lote.

### 7.5 Reservas (mesas)

`ReservaServiceImp`:
- Reserva puede ser:
  - por gestor de reservas (varias mesas)
  - directa (una mesa)
- Estado inicial: `PENDIENTE`.
- Calcula fin de reserva basado en duración (default 120 min o según mesa).
- **Valida cruces de horarios**: no permite solapamientos con reservas bloqueantes.
- `reservas.pending-hold-minutes`: retención de pendientes para liberar cupos (según configuración y lógica interna).
- Pagos de reserva integran Mercado Pago y webhooks.

## 8) Observabilidad, salud y métricas

Actuator:
- `GET /actuator/health`, `.../info`
Exposición:
- `management.endpoints.web.exposure.include=*`
- Prometheus habilitado

## 9) Uso local (rápido)

Requisitos:
- Java 17
- MongoDB accesible (local o Atlas)

Ejecutar:
- Windows: `.\gradlew.bat bootRun`
- Linux/macOS: `./gradlew bootRun`

Variables típicas (ver `application.properties`):
- `MONGODB_URI`
- `CORS_ALLOWED_ORIGINS`
- `MERCADOPAGO_*`
- `CLOUDINARY_*`
- `DEEPSEEK_*`
- `RESEND_*` (si usas Resend)

## 10) Riesgos y deuda técnica identificable

- Endpoints `/internal/*` requieren protección adicional en prod (evitar abuso/diagnóstico público).
- `application.properties` contiene flags de logging muy verbosos (binding TRACE) — revisar en prod.

## 11) Guía operativa (deploy y producción)

### 11.1 Entornos recomendados

- **local**: desarrollo en `localhost`, Mongo local o Atlas, CORS a `http://localhost:4200`.
- **staging** (opcional): entorno espejo de producción para validar webhooks/flows.
- **prod**: Render (u otro) con variables de entorno y secretos gestionados por la plataforma.

### 11.2 Variables por entorno (mínimo)

- **MongoDB**
  - `MONGODB_URI`
- **CORS**
  - `CORS_ALLOWED_ORIGINS` (producción: `https://www.tiendasana.shop`)
- **Mercado Pago**
  - `MERCADOPAGO_ACCESS_TOKEN`
  - `MERCADOPAGO_FRONTEND_BASE_URL` (producción: `https://www.tiendasana.shop`)
  - `MERCADOPAGO_WEBHOOK_BASE_URL` (base pública del backend en prod)
- **Cloudinary** (si admin sube imágenes)
  - `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`
- **IA DeepSeek** (si está habilitada)
  - `DEEPSEEK_ENABLED`, `DEEPSEEK_API_KEY`, `DEEPSEEK_BASE_URL`, `DEEPSEEK_MODEL`, `DEEPSEEK_TIMEOUT_MS`
- **Correo**
  - `RESEND_API_KEY`, `RESEND_FROM` (si el envío se hace por Resend)
  - Si usas SMTP: credenciales por env vars (no versionar)
- **Inventario ledger (migración opcional)**
  - `TIENDASANA_INVENTORY_MIGRATION`

### 11.3 Checklist de producción (pre-deploy)

- **Seguridad**
  - Asegurar que los endpoints `/internal/*` estén **protegidos** (auth/secret) o deshabilitados en prod.
  - Configurar secret JWT por env var (no hardcode, no repo).
- **CORS**
  - Permitir únicamente orígenes necesarios (`https://www.tiendasana.shop`).
- **Webhooks**
  - Verificar `MERCADOPAGO_WEBHOOK_BASE_URL` (debe ser alcanzable desde Mercado Pago).
  - Confirmar que los endpoints de notificación respondan 200 y registren eventos.
- **Observabilidad**
  - Health check disponible (`/actuator/health`).
  - Métricas Prometheus accesibles si se usan.
- **Inventario**
  - Si corres migración ledger, hacerlo una sola vez y bajo control (flag).
- **Logging**
  - Bajar verbosidad de logs de binding/trace en prod.

### 11.4 Checklist post-deploy (smoke tests)

- `GET /actuator/health` → `UP`
- Login + endpoints protegidos con JWT funcionan.
- Flujo compra: crear orden → pago → notificación webhook → stock actualizado.
- Flujo reserva: crear reserva → pago → notificación webhook → horarios reservados correctos.
- Admin: CRUD básico (productos/mesas/lotes) y dashboard analítica carga.

## 12) Mapa de endpoints (por controlador)

> Convención: los paths a continuación son los definidos en controladores (Spring). La seguridad depende de `SecurityConfig` + roles + anotaciones `@PreAuthorize`.

### 12.1 `AutenticacionController` — `/api/auth`

- `POST /login`
- `POST /create-account`
- `PUT /send-recover/{email}`
- `PUT /change-password`
- `PUT /validate-account`
- `PUT /resend-validation/{email}`

### 12.2 `PublicoController` — `/api/public`

- `GET /productos/get-all/{pagina}`
- `GET /productos/get-info/{id}`
- `POST /productos/filter-products`
- `GET /productos/get-types`
- `GET /mesas/get-all/{pagina}`
- `GET /mesas/get-info/{id}`
- `GET /mesas/get-reserved-slots/{id}`
- `POST /mesas/filter-tables`
- `GET /mesas/get-locality`
- `POST /ai/recommendations`
- `POST /venta/receive-notification` (Mercado Pago webhook)
- `POST /reserva/receive-notification` (Mercado Pago webhook)

### 12.3 `CuentaController` — `/api/account` (JWT requerido)

- `PUT /update-account`
- `DELETE /delete/{email}`
- `GET /get/{email}`

### 12.4 `ClienteController` — `/api/cliente` (JWT requerido)

Carrito:
- `PUT /carrito/add-item`
- `PUT /carrito/edit-item`
- `DELETE /carrito/delete-item`
- `DELETE /carrito/clear-all-items`
- `GET /carrito/get-items/{emailUsuario}`

Gestor de reservas:
- `PUT /gestor-reservas/add-item`
- `DELETE /gestor-reservas/delete-item`
- `GET /gestor-reservas/get-items/{emailUsuario}`
- `GET /reserva/get-reservation-manager/{email}` (crea/obtiene gestor)

Ventas:
- `POST /venta/create`
- `POST /venta/make-payment/{ventaProductoId}`
- `DELETE /venta/cancel/{ventaProductoId}`
- `POST /venta/refund/{ventaProductoId}`
- `GET /venta/history/{emailUsuario}`
- `GET /venta/get-info/{orderId}`

Reservas:
- `POST /reserva/create`
- `POST /reserva/create-direct`
- `POST /reserva/make-payment/{reservaId}`
- `DELETE /reserva/cancel/{reservaId}`
- `GET /reserva/history/{emailUsuario}`
- `GET /reserva/get-info/{reservaId}`

### 12.5 `AdminProductController` — `/api/admin/products` (ADMIN)

- `GET /`
- `POST /`
- `PUT /{id}`
- `DELETE /{id}`
- `PATCH /{id}/status`

### 12.6 `AdminSupplierController` — `/api/admin/suppliers` (ADMIN)

- `GET /`
- `POST /`
- `PUT /{id}`
- `DELETE /{id}`
- `PATCH /{id}/activate`

### 12.7 `AdminRestaurantTableController` — `/api/admin/tables` (ADMIN)

- `GET /`
- `POST /`
- `PUT /{id}`
- `PATCH /{id}/status`

### 12.8 `AdminProductLotController` — `/api/admin` (ADMIN)

- `GET /lots` (query optional: `productId`)
- `POST /lots`
- `PUT /lots/{id}`
- `DELETE /lots/{id}`
- `POST /inventory/adjustment`
- `GET /inventory`

### 12.9 `AdminAnalyticsController` — `/api/admin/analytics` (ADMIN)

- `GET /dashboard` (query: `from`, `to`, `comparePrevious`)
- `GET /sales` (query: `from`, `to`, `page`, `size`, `q`, `paymentStatus`)
- `GET /reservations` (query: `from`, `to`, `page`, `size`, `q`, `estado`)
- `GET /product-performance` (query: `from`, `to`)
- `GET /table-performance` (query: `from`, `to`)

### 12.10 `AdminSalesController` — `/api/admin/sales` (ADMIN)

- `POST /{ventaProductoId}/refund`

### 12.11 `CloudinaryController` — `/cloudinary` y `/api/admin/cloudinary` (ADMIN)

- `GET /signature`

### 12.12 `SmtpConnectivityController` — `/internal` (diagnóstico)

- `GET /test-smtp` (query: `host`, `port`, `timeoutMs`)

### 12.13 `InternalEmailController` — `/internal` (diagnóstico)

- `GET /test-email` (query: `to`, `subject`, `body`)

## 13) Glosario de dominio

- **Cuenta**: identidad de usuario (email como llave), rol (`CLIENTE`/`ADMIN`) y estado (activa/inactiva/eliminada).
- **JWT**: token de autenticación para acceder a rutas protegidas.
- **Producto**: ítem vendible del catálogo; tiene estado activo y un “stock cacheado” sincronizado desde el ledger.
- **Carrito**: selección temporal de productos a comprar.
- **Venta**: orden de compra con detalles (items, totales), estado de pago y posibilidad de reembolso.
- **Pago**: información de transacción asociada a venta o reserva (integración Mercado Pago).
- **Mesa**: recurso reservable (capacidad, precio, localidad, estado).
- **Reserva**: bloqueo temporal de una o varias mesas para un rango horario; inicia en `PENDIENTE` y se consolida con pago.
- **Gestor de reservas**: contenedor de “mesas seleccionadas” previo a crear una reserva.
- **Lote (ProductLot)**: entrada de inventario asociada a un producto (fecha de ingreso, proveedor, cantidad, valor unitario).
- **Ledger de inventario (Inventory Transactions)**: historial inmutable de movimientos (entradas/salidas/ajustes).
- **FIFO**: estrategia de consumo de lotes: se descuenta primero el lote más antiguo.
- **Ajuste de inventario**: corrección operacional registrada como nueva transacción (nunca editando el historial).
- **Anulación de lote (voided)**: un lote se marca como inválido; si tenía saldo o consumo, se compensa con ajustes para mantener consistencia.

