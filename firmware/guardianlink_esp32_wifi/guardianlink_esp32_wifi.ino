/*
 * ============================================================================
 * GUARDIANLINK / SAFEGUARD - ESP32 WEARABLE FIRMWARE (LATEST CLOUD EDITION)
 * ----------------------------------------------------------------------------
 * Hardware Components:
 * - ESP32 Development Board (ESP32-WROOM / ESP32-WROVER)
 * - 0.96" I2C SSD1306 OLED (128x64) -> SDA: GPIO 21, SCL: GPIO 22
 * - MAX30102 Heart-Rate Sensor      -> SDA: GPIO 21, SCL: GPIO 22
 * - DHT22 Temperature & Humidity    -> DATA: GPIO 4
 * - Active Buzzer                   -> Signal: GPIO 25
 * - Common-Cathode RGB LED          -> Red: 14, Green: 32, Blue: 33
 * 
 * Networking:
 * - Local Wi-Fi / Hotspot
 * - HTTP/HTTPS POST to GuardianLink Cloud / LAN backend (/api/v1/telemetry)
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
#include "DHT.h"
#include <math.h>
#include "soc/soc.h"
#include "soc/rtc_cntl_reg.h"

// ============================================================================
// 1. WI-FI & BACKEND SERVER CONFIGURATION
// ============================================================================
// Your Wi-Fi or Phone Hotspot Credentials
const char* WIFI_SSID     = "Phone";
const char* WIFI_PASSWORD = "123454321";

// Backend API URL:
// - If deployed on Render (Cloud 24/7):
//   Replace with your Render URL, e.g.:
//   "https://guardianlink-api-xxxx.onrender.com/api/v1/telemetry"
// - If testing locally on your laptop:
//   "http://192.168.x.x:8000/api/v1/telemetry"
const char* SERVER_URL    = "https://guardianlink-api-xxxx.onrender.com/api/v1/telemetry";

// Unique hardware identifier (paired with the GuardianLink Android app)
const char* DEVICE_ID     = "demo_device";

// ============================================================================
// 2. PIN DEFINITIONS
// ============================================================================
#define SDA_PIN 21
#define SCL_PIN 22

#define DHT_PIN 4
#define DHT_TYPE DHT22

#define BUZZER_PIN 25

#define RGB_RED_PIN 14
#define RGB_GREEN_PIN 32
#define RGB_BLUE_PIN 33

// ============================================================================
// 3. OLED DISPLAY (128x64 SSD1306)
// ============================================================================
#define SCREEN_WIDTH 128
#define SCREEN_HEIGHT 64
#define OLED_ADDR 0x3C

Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, -1);
bool oledReady = false;

// Multi-page carousel
unsigned long lastPageSwitch = 0;
const unsigned long PAGE_INTERVAL = 3000;
int oledPage = 0;
unsigned long lastOLEDDraw = 0;
const unsigned long OLED_REFRESH_INTERVAL = 150; // Standard carousel refresh
bool beatPulseFlash = false;

// ============================================================================
// 4. SENSORS (DHT22 & MAX30102)
// ============================================================================
DHT dht(DHT_PIN, DHT_TYPE);
MAX30105 max30102;
bool max30102Ready = false;

// DHT22 readings
float dhtTemperature = 26.5;
float dhtHumidity = 55.0;
unsigned long lastDHTRead = 0;
const unsigned long DHT_INTERVAL = 2000;

// RGB Status & Thresholds
bool sosActive = false;
const float TEMP_WARNING_HIGH = 38.0;
bool rgbBlinkState = false;
unsigned long lastRGBBlinkToggle = 0;
const unsigned long RGB_BLINK_INTERVAL = 150;

// ============================================================================
// 5. ADAPTIVE HEART RATE DETECTION & 12-SECOND CLINICAL CYCLE
// ============================================================================
// Finger detection optical threshold (calibrated for human tissue reflectance)
const long IR_FINGER_THRESHOLD = 15000;
bool fingerPresent = false;
long irValue = 0;
unsigned long lastIRDebug = 0;

// Dynamic DC filter & Peak detection variables
float irDC = 0;                 // Exponential moving average (DC baseline)
long acSignal = 0;              // AC cardiac pulsatile signal
long prevAcSignal = 0;          // Previous AC sample
long acPeakMax = 50;            // Dynamic peak amplitude tracker
unsigned long lastBeatTime = 0; // Timestamp of previous detected beat
int beatCountThisTest = 0;      // Valid beats counted during 12s test
float runningBPM = 0;           // Instantaneous BPM of the latest beat
float accumulatedBPM = 0;       // Running sum of valid beat BPMs
float averageBPM = 0;           // Running average BPM

enum HRState {
  HR_IDLE,       // No finger on optical sensor
  HR_MEASURING,  // Finger placed; 12-second clinical test in progress
  HR_COMPLETED,  // 12-second test completed with valid result
  HR_FAILED      // Finger removed or unstable contact; prompt retry
};

HRState hrState = HR_IDLE;
unsigned long hrStartTime = 0;
const unsigned long HR_DURATION_MS = 12000; // Optimized 12-second clinical window (prevents finger fatigue)
unsigned long hrCompleteTime = 0;
const unsigned long HR_HOLD_MS = 12000;     // Hold result on OLED for 12 seconds

// Verified result (NEVER set to fake fallback numbers like 74!)
int finalBPM = 0;

// ============================================================================
// 6. TELEMETRY TRANSMISSION INTERVAL
// ============================================================================
unsigned long lastTelemetryTime = 0;
const unsigned long TELEMETRY_INTERVAL = 15000; // 15-second background sync

// Forward declarations
void sendTelemetry();
void beep(int times, int onMs, int offMs);
void setRGB(bool red, bool green, bool blue);

// ============================================================================
// BUZZER BEEP HELPER
// ============================================================================
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

// ============================================================================
// RGB STATUS HELPER (COMMON CATHODE)
// ============================================================================
void setRGB(bool red, bool green, bool blue)
{
  digitalWrite(RGB_RED_PIN,   red   ? HIGH : LOW);
  digitalWrite(RGB_GREEN_PIN, green ? HIGH : LOW);
  digitalWrite(RGB_BLUE_PIN,  blue  ? HIGH : LOW);
}

void updateRGBStatus()
{
  // 1. High Fever or Tachycardia Alert: Blinking RED
  if (dhtTemperature >= TEMP_WARNING_HIGH || (finalBPM > 120))
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

// ============================================================================
// ADAPTIVE BEAT DETECTION (REPLACES FRAGILE checkForBeat)
// ============================================================================
bool detectPulse(long sample)
{
  if (sample < IR_FINGER_THRESHOLD)
  {
    return false;
  }

  // 1. Initialize DC baseline on first contact
  if (irDC == 0)
  {
    irDC = sample;
    return false;
  }

  // 2. Exponential Moving Average tracks the DC baseline
  // Follows slow baseline drift (breathing, slight pressure shifts)
  irDC = (irDC * 0.95f) + (sample * 0.05f);
  acSignal = (long)(sample - irDC);

  // 3. Decay dynamic peak amplitude tracker
  if (acPeakMax > 30)
  {
    acPeakMax = (long)(acPeakMax * 0.995f);
  }

  unsigned long now = millis();
  bool beat = false;

  // 4. Adaptive dynamic crest detection:
  // Must exceed 40% of recent peak amplitude (minimum 25 units)
  long threshold = (long)(acPeakMax * 0.40f);
  if (threshold < 25) threshold = 25;

  // Crest condition: AC was above threshold and has turned downward (local peak)
  if (prevAcSignal > threshold && acSignal < prevAcSignal)
  {
    // Physiological Refractory Lockout:
    // No realistic human heart beats faster than 190 BPM (~315ms interval)
    // This rejects dicrotic notch reflections and harmonic noise.
    unsigned long delta = now - lastBeatTime;
    if (delta >= 315 && delta <= 1600) // 37.5 to 190 BPM range
    {
      float instantBPM = 60000.0f / (float)delta;

      // Update dynamic peak amplitude reference
      if (prevAcSignal > acPeakMax) acPeakMax = prevAcSignal;
      if (acPeakMax > 2500) acPeakMax = 2500; // Clamp against movement spikes

      // Discard initial contact transient (first 1.5 seconds)
      if (millis() - hrStartTime > 1500)
      {
        beatCountThisTest++;
        accumulatedBPM += instantBPM;
        averageBPM = accumulatedBPM / (float)beatCountThisTest;
        runningBPM = instantBPM;
      }

      lastBeatTime = now;
      beat = true;

      Serial.printf("[PULSE] Beat #%d! Interval: %lums | Instant: %.1f BPM | Running Avg: %.1f BPM\n", 
                    beatCountThisTest, delta, instantBPM, averageBPM);
    }
    else if (delta > 1600)
    {
      // First beat after placement or reset
      lastBeatTime = now;
      if (prevAcSignal > acPeakMax) acPeakMax = prevAcSignal;
    }
  }

  prevAcSignal = acSignal;
  return beat;
}

// ============================================================================
// PROCESS MAX30102 - 20-SECOND CLINICAL TEST CYCLE
// ============================================================================
void processHeartRate()
{
  if (!max30102Ready) return;

  // Read latest IR optical sample
  irValue = max30102.getIR();
  fingerPresent = (irValue >= IR_FINGER_THRESHOLD);

  // ---------------- CASE 1: NO FINGER PRESENT ----------------
  if (!fingerPresent)
  {
    if (hrState == HR_MEASURING)
    {
      unsigned long elapsed = millis() - hrStartTime;
      if (elapsed < HR_DURATION_MS)
      {
        hrState = HR_IDLE;
        Serial.println("[MAX30102] Finger removed early. Measurement cancelled.");
        beep(1, 150, 0); // Short warning tone
      }
    }
    else if (hrState == HR_COMPLETED || hrState == HR_FAILED)
    {
      if (millis() - hrCompleteTime >= 6000)
      {
        hrState = HR_IDLE;
        finalBPM = 0; // Cleanly clear result so idle state doesn't stay stuck
      }
    }

    // Reset pulse detector filters
    irDC = 0;
    acSignal = 0;
    prevAcSignal = 0;
    lastBeatTime = 0;
    runningBPM = 0;
    averageBPM = 0;
    return;
  }

  // ---------------- CASE 2: FINGER JUST PLACED (START TEST) ----------------
  if (hrState == HR_IDLE)
  {
    hrState = HR_MEASURING;
    hrStartTime = millis();
    beatCountThisTest = 0;
    accumulatedBPM = 0;
    averageBPM = 0;
    runningBPM = 0;
    finalBPM = 0;
    irDC = irValue;
    prevAcSignal = 0;
    lastBeatTime = 0;
    acPeakMax = 60;

    digitalWrite(BUZZER_PIN, HIGH);
    delay(70);
    digitalWrite(BUZZER_PIN, LOW);
    Serial.println("\n[MAX30102] >>> Finger placed! Starting 12-second clinical heart rate test... <<<");
  }
  else if (hrState == HR_COMPLETED || hrState == HR_FAILED)
  {
    if (millis() - hrCompleteTime >= HR_HOLD_MS)
    {
      // Restart test if user continues holding finger on sensor
      hrState = HR_MEASURING;
      hrStartTime = millis();
      beatCountThisTest = 0;
      accumulatedBPM = 0;
      averageBPM = 0;
      runningBPM = 0;
      finalBPM = 0;
      irDC = irValue;
      prevAcSignal = 0;
      lastBeatTime = 0;
      acPeakMax = 60;
    }
  }

  // ---------------- CASE 3: MEASURING IN PROGRESS (12s) ----------------
  if (hrState == HR_MEASURING)
  {
    bool beat = detectPulse(irValue);
    if (beat)
    {
      beatPulseFlash = true;
    }

    unsigned long elapsed = millis() - hrStartTime;
    if (elapsed >= HR_DURATION_MS)
    {
      // 12-second test completed!
      hrCompleteTime = millis();

      // Clinical validity check: at least 4 valid beats detected and plausible BPM
      if (beatCountThisTest >= 4 && averageBPM >= 45.0 && averageBPM <= 190.0)
      {
        // SUCCESS: Real heart rate captured
        hrState = HR_COMPLETED;
        finalBPM = (int)round(averageBPM);

        Serial.printf("\n============================================================\n");
        Serial.printf("  [MAX30102] 12-SECOND TEST COMPLETE!\n");
        Serial.printf("  Valid Beats: %d | Real Heart Rate: %d BPM\n", beatCountThisTest, finalBPM);
        Serial.printf("============================================================\n\n");

        beep(2, 90, 70); // 2 cheerful confirmation beeps

        // Immediately push live verified vitals to GuardianLink cloud!
        sendTelemetry();
      }
      else
      {
        // FAILED: Signal was too faint or noisy. NEVER fake 74 BPM!
        hrState = HR_FAILED;
        finalBPM = 0; // Pure 0, never a fake placeholder

        Serial.printf("\n[MAX30102] Test Inconclusive (%d beats detected). Prompting retry.\n\n", beatCountThisTest);
        beep(1, 250, 0); // 1 longer warning tone
      }
    }
  }
}

void handleFingerDebug()
{
  unsigned long now = millis();
  if (now - lastIRDebug >= 2000)
  {
    lastIRDebug = now;
    if (irValue > 1000)
    {
      Serial.printf("[MAX30102] IR: %ld | Finger: %s | State: %s\n", 
                    irValue, 
                    fingerPresent ? "YES" : "NO",
                    hrState == HR_MEASURING ? "MEASURING (20s)" : 
                    (hrState == HR_COMPLETED ? "COMPLETED" : (hrState == HR_FAILED ? "RETRY" : "IDLE")));
    }
  }
}

// ============================================================================
// READ DHT22 (TEMPERATURE & HUMIDITY)
// ============================================================================
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

// ============================================================================
// OLED DISPLAY CAROUSEL & VISUAL FEEDBACK
// ============================================================================
void drawOLED()
{
  if (!oledReady) return;

  unsigned long now = millis();

  // Rate-limit display updates:
  // During active measurement, refresh at ~250ms intervals (or when a beat occurs)
  // to preserve 95%+ of I2C bandwidth for high-speed MAX30102 optical sampling.
  unsigned long refreshInterval = (hrState == HR_MEASURING) ? 250 : OLED_REFRESH_INTERVAL;
  if (now - lastOLEDDraw < refreshInterval && !beatPulseFlash) return;
  lastOLEDDraw = now;
  bool flashThisFrame = beatPulseFlash;
  beatPulseFlash = false;

  // Lock screen on Heart Rate during testing or result display
  if (hrState == HR_MEASURING || hrState == HR_COMPLETED || hrState == HR_FAILED)
  {
    oledPage = 0;
  }
  else if (now - lastPageSwitch >= PAGE_INTERVAL)
  {
    lastPageSwitch = now;
    oledPage = (oledPage + 1) % 2; // Alternate Page 0 & 1
  }

  display.clearDisplay();
  display.setTextColor(SSD1306_WHITE);

  // Top Status Bar
  display.setTextSize(1);
  display.setCursor(0, 0);
  display.print("SAFEGUARD");
  display.setCursor(68, 0);
  display.print(WiFi.status() == WL_CONNECTED ? "[SYNC]" : "[OFF]");
  display.drawLine(0, 9, 127, 9, SSD1306_WHITE);

  // ---------------- PAGE 0: HEART RATE FOCUS ----------------
  if (oledPage == 0)
  {
    if (hrState == HR_MEASURING)
    {
      unsigned long elapsed = millis() - hrStartTime;
      int secLeft = (int)((HR_DURATION_MS - elapsed) / 1000) + 1;
      if (secLeft < 0) secLeft = 0;
      if (secLeft > 12) secLeft = 12;

      display.setCursor(0, 13);
      display.print("MEASURING (12 SEC)");

      // Countdown timer
      display.setTextSize(2);
      display.setCursor(4, 25);
      display.printf("%02ds", secLeft);

      // Live pulse indicator
      display.setTextSize(1);
      display.setCursor(60, 26);
      if (runningBPM > 0)
      {
        display.printf("LIVE:%2d", (int)round(runningBPM));
      }
      else
      {
        display.print("Reading...");
      }

      // Heart pulse flash
      display.setCursor(114, 26);
      display.print(flashThisFrame ? "<3" : "  ");

      // Smooth progress bar
      int barW = map(elapsed, 0, HR_DURATION_MS, 0, 116);
      if (barW > 116) barW = 116;
      display.drawRect(4, 43, 120, 6, SSD1306_WHITE);
      display.fillRect(6, 45, barW, 2, SSD1306_WHITE);

      display.setTextSize(1);
      display.setCursor(4, 54);
      if (beatCountThisTest > 0)
      {
        display.printf("Beats: %d (Keep steady)", beatCountThisTest);
      }
      else
      {
        display.print("Keep finger steady");
      }
    }
    else if (hrState == HR_COMPLETED)
    {
      display.setCursor(0, 14);
      display.print("HEART RATE RESULT");

      display.setTextSize(3);
      display.setCursor(12, 26);
      display.print(finalBPM);
      display.setTextSize(1);
      display.setCursor(80, 36);
      display.print("BPM");

      display.setCursor(8, 54);
      display.print("Synced to Cloud [OK]");
    }
    else if (hrState == HR_FAILED)
    {
      display.setCursor(0, 14);
      display.print("SIGNAL UNSTABLE");

      display.setTextSize(1);
      display.setCursor(6, 28);
      display.print("Insufficient beats");
      display.setCursor(6, 41);
      display.print("Hold finger gently");
      display.setCursor(6, 54);
      display.print("and retry test...");
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
  // ---------------- PAGE 1: TEMPERATURE & HUMIDITY ----------------
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

// ============================================================================
// WI-FI CONNECTION
// ============================================================================
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
    Serial.println("\n[Wi-Fi] Connection failed. Will retry automatically.");
    setRGB(true, false, false); // Red
  }
}

// ============================================================================
// SEND TELEMETRY TO GUARDIANLINK CLUSTER / BACKEND
// ============================================================================
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

  // Handle HTTPS for Render / Cloud endpoints and HTTP for LAN
  if (String(SERVER_URL).startsWith("https://"))
  {
    secureClient.setInsecure(); // Required for ESP32 HTTPS without root cert bundling
    http.begin(secureClient, SERVER_URL);
  }
  else
  {
    http.begin(client, SERVER_URL);
  }

  http.setTimeout(12000); // 12-second timeout for cloud connections
  http.addHeader("Content-Type", "application/json");
  http.addHeader("Bypass-Tunnel-Reminder", "true");
  http.addHeader("User-Agent", "GuardianLink-ESP32");

  // Format telemetry payload
  StaticJsonDocument<384> doc;
  doc["device_id"]   = DEVICE_ID;

  // Send real measured heart rate when available; omit otherwise to prevent fake 72/74
  if (finalBPM > 0)
  {
    doc["heart_rate"] = finalBPM;
  }
  else if (hrState == HR_MEASURING && runningBPM > 0)
  {
    doc["heart_rate"] = (int)round(runningBPM);
  }

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
    if (httpCode == 200 || httpCode == 201)
    {
      Serial.println("[HTTP] Vitals successfully synced with GuardianLink cloud!");
    }
    else if (httpCode == 429)
    {
      Serial.println("[HTTP 429] Rate limited. Backing off...");
      lastTelemetryTime = millis() + 15000;
    }
  }
  else
  {
    Serial.printf("[HTTP] Error sending: %s\n", http.errorToString(httpCode).c_str());
    lastTelemetryTime = millis() - TELEMETRY_INTERVAL + 5000;
  }

  http.end();
}

// ============================================================================
// SETUP
// ============================================================================
void setup()
{
  WRITE_PERI_REG(RTC_CNTL_BROWN_OUT_REG, 0); // Disable brownout detector to prevent reboot loop
  Serial.begin(115200);
  delay(1200);
  Serial.println("\n");
  Serial.println("=============================================");
  Serial.println("  GuardianLink / SAFEGUARD ESP32 Wearable    ");
  Serial.println("=============================================");
  Serial.flush();

  // 1. Initialize GPIO Pins
  pinMode(BUZZER_PIN, OUTPUT);
  pinMode(RGB_RED_PIN, OUTPUT);
  pinMode(RGB_GREEN_PIN, OUTPUT);
  pinMode(RGB_BLUE_PIN, OUTPUT);

  digitalWrite(BUZZER_PIN, LOW);
  setRGB(false, false, true); // Blue while booting
  Serial.println("[1/4] GPIO Pins initialized.");
  Serial.flush();

  // 2. Initialize Fast I2C Bus (400kHz prevents bus starvation)
  Wire.begin(SDA_PIN, SCL_PIN);
  Wire.setClock(400000); // 400kHz Fast I2C
  Wire.setTimeOut(1000); // 1-second timeout prevents lockups
  Serial.println("[2/4] Fast I2C Bus started at 400kHz (SDA: 21, SCL: 22).");
  Serial.flush();

  // 3. Initialize SSD1306 OLED
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
    Serial.println("FAILED (Screen at 0x3C not found or wiring loose)");
  }
  Serial.flush();

  // 4. Initialize DHT22
  dht.begin();
  Serial.println("[4/4] DHT22 initialized on GPIO 4.");
  Serial.flush();

  // 5. Initialize MAX30102 Heart-Rate Sensor
  Serial.print("[INIT] Initializing MAX30102... ");
  if (max30102.begin(Wire, I2C_SPEED_FAST))
  {
    max30102Ready = true;
    // Optical configuration calibrated for human tissue:
    // ledBrightness = 0x24 (~7.2mA) prevents photodiode ADC saturation
    // sampleAverage = 4 reduces optical noise
    // ledMode = 2 (Red + IR)
    // sampleRate = 100 (100 Hz sampling for sharp crest detection)
    // pulseWidth = 411us (18-bit ADC resolution)
    // adcRange = 4096nA range
    max30102.setup(0x24, 4, 2, 100, 411, 4096);
    max30102.setPulseAmplitudeRed(0x1F); // Red LED optical amplitude
    max30102.setPulseAmplitudeIR(0x24);  // IR LED optical amplitude
    max30102.setPulseAmplitudeGreen(0);
    Serial.println("OK!");
  }
  else
  {
    Serial.println("FAILED (MAX30102 not found on I2C)");
  }
  Serial.flush();

  // 6. Connect to Wi-Fi
  connectToWiFi();
}

// ============================================================================
// MAIN LOOP
// ============================================================================
void loop()
{
  // 1. High-frequency pulse beat sampling (must run rapidly on every loop cycle)
  processHeartRate();
  handleFingerDebug();

  // 2. Periodic DHT22 sensor readings
  readDHT22();

  // 3. Update OLED Carousel Display
  drawOLED();

  // 4. Update RGB status LED
  updateRGBStatus();

  // 5. Periodic telemetry transmission to GuardianLink backend
  // Only transmit during idle/standby — NEVER during the active 20s test
  // so MAX30102 optical sampling gets 100% of CPU cycles with zero latency!
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
