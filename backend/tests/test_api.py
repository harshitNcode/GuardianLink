import os
import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

# Use a separate test SQLite database
os.environ["DATABASE_URL"] = "sqlite:///./test_guardianlink.db"

from app.database import Base, get_db
from app.main import app

test_engine = create_engine("sqlite:///./test_guardianlink.db", connect_args={"check_same_thread": False})
TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=test_engine)


def override_get_db():
    db = TestingSessionLocal()
    try:
        yield db
    finally:
        db.close()


app.dependency_overrides[get_db] = override_get_db


@pytest.fixture(scope="session", autouse=True)
def setup_db():
    Base.metadata.drop_all(bind=test_engine)
    Base.metadata.create_all(bind=test_engine)
    yield
    Base.metadata.drop_all(bind=test_engine)
    if os.path.exists("./test_guardianlink.db"):
        os.remove("./test_guardianlink.db")


@pytest.fixture
def client():
    with TestClient(app) as c:
        yield c


def test_health(client):
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "healthy"}


def test_telemetry_ingestion(client):
    payload = {
        "device_id": "test_esp32_01",
        "latitude": 12.971598,
        "longitude": 77.594562,
        "battery": "92%",
        "network": "Wi-Fi",
        "sos": False
    }
    response = client.post("/api/v1/telemetry", json=payload)
    assert response.status_code == 201
    data = response.json()
    assert data["device_id"] == "test_esp32_01"
    assert data["battery"] == "92%"
    assert data["latitude"] == 12.971598
    assert data["longitude"] == 77.594562
    assert "12.971598, 77.594562" in data["gps"]


def test_get_latest_status(client):
    response = client.get("/api/v1/telemetry/test_esp32_01/latest")
    assert response.status_code == 200
    data = response.json()
    assert data["device_id"] == "test_esp32_01"
    assert data["battery"] == "92%"


def test_geofencing_safe_and_breach(client):
    # 1. Create a safe zone centered at Bangalore center with 500m radius
    zone_data = {
        "device_id": "test_esp32_01",
        "user_id": "user_harshit",
        "name": "Home Safe Zone",
        "center_latitude": 12.9716,
        "center_longitude": 77.5946,
        "radius_meters": 500.0
    }
    create_res = client.post("/api/v1/geofence", json=zone_data)
    assert create_res.status_code == 201
    zone_id = create_res.json()["id"]

    # 2. Ingest coordinate INSIDE safe zone (~50m away)
    inside_payload = {
        "device_id": "test_esp32_01",
        "latitude": 12.9718,
        "longitude": 77.5948,
        "battery": "90%",
        "network": "Wi-Fi",
        "sos": False
    }
    res_inside = client.post("/api/v1/telemetry", json=inside_payload)
    assert res_inside.status_code == 201

    # Check on-demand geofence evaluation for the inside point
    check_res = client.get("/api/v1/geofence/test_esp32_01/check?latitude=12.9718&longitude=77.5948")
    assert check_res.status_code == 200
    results = check_res.json()
    assert len(results) >= 1
    assert results[0]["status"] == "SAFE"
    assert results[0]["is_inside"] is True

    # 3. Ingest coordinate OUTSIDE safe zone (10 km away) -> Should auto-generate breach alert!
    outside_payload = {
        "device_id": "test_esp32_01",
        "latitude": 13.0500,
        "longitude": 77.6500,
        "battery": "88%",
        "network": "Wi-Fi",
        "sos": False
    }
    res_outside = client.post("/api/v1/telemetry", json=outside_payload)
    assert res_outside.status_code == 201

    # Check alerts: an alert for geofence breach should now exist
    alerts_res = client.get("/api/v1/alerts/default_user")
    assert alerts_res.status_code == 200
    alerts = alerts_res.json()
    breach_alerts = [a for a in alerts if "Safe Zone Breach" in a["title"]]
    assert len(breach_alerts) > 0


def test_sos_trigger(client):
    sos_payload = {
        "user_id": "user_harshit",
        "device_id": "test_esp32_01",
        "latitude": 12.9716,
        "longitude": 77.5946,
        "title": "Emergency SOS Triggered"
    }
    response = client.post("/api/v1/alerts/sos", json=sos_payload)
    assert response.status_code == 201
    alert_data = response.json()
    assert alert_data["title"] == "Emergency SOS Triggered"
    assert alert_data["type"] == "danger"

    # Verify device status changed to Alert / sos = True
    device_res = client.get("/api/v1/telemetry/test_esp32_01/latest")
    assert device_res.status_code == 200
    assert device_res.json()["sos"] is True
    assert device_res.json()["status"] == "Alert"


def test_contacts_crud(client):
    # 1. Add contact
    contact_data = {"name": "Mom", "phone": "+91 9876543210"}
    add_res = client.post("/api/v1/contacts/user_harshit", json=contact_data)
    assert add_res.status_code == 201
    c_id = add_res.json()["id"]

    # 2. Get contacts
    get_res = client.get("/api/v1/contacts/user_harshit")
    assert get_res.status_code == 200
    contacts = get_res.json()
    assert any(c["name"] == "Mom" for c in contacts)

    # 3. Delete contact
    del_res = client.delete(f"/api/v1/contacts/{c_id}")
    assert del_res.status_code == 204


def test_user_profile(client):
    # 1. Fetch default profile
    get_res = client.get("/api/v1/users/user_harshit")
    assert get_res.status_code == 200
    assert get_res.json()["uid"] == "user_harshit"

    # 2. Update profile
    update_data = {"name": "Harshit Updated", "emergency_contact": "+91 9123456780"}
    put_res = client.put("/api/v1/users/user_harshit", json=update_data)
    assert put_res.status_code == 200
    assert put_res.json()["name"] == "Harshit Updated"
    assert put_res.json()["emergency_contact"] == "+91 9123456780"
