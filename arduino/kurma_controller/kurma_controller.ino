/* ============================================================================
 *  KURMA MAKHAR CONTROLLER  --  Arduino UNO
 *  Matches the actual wiring on the board:
 *
 *    PIR  ........... 5V / GND / D2
 *    L298N (river) .. IN4=D3  IN1=D4  IN2=D5  IN3=D6
 *    JD1  .. D7 ..... Kurma synchronous AC motor
 *    JD3  .. D8 ..... Warkari fugadi AC motor
 *    JD5  .. D9 ..... Temple LED
 *    JD7  .. D10 .... River LED
 *    JD9  .. D11 .... Temple mist maker #1
 *    JD11 .. D12 .... Temple mist maker #2
 *    JD13 .. A0 ..... Warkari statue LED
 *    JD15 .. A1 ..... Makhar LED (main makhar lighting)
 *
 *  Relay board is ACTIVE LOW:  LOW = relay ON,  HIGH = relay OFF.
 *
 *  ---------------------------------------------------------------------------
 *  PROTOCOL (the Android app is the brain, this sketch is only the muscle)
 *
 *  Arduino -> App :  "READY"   once after boot / reset
 *                    "MOTION"  every time the PIR sees movement
 *                              (repeated at most once a second while movement
 *                               continues -- the app uses this to work out
 *                               when the crowd has actually left)
 *
 *  App -> Arduino :  "K_ON"     step 1: makhar LED + kurma motor
 *                    "R_ON"     step 2: river LED + river motor cycle
 *                    "W_ON"     step 3: fugadi motor + warkari statue LED
 *                    "T_ON"     step 4: temple LED + both mist makers
 *                    "SHOW_ON"  10-minute darshan showcase: everything lit,
 *                               river loops gently, mist is duty-cycled
 *                    "ALL_OFF"  everything off
 *                    "PING"     keep-alive (resets the watchdog)
 *
 *  NOTE: the old 60-second hardware auto-shutdown was removed on purpose --
 *  it fought with the app's 10-minute showcase and killed the lights mid-show.
 *  A long watchdog (WATCHDOG_MS) replaces it: if the app goes silent or the
 *  tablet is unplugged, everything is switched off safely.
 * ========================================================================== */

// --------------------------- PIN DEFINITIONS -------------------------------
#define PIR_PIN 2

// River motors (L298N) -- keep this exact IN order, it matches the wiring
#define IN4 3
#define IN1 4
#define IN2 5
#define IN3 6

// Relays (active LOW)
#define RELAY_KURMA_MOTOR   7    // JD1
#define RELAY_FUGADI_MOTOR  8    // JD3
#define RELAY_TEMPLE_LED    9    // JD5
#define RELAY_RIVER_LED     10   // JD7
#define RELAY_MIST_1        11   // JD9
#define RELAY_MIST_2        12   // JD11
#define RELAY_STATUE_LED    A0   // JD13
#define RELAY_MAKHAR_LED    A1   // JD15

const uint8_t RELAY_PINS[] = {
  RELAY_KURMA_MOTOR, RELAY_FUGADI_MOTOR, RELAY_TEMPLE_LED, RELAY_RIVER_LED,
  RELAY_MIST_1, RELAY_MIST_2, RELAY_STATUE_LED, RELAY_MAKHAR_LED
};
const uint8_t RELAY_COUNT = sizeof(RELAY_PINS);

#define RELAY_ON  LOW
#define RELAY_OFF HIGH

// ------------------------------ SETTINGS -----------------------------------
unsigned long timeM1 = 1200;              // river motor reverse leg (ms)
unsigned long timeM2 = 1400;              // river motor forward leg (ms)
unsigned long riverPause = 2000;          // pause between legs (ms)
int motorSpeed = 55;                      // river motor PWM

const unsigned long PIR_WARMUP_MS    = 30000UL;   // ignore PIR right after boot
const unsigned long MOTION_REPEAT_MS = 1000UL;    // min gap between MOTION lines
const unsigned long RIVER_LOOP_GAP   = 6000UL;    // idle gap when river loops in showcase

// Mist makers are duty-cycled during the long showcase so they are never
// left running dry for ten minutes straight.
const unsigned long MIST_ON_MS  = 120000UL;       // 2 min on
const unsigned long MIST_OFF_MS = 60000UL;        // 1 min off

// If the app says nothing at all for this long, shut the makhar down.
const unsigned long WATCHDOG_MS = 20UL * 60UL * 1000UL;   // 20 minutes

// --------------------------- STATE & TIMERS --------------------------------
String  inputString = "";
bool    stringComplete = false;

unsigned long lastMotionSent = 0;
unsigned long lastCommandAt  = 0;
bool          outputsActive  = false;

// Non-blocking river cycle: 0 = idle, 1 = forward, 2 = pause, 3 = reverse, 4 = loop gap
uint8_t       riverPhase = 0;
unsigned long riverPhaseStart = 0;
bool          riverLoop = false;          // keep repeating (showcase mode)

// Mist duty cycling during showcase
bool          mistCycling = false;
bool          mistOnNow   = false;
unsigned long mistPhaseStart = 0;

void setup() {
  Serial.begin(9600);
  inputString.reserve(32);

  pinMode(PIR_PIN, INPUT);

  pinMode(IN1, OUTPUT); pinMode(IN2, OUTPUT);
  pinMode(IN3, OUTPUT); pinMode(IN4, OUTPUT);
  stopRiverMotors();

  for (uint8_t i = 0; i < RELAY_COUNT; i++) {
    pinMode(RELAY_PINS[i], OUTPUT);
    digitalWrite(RELAY_PINS[i], RELAY_OFF);
  }

  lastCommandAt = millis();
  Serial.println("READY");
}

void loop() {
  handlePir();
  serviceRiver();
  serviceMist();
  handleCommand();
  serviceWatchdog();
}

// ------------------------------- PIR ---------------------------------------
// Report movement, nothing else. The app owns every decision about whether a
// sequence should play, so the sketch never suppresses or filters motion.
void handlePir() {
  if (millis() < PIR_WARMUP_MS) return;

  if (digitalRead(PIR_PIN) == HIGH) {
    if (millis() - lastMotionSent >= MOTION_REPEAT_MS) {
      lastMotionSent = millis();
      Serial.println("MOTION");
    }
  }
}

// --------------------------- SERIAL COMMANDS -------------------------------
void serialEvent() {
  while (Serial.available()) {
    char inChar = (char)Serial.read();
    if (inChar == '\n' || inChar == '\r') {
      if (inputString.length() > 0) stringComplete = true;
    } else {
      if (inputString.length() < 30) inputString += inChar;
    }
  }
}

void handleCommand() {
  if (!stringComplete) return;

  inputString.trim();
  lastCommandAt = millis();

  if (inputString == "K_ON") {                 // step 1 - kurma
    digitalWrite(RELAY_MAKHAR_LED, RELAY_ON);
    digitalWrite(RELAY_KURMA_MOTOR, RELAY_ON);
    outputsActive = true;
  }
  else if (inputString == "R_ON") {            // step 2 - river
    digitalWrite(RELAY_MAKHAR_LED, RELAY_ON);
    digitalWrite(RELAY_RIVER_LED, RELAY_ON);
    startRiverCycle(false);
    outputsActive = true;
  }
  else if (inputString == "W_ON") {            // step 3 - warkari
    digitalWrite(RELAY_FUGADI_MOTOR, RELAY_ON);
    digitalWrite(RELAY_STATUE_LED, RELAY_ON);
    outputsActive = true;
  }
  else if (inputString == "T_ON") {            // step 4 - temple + mist
    digitalWrite(RELAY_TEMPLE_LED, RELAY_ON);
    mistOn();
    mistCycling = false;
    outputsActive = true;
  }
  else if (inputString == "SHOW_ON") {         // 10-minute darshan showcase
    digitalWrite(RELAY_MAKHAR_LED, RELAY_ON);
    digitalWrite(RELAY_KURMA_MOTOR, RELAY_ON);
    digitalWrite(RELAY_FUGADI_MOTOR, RELAY_ON);
    digitalWrite(RELAY_TEMPLE_LED, RELAY_ON);
    digitalWrite(RELAY_RIVER_LED, RELAY_ON);
    digitalWrite(RELAY_STATUE_LED, RELAY_ON);
    if (!mistCycling) {                        // start the mist duty cycle
      mistCycling = true;
      mistOnNow = true;
      mistPhaseStart = millis();
      mistOn();
    }
    if (riverPhase == 0) startRiverCycle(true);
    riverLoop = true;
    outputsActive = true;
  }
  else if (inputString == "ALL_OFF") {
    turnEverythingOff();
  }
  else if (inputString == "PING") {
    // keep-alive only
  }

  inputString = "";
  stringComplete = false;
}

// ---------------------- RIVER MOTORS (non-blocking) ------------------------
// The old version used delay() for ~4.6 s, during which PIR reports and app
// commands were frozen. This runs the same motion off millis() instead.
void startRiverCycle(bool loopForever) {
  riverLoop = loopForever;
  riverPhase = 1;
  riverPhaseStart = millis();
  riverForward();
}

void serviceRiver() {
  if (riverPhase == 0) return;
  unsigned long elapsed = millis() - riverPhaseStart;

  switch (riverPhase) {
    case 1:                                  // forward leg
      if (elapsed >= timeM2) {
        stopRiverMotors();
        riverPhase = 2; riverPhaseStart = millis();
      }
      break;
    case 2:                                  // pause
      if (elapsed >= riverPause) {
        riverReverse();
        riverPhase = 3; riverPhaseStart = millis();
      }
      break;
    case 3:                                  // reverse leg
      if (elapsed >= timeM1) {
        stopRiverMotors();
        if (riverLoop) { riverPhase = 4; riverPhaseStart = millis(); }
        else           { riverPhase = 0; }
      }
      break;
    case 4:                                  // quiet gap, then repeat
      if (elapsed >= RIVER_LOOP_GAP) {
        riverForward();
        riverPhase = 1; riverPhaseStart = millis();
      }
      break;
  }
}

void riverForward() {
  digitalWrite(IN1, HIGH); analogWrite(IN2, 255 - motorSpeed);
  digitalWrite(IN3, LOW);  analogWrite(IN4, motorSpeed);
}

void riverReverse() {
  digitalWrite(IN1, LOW);  analogWrite(IN2, motorSpeed);
  digitalWrite(IN3, HIGH); analogWrite(IN4, 255 - motorSpeed);
}

void stopRiverMotors() {
  digitalWrite(IN1, LOW); analogWrite(IN2, 0);
  digitalWrite(IN3, LOW); analogWrite(IN4, 0);
}

// ------------------------------- MIST --------------------------------------
void mistOn() {
  digitalWrite(RELAY_MIST_1, RELAY_ON);
  digitalWrite(RELAY_MIST_2, RELAY_ON);
}

void mistOff() {
  digitalWrite(RELAY_MIST_1, RELAY_OFF);
  digitalWrite(RELAY_MIST_2, RELAY_OFF);
}

void serviceMist() {
  if (!mistCycling) return;
  unsigned long elapsed = millis() - mistPhaseStart;

  if (mistOnNow && elapsed >= MIST_ON_MS) {
    mistOff(); mistOnNow = false; mistPhaseStart = millis();
  } else if (!mistOnNow && elapsed >= MIST_OFF_MS) {
    mistOn();  mistOnNow = true;  mistPhaseStart = millis();
  }
}

// ----------------------------- WATCHDOG ------------------------------------
void serviceWatchdog() {
  if (!outputsActive) return;
  if (millis() - lastCommandAt >= WATCHDOG_MS) {
    turnEverythingOff();
  }
}

void turnEverythingOff() {
  for (uint8_t i = 0; i < RELAY_COUNT; i++) {
    digitalWrite(RELAY_PINS[i], RELAY_OFF);
  }
  stopRiverMotors();
  riverPhase = 0;
  riverLoop = false;
  mistCycling = false;
  mistOnNow = false;
  outputsActive = false;
}
