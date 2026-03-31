# GoToShelter

GoToShelter is an independent, open-source Android application designed to provide real-time emergency alert monitoring
and automated navigation to safety during life-threatening situations.

**IMPORTANT DISCLAIMER**: GoToShelter does NOT represent and is NOT affiliated with any government entity. It is a
supplementary tool that monitors official notification channels, such as the Israel Home Front
Command (https://www.oref.org.il/). Always follow official government instructions and alerts during emergencies.

## Core Features

- **Real-time Alert Monitoring**:
    - **WebSocket**: Connects directly to Tzofar for sub-second alert delivery.
    - **Notification Listener**: Monitors the official Home Front Command emergency app.
- **Intelligent Navigation Integration**: Automatically triggers navigation to the nearest shelter using **Waze** or **Google Maps**.
- **Context-Aware Driving Detection**: Uses Google Play Services Activity Recognition to provide hands-free assistance specifically when the user is driving.
- **Foreground Alert Resilience**: Dedicated foreground services ensure the app remains vigilant even under aggressive system battery optimization.
- **Overlay Alerts**: Uses system-level overlays to ensure critical navigation prompts are visible over any active application.
- **Privacy First**: All location and alert processing happens on-device, in-memory. No data is collected or shared, 
  except for strictly anonymized crash reports if the user opts in.

## Technical Architecture

The project is built using **Kotlin Multiplatform (KMP)**, separating business logic, alert processing, and UI from 
platform-specific system integrations.

### Stack
- **Language**: Kotlin 2.x
- **UI Framework**: Compose Multiplatform (CMP) with Material 3
- **Asynchrony**: Kotlin Coroutines & Flow for reactive event processing
- **Networking**: Ktor for WebSocket communications
- **Crash Reporting**: Sentry KMP for cross-platform observability

### System Components (Android)
- `AlertManager`: The central coordinator that executes response logic based on alert type and driving status.
- `EmergencyMonitorService`: A high-priority Foreground Service (type: `specialUse` & `location`) managing the persistent WebSocket connection.
- `EmergencyAlertListenerService`: Implements `NotificationListenerService` to intercept official Home Front Command alerts.
- `DrivingActivityReceiver`: Processes Activity Recognition transitions to manage driving state.
- `BootReceiver`: Ensures monitoring starts automatically when the device is powered on.

## Alert Lifecycle & Data Flow

1. **Detection**: Alert data is received via WebSocket (Tzofar) or Notification Listener (Home Front Command).
2. **Processing**: `AlertManager` evaluates the alert against user location and driving status.
3. **Action**: If the user is driving, a navigation intent (Waze/Maps) is fired immediately.

## Permissions

The app requires several critical permissions:

- `INTERNET` & `ACCESS_NETWORK_STATE`: Required for WebSocket connectivity and real-time monitoring.
- `NOTIFICATION_LISTENER` (`BIND_NOTIFICATION_LISTENER_SERVICE`): Essential for monitoring the official emergency app.
- `SYSTEM_ALERT_WINDOW`: Required to launch navigation and overlays from the background.
- `POST_NOTIFICATIONS`: Required for foreground service (Android 13+).
- `ACCESS_COARSE_LOCATION` & `ACCESS_BACKGROUND_LOCATION`: Necessary to determine if an alert applies to the user's 
  current area while the app is in the background.
- `ACTIVITY_RECOGNITION`: Used to detect if the user is driving to automate navigation.
- `RECEIVE_BOOT_COMPLETED`: To automatically start monitoring when the device restarts.
- `FOREGROUND_SERVICE` (including `SPECIAL_USE` & `LOCATION` types): Ensures monitoring remains active on Android 14+.
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: To prevent the system from killing the background monitoring service.

## Getting Started

1. Clone the repository.
2. Open the project in Android Studio (Ladybug or newer recommended).
3. Build and run the `:app` module on an Android device (API 21+).
4. Grant the required permissions.

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.
