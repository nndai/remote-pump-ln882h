#include <Arduino.h>

unsigned long pulseIn(uint8_t pin, uint8_t state, unsigned long timeout) {
    unsigned long start = micros();
    unsigned long now;

    while (digitalRead(pin) == state) {
        now = micros();
        if (now - start > timeout) return 0;
    }
    while (digitalRead(pin) != state) {
        now = micros();
        if (now - start > timeout) return 0;
    }
    start = micros();
    while (digitalRead(pin) == state) {
        now = micros();
        if (now - start > timeout) return 0;
    }
    return now - start;
}
