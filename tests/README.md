# Unit Tests

This folder holds unit tests for local verification of core application logic and data models.

## Structure

- **`BleDeviceTest.kt`**: Tests the distance model calculations (derived from signal strength RSSI inputs) and default parameter configs.
- **`AttendanceRecordTest.kt`**: Validates the Room database entity instantiation, properties, and status bindings.

## Running Tests

These tests have been integrated into the standard Gradle source directories so they can be run securely:

```bash
gradle :app:testDebugUnitTest
```

You can view the test results and baseline logs dynamically after execution.
