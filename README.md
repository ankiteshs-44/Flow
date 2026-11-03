# Flow

Flow is a native Android timetable manager built with Kotlin and Jetpack Compose. It uses Google ML Kit Text Recognition on-device to read a timetable image, reconstructs the grid from OCR bounding-box coordinates, and saves the resulting classes in a local SQLite database.

## Run

Open this folder in Android Studio and sync the Gradle project. Use Android SDK 34 and a device or emulator running Android 6.0 (API 23) or newer. Select **Scan a timetable** and choose an image from the device photo picker.

## Reconstruction

OCR lines are classified as weekday headers, time labels, or subject text. Weekdays sharing a horizontal OCR row are treated as headers. Time labels are clustered by their vertical positions for row-based timetables, or their horizontal positions for transposed timetables. Each remaining OCR line is assigned to the nearest day/time coordinate pair.

Scanned entries replace the current schedule. The photo itself is not retained, and ML Kit recognition runs on-device.
