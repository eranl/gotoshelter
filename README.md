# GoToShelter

GoToShelter is an independent, open-source Android application designed to provide real-time emergency alert monitoring and navigation to safety during life-threatening situations. 

**IMPORTANT DISCLAIMER**: GoToShelter does NOT represent and is NOT affiliated with any government entity. It is a supplementary tool that monitors official notification channels, such as the Israel Home Front Command (https://www.oref.org.il/). Always follow official government instructions and alerts during emergencies.

## Features

- **Emergency Alert Monitoring**: Listens for critical emergency notifications using a dedicated `NotificationListenerService`.
- **Real-time Monitoring**: Runs a foreground service to ensure continuous monitoring during emergency events.
- **Navigation Integration**: Seamlessly integrates with popular navigation apps like **Waze** and **Google Maps** to guide users to safety.
- **Driving Detection**: Automatically detects when the user is driving to provide navigation assistance.
- **Overlay Alerts**: Uses system-level overlays to ensure navigation starts even when other apps are in use.

## Technical Details

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material 3
- **Architecture**: Modern Android architecture components
- **Key Services**:
    - `EmergencyAlertListenerService`: Monitors system notifications for emergency alerts.
    - `EmergencyMonitorService`: A foreground service for real-time safety monitoring.
    - `DrivingActivityReceiver`: Detects user activity to adapt app behavior.

## Permissions

To provide its life-saving features, the app requires several permissions:

- `NOTIFICATION_LISTENER`: To monitor incoming emergency alerts.
- `SYSTEM_ALERT_WINDOW`: To launch navigation from the background.
- `ACCESS_COARSE_LOCATION` & `ACCESS_BACKGROUND_LOCATION`: To determine whether an incoming alert applies to the user's current position.
- `ACTIVITY_RECOGNITION`: To detect driving.
- `FOREGROUND_SERVICE`: To ensure the app remains active during emergencies.

## Getting Started

1. Clone the repository.
2. Open the project in Android Studio.
3. Build and run the app on an Android device (API 24 or higher recommended).
4. Grant the necessary permissions (Notification Access and Overlay) when prompted by the app.

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.
