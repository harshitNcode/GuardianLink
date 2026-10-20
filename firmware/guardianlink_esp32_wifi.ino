/*
 * GuardianLink - ESP32 Wearable Device Firmware (Local Wi-Fi Edition)
 * 
 * Hardware:
 * - ESP32 Development Board (ESP32-WROOM / ESP32-WROVER)
 * - GPS Module (NEO-6M / NEO-7M / NEO-8M) on UART (TX -> RX2: GPIO 16, RX -> TX2: GPIO 17)
 * - Emergency SOS Push Button (GPIO 4, with internal pull-up)
 * - Status LED (GPIO 2, onboard LED)
 *
 * Networking:
 * - Local Wi-Fi (SSID & Password)
 * - Sends HTTP POST to GuardianLink FastAPI backend /api/v1/telemetry
 */

#include <WiFi.h>
#include <HTTPClient.h>
#include <HardwareSerial.h>
#include <ArduinoJson.h> // Library: ArduinoJson by Benoit Blanchon (v6 or v7)

// ======================== CONFIGURATION ========================
// 1. Wi-Fi Credentials
const char* WIFI_SSID     = "YOUR_WIFI_SSID";
const char* WIFI_PASSWORD = "YOUR_WIFI_PASSWORD";

// 2. GuardianLink Backend Server IP & Port
// (Use your PC's local Wi-Fi IPv4 address, e.g., "http://10.239.35.109:8000")
const char* SERVER_URL    = "http://10.239.35.109:8000/api/v1/telemetry";

// 3. Device Identification
const char* DEVICE_ID     = "demo_device";

// 4. Pin Definitions
#define SOS_BUTTON_PIN    4     // Push button for Emergency SOS
#define STATUS_LED_PIN    2     // Onboard LED indicator
#define GPS_RX_PIN        16    // ESP32 RX2 connected to GPS TX
#define GPS_TX_PIN        17    // ESP32 TX2 connected to GPS RX
// ===============================================================

HardwareSerial gpsSerial(2);

// Telemetry state
float currentLatitude  = 28.6139; // Default fallback (e.g. Delhi center)
float currentLongitude = 77.2090;
int batteryPercentage  = 95;
bool isSosActive       = false;

unsigned long lastTelemetryTime = 0;
const unsigned long TELEMETRY_INTERVAL = 3000; // Send telemetry every 3 seconds

void connectToWiFi() {
  Serial.print("Connecting to Wi-Fi: ");
  Serial.println(WIFI_SSID);
  
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  int retries = 0;
  while (WiFi.status() != WL_CONNECTED && retries < 20) {
    delay(500);
    Serial.print(".");
    digitalWrite(STATUS_LED_PIN, !digitalRead(STATUS_LED_PIN)); // Blink while connecting
    retries++;
  }

  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("\n[Wi-Fi] Connected successfully!");
    Serial.print("[Wi-Fi] IP Address: ");
    Serial.println(WiFi.localIP());
    Serial.print("[Wi-Fi] RSSI Signal: ");
    Serial.println(WiFi.RSSI());
    digitalWrite(STATUS_LED_PIN, HIGH); // Solid ON when connected
  } else {
    Serial.println("\n[Wi-Fi] Connection failed. Will retry in loop.");
    digitalWrite(STATUS_LED_PIN, LOW);
  }
}

void parseGpsNmea() {
  while (gpsSerial.available() > 0) {
    char c = gpsSerial.read();
    // Simple NMEA sentence parsing or TinyGPS++ can be hooked here
    // If using TinyGPS++, gps.encode(c) updates currentLatitude & currentLongitude
  }
}

void checkSosButton() {
  // Active LOW with internal pull-up
  if (digitalRead(SOS_BUTTON_PIN) == LOW) {
    delay(50); // Debounce
    if (digitalRead(SOS_BUTTON_PIN) == LOW) {
      if (!isSosActive) {
        isSosActive = true;
        Serial.println("\n[EMERGENCY] SOS Button Pressed! Sending immediate alert...");
        sendTelemetry(true);
      }
    }
  } else {
    isSosActive = false;
  }
}

void sendTelemetry(bool isEmergency) {
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("[HTTP] Wi-Fi not connected. Attempting reconnection...");
    connectToWiFi();
    if (WiFi.status() != WL_CONNECTED) return;
  }

  HTTPClient http;
  http.begin(SERVER_URL);
  http.addHeader("Content-Type", "application/json");

  // Construct JSON payload
  StaticJsonDocument<256> doc;
  doc["device_id"] = DEVICE_ID;
  doc["latitude"]  = currentLatitude;
  doc["longitude"] = currentLongitude;
  doc["battery"]   = String(batteryPercentage) + "%";
  doc["network"]   = "Local Wi-Fi";
  doc["sos"]       = isEmergency || isSosActive;

  String jsonPayload;
  serializeJson(doc, jsonPayload);

  Serial.print("[HTTP POST] Sending: ");
  Serial.println(jsonPayload);

  int httpResponseCode = http.POST(jsonPayload);

  if (httpResponseCode > 0) {
    Serial.print("[HTTP] Server Response Code: ");
    Serial.println(httpResponseCode);
    String response = http.getString();
    Serial.print("[HTTP] Response Body: ");
    Serial.println(response);
  } else {
    Serial.print("[HTTP] Error sending POST: ");
    Serial.println(http.errorToString(httpResponseCode).c_str());
  }

  http.end();
}

void setup() {
  Serial.begin(115200);
  delay(1000);
  Serial.println("\n=======================================");
  Serial.println("  GuardianLink ESP32 (Local Wi-Fi)    ");
  Serial.println("=======================================");

  pinMode(SOS_BUTTON_PIN, INPUT_PULLUP);
  pinMode(STATUS_LED_PIN, OUTPUT);
  digitalWrite(STATUS_LED_PIN, LOW);

  gpsSerial.begin(9600, SERIAL_8N1, GPS_RX_PIN, GPS_TX_PIN);

  connectToWiFi();
}

void loop() {
  parseGpsNmea();
  checkSosButton();

  // Periodic telemetry update
  unsigned long now = millis();
  if (now - lastTelemetryTime >= TELEMETRY_INTERVAL) {
    lastTelemetryTime = now;
    sendTelemetry(isSosActive);
  }
}
