# Smart Order (H5 + Realtime)

A full scan-to-order flow: customer scans a table QR -> shared cart + AI suggestions -> order is created -> kitchen/cashier see realtime updates -> cashier settles and clears table -> orders archived. Browser-only clients (mobile + desktop). No payment integration or printing in MVP.

## Architecture Overview
- Backend: Java (REST + WebSocket)
- Frontend: React 18 + Vite + TypeScript (SPA)
- Realtime: WebSocket (STOMP over SockJS)
- Auth: JWT access + refresh tokens, refresh persisted in Redis
- Database: MySQL (all core data persisted)
- Cache/session: Redis
- Media: MinIO (image storage)
- Deployment: local Docker or cloud VM/container
- Database: uses external MySQL (Cloud SQL) in production

## Roles & Pages
### Customer (Mobile H5)
- QR scan binds table
- Shared cart across same table (realtime sync)
- AI suggestion flow (can skip)
- AI adjust: replace vs add, with before/after compare
- Menu shows rich dish details: image, ingredients, calories, allergens
- Orders view shows kitchen/payment status

### Kitchen (Web)
- Realtime orders
- Actions: Accept (NEW -> ACCEPTED), Ready (ACCEPTED -> READY)

### Cashier (Web)
- Table summary with unpaid aggregation
- Settle all unpaid for a table
- Clear paid orders (archive) and reset table

### Store Admin (Web)
- Menu builder: image parse (OpenAI Vision) or text parse
- Manual menu maintenance: add categories and dishes directly
- Dish edit: description, ingredients, allergens, calories, thumbnail + detail image
- Dish options: size/spice/ice and other option groups
- AI Autofill for dish fields (preview + manual apply)
- Brand assets: store logo + cover image
- Staff user management (cashier/kitchen/admin)
- Wallet overview (balance + ledger)
- Table management: create table, bind/unbind existing QR codes, and generate QR links

### Platform Admin (Web)
- Platform login
- Pricing management (platform fee, store fee, AI per-call)
- Store list, subscription status, AI token usage
- Manual top-up, pause/renew subscription, charge subscription

## Key Product Rules
- Shared table cart: multiple customers can add to the same cart
- Session lock: if a table already has activity, new scans skip AI/start and go directly to menu
- AI adjust uses current cart and preferences; user confirms before applying
- Trial rule: new store gets 3-day trial; after expiry AI features are disabled until subscription is paid
- Auto-renew: within 7 days of expiry, system attempts to charge balance at 00:00 daily (renews 30 days)

## State Machine (MVP)
- OrderStatus: NEW -> ACCEPTED -> READY -> CLOSED
- PaymentStatus: UNPAID -> PAID
- TableStatus: IDLE -> DINING -> TO_PAY -> IDLE

Rule of thumb:
- If a table has any open order, it is DINING
- After payment, it moves to TO_PAY
- Clear table closes PAID orders and resets table to IDLE

## Realtime Events
WebSocket event stream (`/topic/events`):
- ORDER_CREATED
- ORDER_UPDATED
- TABLE_UPDATED
- MENU_UPDATED
- CART_UPDATED

## OpenAI Integrations
1) Menu image/text -> structured menu
   - Parsed categories/dishes are written into MySQL automatically
2) Customer preference -> dish recommendations
3) Dish detail autofill (ingredients, allergens, calories, tags)

## Project Structure
```
backend/
  src/main/java/...
  src/main/resources/...
frontend/
  index.html
  src/
    api/
    components/
    pages/
    styles/
  vite.config.js
  tsconfig.json
```

## Local Run
### Backend (Java 17 + Maven)
```
cd backend
mvn spring-boot:run
```
Backend runs at `http://localhost:8080`.

### Frontend (Vite)
```
cd frontend
npm install
npm run dev
```
Frontend defaults to same-origin `/api` and `/ws`. If you need custom domains, set in `frontend/.env`:
```
VITE_API_BASE=https://your-api.example.com
VITE_WS_URL=https://your-api.example.com/ws
```
Dev server also proxies `/api` and `/ws` to `http://localhost:8080`.
In production, Nginx forwards `/api` and `/ws` to the backend (see `deploy/nginx.conf`).
Then open:
- Landing: `http://localhost:8081/`
- Store Login: `http://localhost:8081/login`
- Platform Login: `http://localhost:8081/platform-login`
- Admin: `http://localhost:8081/admin`
- Customer (direct): `http://localhost:8081/customer?storeId=demo-store&tableNo=T1`
- Customer (QR): `http://localhost:8081/q?code=YOUR_CODE`
- Kitchen: `http://localhost:8081/kitchen`
- Cashier: `http://localhost:8081/cashier`
- Platform Admin: `http://localhost:8081/platform`

## Deployment (Practical Steps)
### Option A: Single VM (fastest MVP)
1) Provision a Linux VM (AWS EC2 / GCP Compute Engine)
2) Install Java 17 + Maven
3) Copy repo to the VM
4) Build + run backend:
```
cd backend
mvn spring-boot:run
```
5) Serve frontend static files (nginx or a simple static server)
6) Set `VITE_API_BASE` and `VITE_WS_URL` in your frontend environment (or set `window.API_BASE` / `window.WS_URL` in your hosting template)
7) Open firewall ports:
- 8080 (backend)
- 80/443 (frontend)
- 6379 (redis)
- 9000 (minio)
- 9001 (minio console)

### Option B: Docker Compose (server)
Run Nginx + backend + Redis + MinIO + Cloud SQL proxy (MySQL is external, e.g., Cloud SQL):
```
docker compose up -d --build
docker compose ps
```
This uses `docker-compose.yml` and serves frontend via Nginx.

### Option C: Docker Compose (local)
Run Redis + MinIO only (MySQL and backend run locally):
```
docker compose -f docker-compose.local.yml up -d
docker compose -f docker-compose.local.yml ps
```
Then run backend + frontend with `mvn spring-boot:run` and `npm run dev`.

## Local Scripts & Operational Checklist
### SQL Init (manual)
This project does not auto-run SQL. Run manually in MySQL:
```
mysql -u root -p < backend/src/main/resources/schema.sql
```
Seed data is intentionally empty. Add your own records or use the UI.

### Clear Database (manual)
```
mysql -u root -p < scripts/clear-db.sql
```
This drops all tables (use when schema changes).

### MinIO Bucket
If using MinIO for images, create the bucket once:
```
mc alias set local http://localhost:9000 admin admin123
mc mb local/smart-order
```
Or create the bucket in the MinIO console (`http://localhost:9001`).

### Redis
Redis is required for refresh tokens and session lock.
If using Docker Compose, it is already provisioned.

### Startup Order (local)
1) `docker compose -f docker-compose.local.yml up -d`
2) `cd backend && mvn spring-boot:run`
3) `cd frontend && npm install && npm run dev`

## API (Current MVP)
Auth & Users:
- POST /api/auth/login
- POST /api/auth/register
- POST /api/auth/refresh
- GET /api/users?storeId=
- POST /api/users

Store:
- GET /api/store/{storeId}
- PATCH /api/store/{storeId}

Menu:
- GET /api/menu/{storeId}
- PATCH /api/menu/{storeId}/dishes/{dishId}
- POST /api/menu/{storeId}/categories
- POST /api/menu/{storeId}/categories/{categoryId}/dishes
- POST /api/menu/parse
- POST /api/menu/parse-file (multipart file)
- POST /api/menu/ai-fill

Media:
- POST /api/media/upload (multipart file)

Orders:
- POST /api/orders
- GET /api/orders?storeId=&tableNo=&status=
- PATCH /api/orders/{id}/status
- PATCH /api/orders/{id}/payment

Tables & Cart:
- GET /api/cart?storeId=&tableNo=
- POST /api/cart/items
- PATCH /api/cart/items
- DELETE /api/cart/items?storeId=&tableNo=&dishId=&optionSignature=
- POST /api/cart/clear
- GET /api/tables?storeId=
- POST /api/tables
- POST /api/tables/bind
- POST /api/tables/unbind
- GET /api/q?code=
- POST /api/tables/{tableNo}/settle
- POST /api/tables/{tableNo}/clear
- GET /api/session?storeId=&tableNo=
- POST /api/session/lock
- POST /api/session/unlock

AI:
- POST /api/ai/recommend

Wallet & Billing:
- GET /api/wallet/{storeId}

Platform Admin:
- POST /api/platform/login
- GET /api/platform/pricing
- POST /api/platform/pricing
- GET /api/platform/stores
- POST /api/platform/stores/{storeId}/topup
- POST /api/platform/stores/{storeId}/subscription/pause
- POST /api/platform/stores/{storeId}/subscription/renew
- POST /api/platform/stores/{storeId}/subscription/charge

## Data Model (Minimal)
- Store (name, logoUrl, coverUrl)
- User (storeId, username, password, role)
- Table
- Menu (Category, Dish, OptionGroup)
- Dish fields: name, price, description, imageUrl, detailImageUrl, ingredients, allergens, calories, tags
- Order + OrderItem + SelectedOption
- Cart (table-level, selected options supported)
- Wallet + Ledger
- Subscription + Pricing
- AuthSession (refresh token store in Redis)

## Notes
- Core business data is persisted in MySQL; auth refresh tokens are persisted in Redis.
- New store registration creates a 3-day trial subscription.
- MySQL schema lives in `backend/src/main/resources/schema.sql`. Seed data is intentionally empty; run SQL manually if needed.
- WebSocket endpoint: `/ws` (SockJS); clients subscribe to `/topic/events`.
- Frontend uses npm packages for SockJS/STOMP (no CDN dependencies).

## OpenAI Config
Set the API key in `backend/src/main/resources/application.yml`:
```
app:
  openai:
    api-key: ${OPENAI_API_KEY:}
```
Or set environment variable `OPENAI_API_KEY`.

## Auth Config
JWT + refresh settings in `backend/src/main/resources/application.yml`:
```
app:
  auth:
    jwt-secret: ${JWT_SECRET:change-this-secret-32-bytes-min}
    access-ttl-minutes: 60
    refresh-ttl-days: 30
```
Redis settings:
```
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
```

## Environment Templates (Dev vs Prod)
### Local Dev (.env)
```
JWT_SECRET=change-this-to-strong-32bytes-min
OPENAI_API_KEY=

DB_URL=jdbc:mysql://localhost:3306/smart_order?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
DB_USER=smart
DB_PASSWORD=smart

REDIS_HOST=localhost
REDIS_PORT=6379

MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=admin
MINIO_SECRET_KEY=admin123
MINIO_BUCKET=smart-order
```

### Production (docker-compose.yml)
```
JWT_SECRET=change-this-to-strong-32bytes-min
OPENAI_API_KEY=your-openai-key

CLOUDSQL_INSTANCE=YOUR_PROJECT:YOUR_REGION:YOUR_INSTANCE
DB_NAME=smart_order
DB_USER=smart
DB_PASSWORD=smart

MINIO_ROOT_USER=admin
MINIO_ROOT_PASSWORD=admin123
MINIO_BUCKET=smart-order
```

## MinIO Config
```
app:
  minio:
    endpoint: http://localhost:9000
    accessKey: admin
    secretKey: admin123
    bucket: smart-order
    thumb-size: 320
    detail-size: 900
```
