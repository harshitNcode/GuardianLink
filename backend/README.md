~~# GuardianLink FastAPI Backend (100% Free Architecture)

High-performance, async IoT backend for **GuardianLink** with real-time GPS telemetry, PostGIS geofencing, and WebSocket streaming.

---

## 1. Quick Local Start (0 Setup Fees)

```bash
# Navigate to backend folder
cd C:\Users\Harshit\AndroidStudioProjects\GuardianLink\backend

# Activate virtual environment (Windows PowerShell)
.\venv\Scripts\Activate.ps1

# Start local server with hot reload
uvicorn app.main:app --reload --port 8000
```

* **Interactive Swagger API Docs**: Open [http://localhost:8000/docs](http://localhost:8000/docs)
* **Interactive Redoc**: [http://localhost:8000/redoc](http://localhost:8000/redoc)
* **WebSocket Endpoint**: `ws://localhost:8000/ws/devices/{device_id}`

---

## 2. Connect 100% Free Cloud PostgreSQL with PostGIS

To switch from local SQLite to a permanent free cloud database:

1. Go to **[Neon.tech](https://neon.tech/)** (or **Supabase.com**) and create a free account ($0, no credit card required).
2. Create a new project named `guardianlink`.
3. In the SQL Editor tab, run:
   ```sql
   CREATE EXTENSION IF NOT EXISTS postgis;
   ```
4. Copy your PostgreSQL connection string:
   `postgresql://username:password@ep-sample-123456.us-east-2.aws.neon.tech/guardianlink?sslmode=require`
5. In `backend/.env`, set:
   ```env
   DATABASE_URL=postgresql://username:password@ep-sample-123456.us-east-2.aws.neon.tech/guardianlink?sslmode=require
   ```
The backend will automatically create all tables and use PostGIS spatial indexes!

---

## 3. Deploy to Render.com (100% Free Web Service)

1. Push this project to your GitHub account.
2. Sign up on **[Render.com](https://render.com/)** (Free tier).
3. Click **New +** → **Web Service** → select your GitHub repository.
4. Set:
   * **Runtime**: Python 3
   * **Build Command**: `pip install -r requirements.txt`
   * **Start Command**: `uvicorn app.main:app --host 0.0.0.0 --port $PORT`
   * **Instance Type**: Free
   * **Environment Variables**: Add `DATABASE_URL` with your Neon.tech connection string.
5. Click **Create Web Service**. Your backend is now live with free SSL/HTTPS and WebSockets!

---

## 4. Key Endpoints

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/v1/telemetry` | Ingests GPS, battery, network from ESP32 & checks geofence |
| `GET` | `/api/v1/telemetry/{id}/latest` | Fetch latest device status for Android dashboard |
| `GET` | `/api/v1/telemetry/{id}/history` | Historical location trail |
| `WS` | `/ws/devices/{device_id}` | Real-time WebSocket streaming to Android app |
| `POST` | `/api/v1/geofence` | Create circular or polygon safe zones |
| `GET` | `/api/v1/geofence/{id}/check` | Check if coordinates are inside safe zone |
| `POST` | `/api/v1/alerts/sos` | Trigger emergency SOS & broadcast |
| `GET` | `/api/v1/alerts/{user_id}` | List alert history |
| `GET/POST`| `/api/v1/contacts/{user_id}` | Emergency contacts CRUD |
| `GET/PUT` | `/api/v1/users/{uid}` | User profile & device pairing |~~
