/*
 * ============================================================================
 * GUARDIANLINK / SAFEGUARD - ESP32 WEARABLE FIRMWARE
 * ----------------------------------------------------------------------------
 * Hardware Components:
 * - ESP32 Development Board
 * - 0.96" I2C SSD1306 OLED (128x64) -> SDA: GPIO 21, SCL: GPIO 22
 * - MAX30102 Heart-Rate Sensor      -> SDA: GPIO 21, SCL: GPIO 22
 * - DHT22 Temperature & Humidity    -> DATA: GPIO 4
 * - Active Buzzer                   -> Signal: GPIO 25
 * - Common-Cathode RGB LED          -> Red: 14, Green: 32, Blue: 33
 * 
 * Networking:
 * - Local Wi-Fi + HTTP POST to GuardianLink FastAPI backend /api/v1/telemetry
 * ============================================================================
 */

#include <WiFi.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>
#include <ArduinoJson.h>
#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include "MAX30105.h"
#include "heartRate.h"
#include "DHT.h"
#include <math.h>
#include "soc/soc.h"
#include "soc/rtc_cntl_reg.h"

// ---------------- PIN DEFINITIONS --------------------
#define SDA_PIN 21
#define SCL_PIN 22

#define DHT_PIN 4
#define DHT_TYPE DHT22

#define BUZZER_PIN 25

#define RGB_RED_PIN 14
#define RGB_GREEN_PIN 32
#define RGB_BLUE_PIN 33

// ---------------- WI-FI & BACKEND CONFIG -------------
const char* WIFI_SSID     = "Phone";
const char* WIFI_PASSWORD = "123454321";

// Remote public Localtunnel endpoint
const char* SERVER_URL    = "https://guardianlink-harshit.loca.lt/api/v1/telemetry";
const char* DEVICE_ID     = "demo_device";

// ---------------- OLED DISPLAY -----------------------
#define SCREEN_WIDTH 128
#define SCREEN_HEIGHT 64
#define OLED_ADDR 0x3C

Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, -1);
bool oledReady = false;

// ---------------- SENSORS ----------------------------
DHT dht(DHT_PIN, DHT_TYPE);
MAX30105 max30102;
bool max30102Ready = false;

// ---------------- BUZZER -----------------------------
bool alarmActive = false;
unsigned long lastAlarmTime = 0;
const unsigned long ALARM_TOGGLE_INTERVAL = 200;

// ---------------- DHT22 -------------------------------
float dhtTemperature = 36.6;
float dhtHumidity = 50.0;
unsigned long lastDHTRead = 0;
const unsigned long DHT_INTERVAL = 2000;

// ---------------- RGB LED -----------------------------
bool sosActive = false;
const float TEMP_WARNING_HIGH = 38.0;
const float HUMIDITY_WARNING_HIGH = 85.0;

bool rgbBlinkState = false;
unsigned long lastRGBBlinkToggle = 0;
const unsigned long RGB_BLINK_INTERVAL = 150;

// ---------------- MAX30102 HEART RATE -----------------
const long IR_THRESHOLD = 20000;
bool fingerPresent = false;
bool fingerBeepDone = false;
long irValue = 0;

float bpm = 0;
float averageBPM = 0;

const byte RATE_SIZE = 5;
byte rates[RATE_SIZE] = {0};

byte rateSpot = 0;
byte rateCount = 0;
unsigned long lastBeat = 0;

// ---------------- OLED MULTI-PAGE CAROUSEL -----------
unsigned long lastPageSwitch = 0;
const unsigned long PAGE_INTERVAL = 3000;
int oledPage = 0;

// ---------------- TELEMETRY INTERVAL -----------------
unsigned long lastTelemetryTime = 0;
const unsigned long TELEMETRY_INTERVAL = 3000;

// =====================================================
// BUZZER BEEP
// =====================================================
void beep(int times, int onMs, int offMs)
{
  for (int i = 0; i < times; i++)
  {
    digitalWrite(BUZZER_PIN, HIGH);
    delay(onMs);
    digitalWrite(BUZZER_PIN, LOW);
    if (i < times - 1) delay(offMs);
  }
}

// =====================================================
// RGB LED HELPER (COMMON CATHODE)
// =====================================================
void setRGB(bool red, bool green, bool blue)
{
  digitalWrite(RGB_RED_PIN,   red   ? HIGH : LOW);
  digitalWrite(RGB_GREEN_PIN, green ? HIGH : LOW);
  digitalWrite(RGB_BLUE_PIN,  blue  ? HIGH : LOW);
}

void updateRGBStatus()
{
  // 1. High Fever or Tachycardia Alert: Blinking RED
  if (dhtTemperature >= TEMP_WARNING_HIGH || (averageBPM > 120 && fingerPresent))
  {
    if (millis() - lastRGBBlinkToggle >= RGB_BLINK_INTERVAL)
    {
      lastRGBBlinkToggle = millis();
      rgbBlinkState = !rgbBlinkState;
      setRGB(rgbBlinkState, false, false);
    }
  }
  // 2. Wi-Fi Connecting: BLUE
  else if (WiFi.status() != WL_CONNECTED)
  {
    setRGB(false, false, true);
  }
  // 3. Normal Healthy Operating: GREEN
  else
  {
    setRGB(false, true, false);
  }
}

// =====================================================
// RESET HEART RATE
// =====================================================
void resetHeartRate()
{
  bpm = 0;
  averageBPM = 0;
  lastBeat = 0;
  rateSpot = 0;
  rateCount = 0;

  for (byte i = 0; i < RATE_SIZE; i++)
  {
    rates[i] = 0;
  }
}

// =====================================================
// PROCESS MAX30102
// SparkFun checkForBeat() pulse detection
// =====================================================
void processHeartRate()
{
  if (!max30102Ready) return;

  // Read the latest IR sample
  irValue = max30102.getIR();

  // Check whether a finger is present
  if (irValue < IR_THRESHOLD)
  {
    resetHeartRate();
    return;
  }

  // Detect a heartbeat from the IR signal
  if (checkForBeat(irValue))
  {
    unsigned long now = millis();

    if (lastBeat != 0)
    {
      unsigned long delta = now - lastBeat;

      if (delta > 0)
      {
        float newBPM = 60000.0 / delta;

        // Accept plausible heart rates only
        if (newBPM >= 40 && newBPM <= 200)
        {
          bpm = newBPM;

          rates[rateSpot] = (byte)round(bpm);
          rateSpot = (rateSpot + 1) % RATE_SIZE;

          if (rateCount < RATE_SIZE)
          {
            rateCount++;
          }

          int sum = 0;
          for (byte i = 0; i < rateCount; i++)
          {
            sum += rates[i];
          }

          if (rateCount > 0)
          {
            averageBPM = (float)sum / rateCount;
          }

          Serial.print("[PULSE] Beat! Instant: ");
          Serial.print(bpm, 1);
          Serial.print(" BPM | Avg: ");
          Serial.print(averageBPM, 1);
          Serial.println(" BPM");
        }
      }
    }

    lastBeat = now;
  }
}

void handleFingerDetection()
{
  bool fingerNow = (irValue >= IR_THRESHOLD);

  if (fingerNow && !fingerPresent)
  {
    digitalWrite(BUZZER_PIN, HIGH);
    delay(80);
    digitalWrite(BUZZER_PIN, LOW);
    Serial.println("[MAX30102] Finger placed on sensor.");
  }

  fingerPresent = fingerNow;
}

// =====================================================
// READ DHT22
// =====================================================
void readDHT22()
{
  unsigned long now = millis();

  if (now - lastDHTRead >= DHT_INTERVAL)
  {
    lastDHTRead = now;

    float h = dht.readHumidity();
    float t = dht.readTemperature();

    if (!isnan(h) && !isnan(t) && t > 0.0 && t < 70.0)
    {
      dhtHumidity = h;
      dhtTemperature = t;
    }
  }
}

// =====================================================
// OLED DISPLAY CAROUSEL (PAGE ROTATION)
// =====================================================
void drawOLED()
{
  if (!oledReady) return;

  unsigned long now = millis();
  if (now - lastPageSwitch >= PAGE_INTERVAL)
  {
    lastPageSwitch = now;
    oledPage = (oledPage + 1) % 2; // Cycle between Page 0 and Page 1
  }

  display.clearDisplay();
  display.setTextColor(SSD1306_WHITE);

  // Top Title Bar
  display.setTextSize(1);
  display.setCursor(0, 0);
  display.print("SAFEGUARD");
  display.setCursor(68, 0);
  display.print(WiFi.status() == WL_CONNECTED ? "[SYNC]" : "[OFF]");

  display.drawLine(0, 9, 127, 9, SSD1306_WHITE);

  // PAGE 0: Heart Rate Focus
  if (oledPage == 0)
  {
    display.setCursor(0, 14);
    display.print("HEART RATE MONITOR");

    if (fingerPresent && averageBPM > 0)
    {
      display.setTextSize(3);
      display.setCursor(10, 26);
      display.print((int)round(averageBPM));
      display.setTextSize(1);
      display.setCursor(75, 36);
      display.print("BPM");

      display.setCursor(10, 54);
      display.print("Instant: ");
      display.print((int)round(bpm));
      display.print(" BPM");
    }
    else
    {
      display.setTextSize(2);
      display.setCursor(4, 28);
      display.print("NO FINGER");
      display.setTextSize(1);
      display.setCursor(4, 52);
      display.print("Touch MAX30102");
    }
  }
  // PAGE 1: Temperature & Climate
  else if (oledPage == 1)
  {
    display.setCursor(0, 14);
    display.print("CLIMATE & VITALS");

    display.setTextSize(2);
    display.setCursor(10, 26);
    display.print(dhtTemperature, 1);
    display.setTextSize(1);
    display.print(" ");
    display.setTextSize(2);
    display.print("C");

    display.setTextSize(1);
    display.setCursor(10, 52);
    display.print("Humidity: ");
    display.print((int)dhtHumidity);
    display.print("%");
  }

  display.display();
}

// =====================================================
// WI-FI CONNECTION
// =====================================================
void connectToWiFi()
{
  Serial.print("\n[Wi-Fi] Connecting to: ");
  Serial.println(WIFI_SSID);

  setRGB(false, false, true); // Blue while connecting

  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  int tries = 0;
  while (WiFi.status() != WL_CONNECTED && tries < 20)
  {
    delay(400);
    Serial.print(".");
    tries++;
  }

  if (WiFi.status() == WL_CONNECTED)
  {
    Serial.println("\n[Wi-Fi] Connected successfully!");
    Serial.print("[Wi-Fi] Local IP: ");
    Serial.println(WiFi.localIP());

    setRGB(false, true, false); // Green
    beep(2, 60, 50);            // 2 cheerful beeps
  }
  else
  {
    Serial.println("\n[Wi-Fi] Connection failed. Will retry in loop.");
    setRGB(true, false, false); // Red
  }
}

// =====================================================
// SEND TELEMETRY TO GUARDIANLINK BACKEND
// =====================================================
void sendTelemetry()
{
  if (WiFi.status() != WL_CONNECTED)
  {
    connectToWiFi();
    if (WiFi.status() != WL_CONNECTED) return;
  }

  HTTPClient http;
  WiFiClient client;
  WiFiClientSecure secureClient;

  if (String(SERVER_URL).startsWith("https://"))
  {
    secureClient.setInsecure(); // Required for HTTPS tunnels (Pinggy / Ngrok / Localtunnel)
    http.begin(secureClient, SERVER_URL);
  }
  else
  {
    http.begin(client, SERVER_URL);
  }

  http.addHeader("Content-Type", "application/json");
  http.addHeader("Bypass-Tunnel-Reminder", "true"); // Allows localtunnel to bypass splash page

  // Format telemetry payload
  StaticJsonDocument<384> doc;
  doc["device_id"]   = DEVICE_ID;
  doc["heart_rate"]  = (fingerPresent && averageBPM > 0) ? (int)round(averageBPM) : 72;
  doc["temperature"] = serialized(String(dhtTemperature, 1));
  doc["battery"]     = "95%";
  doc["network"]     = "Local Wi-Fi";
  doc["sos"]         = sosActive;

  String jsonPayload;
  serializeJson(doc, jsonPayload);

  Serial.print("[HTTP POST] Sending Vitals: ");
  Serial.println(jsonPayload);

  int httpCode = http.POST(jsonPayload);

  if (httpCode > 0)
  {
    Serial.printf("[HTTP] Server Response: %d\n", httpCode);
  }
  else
  {
    Serial.printf("[HTTP] Error sending: %s\n", http.errorToString(httpCode).c_str());
  }

  http.end();
}

// =====================================================
// SETUP
// =====================================================
void setup()
{
  WRITE_PERI_REG(RTC_CNTL_BROWN_OUT_REG, 0); // Disable brownout detector to prevent reboot loop from USB voltage dips
  Serial.begin(115200);
  delay(1500); // Allow USB Serial Monitor buffer to stabilize
  Serial.println("\n");
  Serial.println("=============================================");
  Serial.println("     GuardianLink / SAFEGUARD ESP32 Node     ");
  Serial.println("=============================================");
  Serial.flush();

  // Initialize Pins
  pinMode(BUZZER_PIN, OUTPUT);
  pinMode(RGB_RED_PIN, OUTPUT);
  pinMode(RGB_GREEN_PIN, OUTPUT);
  pinMode(RGB_BLUE_PIN, OUTPUT);

  digitalWrite(BUZZER_PIN, LOW);
  setRGB(false, false, true); // Blue while booting
  Serial.println("[1/4] GPIO Pins initialized.");
  Serial.flush();

  // Initialize I2C Bus with timeout protection to prevent hanging if wires are loose
  Wire.begin(SDA_PIN, SCL_PIN);
  Wire.setTimeOut(1000); // 1-second timeout prevents I2C bus lockup
  Serial.println("[2/4] I2C Bus started (SDA: 21, SCL: 22).");
  Serial.flush();

  // Initialize SSD1306 OLED
  Serial.print("[3/4] Initializing OLED SSD1306... ");
  if (display.begin(SSD1306_SWITCHCAPVCC, OLED_ADDR))
  {
    oledReady = true;
    display.clearDisplay();
    display.setTextSize(1);
    display.setTextColor(SSD1306_WHITE);
    display.setCursor(15, 20);
    display.println("SAFEGUARD IoT");
    display.setCursor(15, 36);
    display.println("Starting up...");
    display.display();
    Serial.println("OK!");
  }
  else
  {
    Serial.println("FAILED (Screen at 0x3C not found or wiring loose - continuing...)");
  }
  Serial.flush();

  // Initialize DHT22
  dht.begin();
  Serial.println("[4/4] DHT22 initialized on GPIO 4.");
  Serial.flush();

  // Initialize MAX30102 Heart-Rate Sensor
  Serial.print("[INIT] Initializing MAX30102... ");
  if (max30102.begin(Wire, I2C_SPEED_FAST))
  {
    max30102Ready = true;
    max30102.setup();                    // Default configuration
    max30102.setPulseAmplitudeRed(0x0A); // Low red amplitude
    max30102.setPulseAmplitudeGreen(0);
    Serial.println("OK!");
  }
  else
  {
    Serial.println("FAILED (MAX30102 not found on I2C - continuing with simulated vitals...)");
  }
  Serial.flush();

  // Connect to Wi-Fi
  connectToWiFi();
}

// =====================================================
// MAIN LOOP
// =====================================================
void loop()
{
  // 1. Continuous pulse beat sampling (must run rapidly)
  processHeartRate();
  handleFingerDetection();

  // 2. Periodic DHT22 sensor readings
  readDHT22();

  // 3. Update OLED Carousel Display
  drawOLED();

  // 4. Update RGB status LED
  updateRGBStatus();

  // 5. Periodic telemetry transmission to GuardianLink backend
  unsigned long now = millis();
  if (now - lastTelemetryTime >= TELEMETRY_INTERVAL)
  {
    lastTelemetryTime = now;
    sendTelemetry();
  }
}
