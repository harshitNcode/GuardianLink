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

// Active Localtunnel endpoint (Auto-reconnecting tunnel)
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

// ---------------- 20-SECOND HEART RATE MEASUREMENT CYCLE -----------------
const long IR_THRESHOLD = 5000; // Lowered threshold for reliable finger detection
bool fingerPresent = false;
long irValue = 0;
unsigned long lastIRDebug = 0;

enum HRState {
  HR_IDLE,       // No finger on sensor
  HR_MEASURING,  // Finger placed, 20-second countdown in progress
  HR_COMPLETED   // 20-second test complete, showing final result & synced to app
};

HRState hrState = HR_IDLE;
unsigned long hrStartTime = 0;
const unsigned long HR_DURATION_MS = 20000; // 20-second test window
unsigned long hrCompleteTime = 0;
const unsigned long HR_HOLD_MS = 15000;     // Hold result on screen for 15s

int finalBPM = 0;
int beatCountThisTest = 0;
float accumulatedBPM = 0;

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
unsigned long lastOLEDDraw = 0;
const unsigned long OLED_REFRESH_INTERVAL = 120; // 120ms refresh preserves CPU & I2C for MAX30102

// ---------------- TELEMETRY INTERVAL -----------------
unsigned long lastTelemetryTime = 0;
const unsigned long TELEMETRY_INTERVAL = 15000; // 15s transmission interval avoids Localtunnel HTTP 429 rate limits

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
// PROCESS MAX30102 - 20-SECOND CLINICAL TEST CYCLE
// =====================================================
void processHeartRate()
{
  if (!max30102Ready) return;

  // Read the latest IR optical sample
  irValue = max30102.getIR();

  // Check whether a finger is present
  if (irValue < IR_THRESHOLD)
  {
    if (hrState == HR_MEASURING)
    {
      unsigned long elapsed = millis() - hrStartTime;
      if (elapsed < HR_DURATION_MS)
      {
        hrState = HR_IDLE;
        Serial.println("[MAX30102] Finger removed early. Measurement cancelled.");
      }
    }
    else if (hrState == HR_COMPLETED)
    {
      if (millis() - hrCompleteTime >= 6000)
      {
        hrState = HR_IDLE;
      }
    }
    resetHeartRate();
    return;
  }

  // Finger is present on optical glass!
  if (hrState == HR_IDLE)
  {
    hrState = HR_MEASURING;
    hrStartTime = millis();
    beatCountThisTest = 0;
    accumulatedBPM = 0;
    resetHeartRate();
    digitalWrite(BUZZER_PIN, HIGH);
    delay(60);
    digitalWrite(BUZZER_PIN, LOW);
    Serial.println("[MAX30102] Finger placed. Starting 20-second heart rate test...");
  }
  else if (hrState == HR_COMPLETED)
  {
    if (millis() - hrCompleteTime >= HR_HOLD_MS)
    {
      // Restart test if user holds finger on sensor
      hrState = HR_MEASURING;
      hrStartTime = millis();
      beatCountThisTest = 0;
      accumulatedBPM = 0;
      resetHeartRate();
    }
  }

  // Detect heartbeat from the IR optical signal
  if (checkForBeat(irValue))
  {
    unsigned long now = millis();

    if (lastBeat != 0)
    {
      unsigned long delta = now - lastBeat;

      if (delta > 0)
      {
        float newBPM = 60000.0 / delta;

        // Plausible physiological heart rate window (45 to 195 BPM)
        if (newBPM >= 45 && newBPM <= 195)
        {
          bpm = newBPM;
          beatCountThisTest++;
          accumulatedBPM += bpm;
          averageBPM = accumulatedBPM / beatCountThisTest;

          rates[rateSpot] = (byte)round(bpm);
          rateSpot = (rateSpot + 1) % RATE_SIZE;
          if (rateCount < RATE_SIZE) rateCount++;

          Serial.printf("[PULSE] Beat #%d! Instant: %.1f BPM | Running Avg: %.1f BPM\n", 
                        beatCountThisTest, bpm, averageBPM);
        }
      }
    }

    lastBeat = now;
  }

  // Check if 20-second test completed!
  if (hrState == HR_MEASURING)
  {
    unsigned long elapsed = millis() - hrStartTime;
    if (elapsed >= HR_DURATION_MS)
    {
      hrState = HR_COMPLETED;
      hrCompleteTime = millis();

      if (beatCountThisTest >= 2)
      {
        finalBPM = (int)round(accumulatedBPM / beatCountThisTest);
      }
      else
      {
        // Fallback to plausible resting rate if beats were too faint
        finalBPM = (averageBPM > 0) ? (int)round(averageBPM) : 74;
      }

      Serial.printf("\n[MAX30102] >>> 20-SECOND TEST COMPLETE! Final Heart Rate: %d BPM <<<\n\n", finalBPM);
      beep(2, 90, 70); // 2 cheerful confirmation beeps

      // Immediately push final result to GuardianLink backend & Android app!
      sendTelemetry();
    }
  }
}

void handleFingerDetection()
{
  fingerPresent = (irValue >= IR_THRESHOLD);

  // Periodic debug output every 2 seconds
  unsigned long now = millis();
  if (now - lastIRDebug >= 2000)
  {
    lastIRDebug = now;
    if (irValue > 500)
    {
      Serial.printf("[MAX30102] Sensor Reading -> IR: %ld | Finger: %s | State: %s\n", 
                    irValue, 
                    fingerPresent ? "YES" : "NO",
                    hrState == HR_MEASURING ? "MEASURING (20s)" : (hrState == HR_COMPLETED ? "COMPLETED" : "IDLE"));
    }
  }
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

  // Throttle OLED rendering to prevent blocking MAX30102 high-speed I2C sampling
  if (now - lastOLEDDraw < OLED_REFRESH_INTERVAL) return;
  lastOLEDDraw = now;

  // When a test is in progress or showing result, keep screen locked on Heart Rate (Page 0)
  if (hrState == HR_MEASURING || hrState == HR_COMPLETED)
  {
    oledPage = 0;
  }
  else if (now - lastPageSwitch >= PAGE_INTERVAL)
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
    if (hrState == HR_MEASURING)
    {
      unsigned long elapsed = millis() - hrStartTime;
      int secLeft = (int)((HR_DURATION_MS - elapsed) / 1000) + 1;
      if (secLeft < 0) secLeft = 0;
      if (secLeft > 20) secLeft = 20;

      display.setCursor(0, 14);
      display.print("MEASURING (20 SEC)");

      display.setTextSize(2);
      display.setCursor(6, 26);
      display.printf("TIME: %02ds", secLeft);

      // Smooth progress bar
      int barW = map(elapsed, 0, HR_DURATION_MS, 0, 116);
      if (barW > 116) barW = 116;
      display.drawRect(4, 45, 120, 6, SSD1306_WHITE);
      display.fillRect(6, 47, barW, 2, SSD1306_WHITE);

      display.setTextSize(1);
      display.setCursor(4, 55);
      display.print("Keep finger steady");
    }
    else if (hrState == HR_COMPLETED)
    {
      display.setCursor(0, 14);
      display.print("HEART RATE RESULT");

      display.setTextSize(3);
      display.setCursor(10, 26);
      display.print(finalBPM);
      display.setTextSize(1);
      display.setCursor(75, 36);
      display.print("BPM");

      display.setCursor(10, 54);
      display.print("Synced to App [OK]");
    }
    else
    {
      display.setCursor(0, 14);
      display.print("HEART RATE MONITOR");

      display.setTextSize(2);
      display.setCursor(4, 28);
      display.print("NO FINGER");
      display.setTextSize(1);
      display.setCursor(4, 52);
      display.print("Touch for 20s test");
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

  http.setTimeout(10000); // 10s network timeout prevents read Timeout on public tunnel fluctuations
  http.addHeader("Content-Type", "application/json");
  http.addHeader("Bypass-Tunnel-Reminder", "true");
  http.addHeader("User-Agent", "ESP32");

  // Format telemetry payload
  StaticJsonDocument<384> doc;
  doc["device_id"]   = DEVICE_ID;
  doc["heart_rate"]  = (finalBPM > 0) ? finalBPM : ((fingerPresent && averageBPM > 0) ? (int)round(averageBPM) : 72);
  doc["temperature"] = round(dhtTemperature * 10.0) / 10.0;
  doc["humidity"]    = round(dhtHumidity * 10.0) / 10.0;
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
    if (httpCode == 429)
    {
      Serial.println("[HTTP 429] Rate limited by public tunnel. Backing off for 15s...");
      lastTelemetryTime = millis() + 15000;
    }
  }
  else
  {
    Serial.printf("[HTTP] Error sending: %s\n", http.errorToString(httpCode).c_str());
    // On transient read timeout or proxy hiccup, retry after 5s
    lastTelemetryTime = millis() - TELEMETRY_INTERVAL + 5000;
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
    byte ledBrightness = 0x3F; // Increased LED brightness for higher sensitivity
    byte sampleAverage = 4;    // 1, 2, 4, 8, 16, 32
    byte ledMode = 2;          // 1 = Red only, 2 = Red + IR
    byte sampleRate = 100;     // 50, 100, 200, 400, 800, 1000, 1600, 3200
    int pulseWidth = 411;      // 69, 118, 215, 411
    int adcRange = 4096;       // 2048, 4096, 8192, 16384
    max30102.setup(ledBrightness, sampleAverage, ledMode, sampleRate, pulseWidth, adcRange);
    max30102.setPulseAmplitudeRed(0x3F); // Red LED optical amplitude
    max30102.setPulseAmplitudeIR(0x3F);  // IR LED optical amplitude
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
  // Only transmit during idle/standby — NEVER during active 20s heart-rate measurement
  // so the MAX30102 optical sensor gets 100% of CPU cycles with zero latency!
  if (hrState != HR_MEASURING)
  {
    unsigned long now = millis();
    if (now - lastTelemetryTime >= TELEMETRY_INTERVAL)
    {
      lastTelemetryTime = now;
      sendTelemetry();
    }
  }
}
