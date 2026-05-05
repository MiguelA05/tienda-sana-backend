# Restaurant System API (Backend)

API REST para la gestión de **productos, inventario, ventas y reservas** en un sistema de restaurante.

Implementa un modelo de inventario **basado en transacciones (ledger)** para garantizar consistencia.

## Tech Stack

- Java 17
- Spring Boot 3.x (Web, Validation, Security)
- MongoDB (Spring Data MongoDB)
- JWT (jjwt)
- OpenAPI / Swagger (springdoc)
- Observabilidad: Actuator + Prometheus
- Integraciones: Mercado Pago, Cloudinary, DeepSeek (IA), Email (SMTP / Resend)

## Architecture

El sistema sigue un modelo basado en:

- **Productos** (catálogo + estado activo)
- **Transacciones de inventario** (ledger inmutable)
- **Ventas** (órdenes, pago, webhooks, reembolsos)
- **Reservas** (mesas, horarios, pago, webhooks)

### Inventario (ledger)

El stock **NO** se almacena como “fuente de verdad”; se calcula dinámicamente como:

\[
stock(productId) = \sum transactions.quantity
\]

Reglas clave:
- Las **salidas** registran cantidades negativas (por ejemplo, ventas).
- Los **ajustes** se registran como nuevas transacciones (no se reescribe historial).
- Para ventas se aplica **FIFO por lotes** cuando hay lotes disponibles.

## Project Structure

Estructura real (resumen) del proyecto:

src/main/java/co/uniquindio/tiendasana/
 ├── controllers/              # REST controllers (public, auth, cliente, admin, internal)
 ├── services/
 │   ├── interfaces/           # Contratos (ports)
 │   ├── implementations/      # Casos de uso (ventas, reservas, cuentas, etc.)
 │   └── admin/                # Inventario ledger, lotes, catálogos admin, analítica
 ├── repos/                    # Repositorios (incluye repos mongo)
 ├── model/
 │   ├── documents/            # Entidades de dominio (Cuenta, Reserva, VentaProducto, etc.)
 │   ├── mongo/                # Documentos/colecciones para lotes y ledger
 │   ├── enums/                # Enums de negocio
 │   └── vo/                   # Value Objects
 ├── dto/                      # DTOs (request/response)
 ├── config/                   # Seguridad, JWT, CORS, rate limit, etc.
 ├── exceptions/               # Manejo global de errores/excepciones
 └── utils/                    # Constantes y utilidades

src/main/resources/
 └── application.properties    # Configuración (con fallback a variables de entorno)

docs/
 ├── BACKEND_INIT.md           # Guía de inicio local (variables y comandos)
 └── BACKEND_DOCUMENTATION.md  # Documentación técnica extendida

## Installation

```bash
git clone <repo>
cd tienda-sana-backend
```

Requisitos:
- Java 17
- MongoDB accesible (local o Atlas)

En Windows (PowerShell):

```powershell
.\gradlew.bat build
```

En Linux/macOS:

```bash
./gradlew build
```

## Environment Variables

Variables principales (ver `src/main/resources/application.properties`):

```md
# Runtime
PORT=8080
MONGODB_URI=mongodb://localhost:27017/tienda_sana
CORS_ALLOWED_ORIGINS=http://localhost:4200

# Mercado Pago
MERCADOPAGO_ACCESS_TOKEN=...
MERCADOPAGO_FRONTEND_BASE_URL=http://localhost:4200
MERCADOPAGO_WEBHOOK_BASE_URL=http://localhost:8080

# Cloudinary
CLOUDINARY_CLOUD_NAME=...
CLOUDINARY_API_KEY=...
CLOUDINARY_API_SECRET=...

# IA (DeepSeek)
DEEPSEEK_ENABLED=true
DEEPSEEK_API_KEY=...
DEEPSEEK_BASE_URL=https://api.deepseek.com
DEEPSEEK_MODEL=deepseek-chat
DEEPSEEK_TIMEOUT_MS=12000

# Email (Resend)
RESEND_API_KEY=...
RESEND_FROM=no-reply@tiendasana.shop

# Migración inventario ledger (solo si aplica)
TIENDASANA_INVENTORY_MIGRATION=false
```

## Run

Windows:

```powershell
.\gradlew.bat bootRun
```

Linux/macOS:

```bash
./gradlew bootRun
```

## API Endpoints

Resumen por dominios (paths reales del proyecto):

### Auth
- `POST /api/auth/login`
- `POST /api/auth/create-account`
- `PUT /api/auth/send-recover/{email}`
- `PUT /api/auth/change-password`
- `PUT /api/auth/validate-account`
- `PUT /api/auth/resend-validation/{email}`

### Public (Catálogo)
- `GET /api/public/productos/get-all/{pagina}`
- `GET /api/public/productos/get-info/{id}`
- `POST /api/public/productos/filter-products`
- `GET /api/public/mesas/get-all/{pagina}`
- `GET /api/public/mesas/get-info/{id}`
- `GET /api/public/mesas/get-reserved-slots/{id}`
- `POST /api/public/ai/recommendations`

### Cliente (JWT requerido)
- Carrito: `PUT /api/cliente/carrito/add-item`, `PUT /api/cliente/carrito/edit-item`, `DELETE /api/cliente/carrito/delete-item`, etc.
- Ventas: `POST /api/cliente/venta/create`, `POST /api/cliente/venta/make-payment/{id}`, etc.
- Reservas: `POST /api/cliente/reserva/create`, `POST /api/cliente/reserva/make-payment/{id}`, etc.

### Admin (ROLE_ADMIN)
- Productos: `GET/POST /api/admin/products`, `PUT /api/admin/products/{id}`, `PATCH /api/admin/products/{id}/status`
- Inventario/lotes:
  - `GET /api/admin/lots`
  - `POST /api/admin/lots`
  - `PUT /api/admin/lots/{id}`
  - `DELETE /api/admin/lots/{id}`
  - `POST /api/admin/inventory/adjustment`
  - `GET /api/admin/inventory`
- Analítica: `GET /api/admin/analytics/...` (según controlador)
- Reembolsos: `POST /api/admin/sales/{ventaProductoId}/refund`

### Webhooks (Mercado Pago)
- `POST /api/public/venta/receive-notification`
- `POST /api/public/reserva/receive-notification`

### Internal (diagnóstico)
- `GET /internal/test-smtp`
- `GET /internal/test-email`

## Inventory System

- Basado en **transacciones inmutables** (ledger).
- **No** se elimina historial para “corregir” stock; se corrige con nuevas transacciones.
- FIFO por lotes:
  - Las ventas consumen primero lotes más antiguos (por fecha de ingreso).
  - Si no hay capacidad en lotes activos, el remanente se registra como venta con referencia alternativa.

Tipos (según `InventoryTransactionTypes`):
- `ENTRY`
- `SALE`
- `ADJUSTMENT`

## Database

Persistencia: **MongoDB** (colecciones/documentos).

Colecciones típicas (nombres pueden variar por configuración):
- `cuentas` (Cuenta)
- `productos` / `producto_documents` (ProductoDocument)
- `product_lots` (ProductLotDocument)
- `inventory_transactions` (InventoryTransactionDocument)
- `ventas` / `venta_producto` (VentaProducto)
- `reservas` (Reserva)
- `mesas` (Mesa)

## Business Rules

- No eliminar transacciones del ledger de inventario.
- No modificar historial para cuadrar stock; usar ajustes.
- Control de acceso:
  - `/api/admin/**` requiere `ROLE_ADMIN`
  - `/api/cliente/**` y `/api/account/**` requieren JWT válido
  - acciones de cuenta validan que el email autenticado coincida (o sea admin cuando aplique)

## Testing

Unit tests:

```bash
./gradlew test
```

Atajo (si existe en tu `build.gradle`):

```bash
./gradlew unitTest
```

## Error Handling

- Manejo global de excepciones (ver `exceptions/GlobalExceptions.java`).
- Patrón de respuesta frecuente: `MessageDTO<T>` con `error` + `reply`.

## Future Improvements

- Cache controlado del stock (sin perder consistencia del ledger)
- Auditoría avanzada (quién/por qué ajustó inventario, correlación con ventas)
- WebSockets o SSE para notificaciones (stock bajo, eventos de pago)
- Endpoints `/internal/*` protegidos por secret/rol para producción

