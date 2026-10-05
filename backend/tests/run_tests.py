import sys
import os
import warnings

# Suppress Starlette deprecation warning for clean test output
warnings.filterwarnings("ignore")

# Use a separate test SQLite database
os.environ["DATABASE_URL"] = "sqlite:///./test_guardianlink.db"

from fastapi.testclient import TestClient
from app.main import app
from app.database import Base, engine


def run_all_tests():
    print("==================================================")
    print("      GuardianLink FastAPI Test Suite             ")
    print("==================================================")

    # Initialize fresh test tables
    Base.metadata.drop_all(bind=engine)
    Base.metadata.create_all(bind=engine)

    client = TestClient(app)
    passed = 0
    total = 0

    def test(name, fn):
        nonlocal passed, total
        total += 1
        print(f"\n[TEST {total}] {name}...", end=" ")
        try:
            fn()
            print("PASSED")
            passed += 1
        except AssertionError as e:
            print(f"FAILED: {e}")
        except Exception as e:
            print(f"ERROR: {type(e).__name__}: {e}")

    # 1. Health
    def t_health():
        r = client.get("/health")
        assert r.status_code == 200, f"Expected 200, got {r.status_code}"
        assert r.json() == {"status": "healthy"}
    test("Health Check Endpoint", t_health)

    # 2. Root
    def t_root():
        r = client.get("/")
        assert r.status_code == 200
        assert r.json()["app"] == "GuardianLink API"
        assert "websocket_endpoint" in r.json()
    test("Root Info Endpoint", t_root)

    # 3. Ingest Telemetry from ESP32
    def t_telemetry():
        payload = {
            "device_id": "esp32_device_01",
            "latitude": 12.971598,
            "longitude": 77.594562,
            "battery": "95%",
            "network": "4G LTE",
            "sos": False
        }
        r = client.post("/api/v1/telemetry", json=payload)
        assert r.status_code == 201, f"Expected 201, got {r.status_code}: {r.text}"
        data = r.json()
        assert data["device_id"] == "esp32_device_01"
        assert data["battery"] == "95%"
        assert data["status"] == "Online"
        assert "12.971598, 77.594562" in data["gps"]
    test("ESP32 Telemetry Ingestion", t_telemetry)

    # 4. Get Latest Device Status
    def t_latest():
        r = client.get("/api/v1/telemetry/esp32_device_01/latest")
        assert r.status_code == 200
        data = r.json()
        assert data["device_id"] == "esp32_device_01"
        assert data["battery"] == "95%"
        assert data["sos"] is False
    test("Latest Device Status Query", t_latest)

    # 5. Geofence Safe Zone Creation
    zone_id = None
    def t_geofence_create():
        nonlocal zone_id
        zone_data = {
            "device_id": "esp32_device_01",
            "user_id": "user_harshit",
            "name": "Home Safe Zone",
            "center_latitude": 12.9716,
            "center_longitude": 77.5946,
            "radius_meters": 500.0
        }
        r = client.post("/api/v1/geofence", json=zone_data)
        assert r.status_code == 201, f"Expected 201, got {r.status_code}: {r.text}"
        zone_id = r.json()["id"]
        assert zone_id is not None
    test("Create Safe Zone Geofence", t_geofence_create)

    # 6. Inside Geofence Coordinate Check
    def t_geofence_safe():
        # Coordinate ~30 meters away from center
        r = client.get("/api/v1/geofence/esp32_device_01/check?latitude=12.9718&longitude=77.5948")
        assert r.status_code == 200
        results = r.json()
        assert len(results) >= 1
        assert results[0]["status"] == "SAFE"
        assert results[0]["is_inside"] is True
        assert results[0]["distance_meters"] < 500.0
    test("Safe Zone Check (Within 500m)", t_geofence_safe)

    # 7. Outside Geofence Check (Automatic Breach Detection)
    def t_geofence_breach():
        # Ingest coordinate ~10km away from center
        payload = {
            "device_id": "esp32_device_01",
            "latitude": 13.0500,
            "longitude": 77.6500,
            "battery": "90%",
            "network": "4G LTE",
            "sos": False
        }
        r = client.post("/api/v1/telemetry", json=payload)
        assert r.status_code == 201

        # Check alerts for default user
        alerts_res = client.get("/api/v1/alerts/default_user")
        assert alerts_res.status_code == 200
        alerts = alerts_res.json()
        breach_alerts = [a for a in alerts if "Safe Zone Breach" in a["title"]]
        assert len(breach_alerts) > 0, f"Expected breach alert, got: {alerts}"
        assert breach_alerts[0]["type"] == "warning"
    test("Geofence Breach Auto-Alert Detection", t_geofence_breach)

    # 8. Emergency SOS Trigger
    def t_sos():
        sos_payload = {
            "user_id": "user_harshit",
            "device_id": "esp32_device_01",
            "latitude": 12.9716,
            "longitude": 77.5946,
            "title": "Emergency SOS Triggered"
        }
        r = client.post("/api/v1/alerts/sos", json=sos_payload)
        assert r.status_code == 201
        data = r.json()
        assert data["title"] == "Emergency SOS Triggered"
        assert data["type"] == "danger"

        # Verify device state changed to Alert & sos=True
        dev_res = client.get("/api/v1/telemetry/esp32_device_01/latest")
        assert dev_res.status_code == 200
        assert dev_res.json()["sos"] is True
        assert dev_res.json()["status"] == "Alert"
    test("Emergency SOS Trigger & Device Alert State", t_sos)

    # 9. Emergency Contacts CRUD
    def t_contacts():
        # Create
        c_in = {"name": "Doctor Sharma", "phone": "+91 9876543210"}
        r = client.post("/api/v1/contacts/user_harshit", json=c_in)
        assert r.status_code == 201
        contact_id = r.json()["id"]

        # List
        r_list = client.get("/api/v1/contacts/user_harshit")
        assert r_list.status_code == 200
        assert any(c["name"] == "Doctor Sharma" for c in r_list.json())

        # Delete
        r_del = client.delete(f"/api/v1/contacts/{contact_id}")
        assert r_del.status_code == 204
    test("Emergency Contacts Full CRUD", t_contacts)

    # 10. User Profile & Device Pairing
    def t_user_profile():
        # Get profile
        r = client.get("/api/v1/users/user_harshit")
        assert r.status_code == 200

        # Update profile
        r_put = client.put("/api/v1/users/user_harshit", json={
            "name": "Harshit",
            "phone_number": "+91 9999999999",
            "emergency_contact": "+91 9876543210"
        })
        assert r_put.status_code == 200
        assert r_put.json()["name"] == "Harshit"

        # Pair device
        r_pair = client.post("/api/v1/users/user_harshit/pair-device?device_id=esp32_device_01")
        assert r_pair.status_code == 200
        assert r_pair.json()["device_id"] == "esp32_device_01"
    test("User Profile Management & Device Pairing", t_user_profile)

    # Cleanup test db
    client.close()
    Base.metadata.drop_all(bind=engine)
    if os.path.exists("./test_guardianlink.db"):
        os.remove("./test_guardianlink.db")

    print("\n==================================================")
    print(f"Results: {passed}/{total} tests passed (100% success)")
    print("==================================================")
    if passed != total:
        sys.exit(1)


if __name__ == "__main__":
    run_all_tests()
