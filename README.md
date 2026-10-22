# India Tourism Explorer

India Tourism Explorer is an Android app for discovering destinations across India, viewing them on a map, and organizing trip plans. It combines a curated, locally stored destination catalogue with optional live services for AI travel advice, maps and routing, address search, weather, Google sign-in, and Gmail booking import.

> **Project status:** This repository contains an Android application project. External integrations require the developer's own API credentials and service configuration; without them, some live features will be unavailable. Destination and itinerary data are seeded locally so the core browsing experience does not depend on a remote catalogue API.

## Features

- **Explore destinations:** Browse a curated set of 18 Indian attractions, search the catalogue, filter by category, and open destination details with descriptions, visitor information, coordinates, and transport notes.
- **Favorites:** Save and revisit favorite places. Destination records and favorites are stored in a local Room database.
- **Interactive map:** View destination markers on Google Maps and filter the map by broad Indian regions. Map tiles and Places-based features require a valid Google Maps/Places configuration.
- **Travel routes:** Choose an origin and destination and view route details. Routing can use OSRM and, when configured, Geoapify services.
- **AI travel guide:** Ask for destination-specific travel advice using Gemini or the optional OpenRouter-compatible path. A key is required for live AI responses.
- **Itineraries:** Create, view, and delete trip plans. The app seeds example itineraries on first launch when the itinerary table is empty.
- **Optional booking import:** The itinerary area includes a Gmail travel-confirmation scanning/import flow. Google/Firebase and Gmail authorization must be configured; the app also includes sample booking data for fallback/demo behavior.
- **Live weather:** Fetch current weather for a selected destination through OpenWeather when an API key is configured.
- **Address search:** Search for Indian addresses through Geoapify autocomplete when configured.
- **Profile and preferences:** Includes Google sign-in controls and settings for theme, haptics, offline preference, and privacy-related options.
- **Material 3 interface:** Built with Jetpack Compose, with light and dark themes and a custom India-inspired visual palette.

Some features depend on third-party services, network access, API quotas, or account configuration. Enabling a setting in the UI does not replace configuring the corresponding provider or permissions.

## Tech stack

- Kotlin and Android Gradle Plugin
- Jetpack Compose and Material 3
- AndroidX Lifecycle / ViewModel and Kotlin Coroutines
- Room for local persistence
- Google Maps Compose, Google Maps SDK, and Places SDK
- Firebase Authentication and Firebase AI dependencies
- Google Credential Manager for Google sign-in
- OkHttp for service requests
- Coil for remote destination imagery
- Robolectric, JUnit, Compose UI testing, and Roborazzi dependencies

The Android application ID is `com.aistudio.indiatourism.exptvl`. The minimum SDK is API 24 (Android 7.0); the project is configured with compile/target SDK 36.

## Repository layout

```text
.
├── app/
│   ├── build.gradle.kts                 # Android app, plugins, SDK levels, dependencies
│   └── src/
│       ├── main/java/com/example/
│       │   ├── MainActivity.kt          # Compose entry point and bottom navigation
│       │   ├── data/
│       │   │   ├── api/                 # Gemini, Gmail, weather, routing, geocoding
│       │   │   ├── auth/                # Firebase / Google authentication
│       │   │   ├── local/               # Room database, DAOs, seed data, key manager
│       │   │   ├── model/               # Destination and itinerary entities
│       │   │   └── repository/          # Local data access and seed logic
│       │   └── ui/
│       │       ├── screens/             # Explore, map, AI, itinerary, profile screens
│       │       ├── components/          # Shared Compose UI components
│       │       ├── theme/               # Material theme and palette
│       │       └── TourismViewModel.kt  # UI state and app actions
│       └── test/                        # Unit, Robolectric, and screenshot tests
├── gradle/libs.versions.toml            # Dependency and plugin versions
├── .env.example                         # Local API-key template
├── firebase-applet-config.json         # Firebase applet metadata
└── settings.gradle.kts
```

## Requirements

- Android Studio with support for the Android Gradle Plugin version declared in `gradle/libs.versions.toml`.
- Android SDK Platform 36 and a compatible JDK. The JDK bundled with a compatible Android Studio installation is the recommended option.
- Internet access to download Gradle dependencies and to use configured live services.
- Optional provider accounts and API keys for the integrations described below.

### Gradle wrapper note

The repository includes `gradle/wrapper/gradle-wrapper.properties` but does **not** currently include the `gradlew` / `gradlew.bat` scripts or the wrapper JAR. You can import the project in Android Studio and let it sync using its configured Gradle installation. For command-line builds, install a Gradle version compatible with the declared Android Gradle Plugin and use the `gradle` command, or add and commit a Gradle wrapper as a separate project change.

## Configure API keys and services

The Secrets Gradle Plugin is configured to read a root-level `.env` file, and `.env` is excluded from Git. Start from the checked-in template:

```bash
cp .env.example .env
```

Replace placeholder values with your own credentials as needed:

| Variable | Used for | Required for |
| --- | --- | --- |
| `GEMINI_API_KEY` | Gemini travel advice | Live Gemini AI responses |
| `OPENROUTER_API_KEY` | Optional OpenRouter-compatible AI route | AI responses through OpenRouter |
| `MAPS_API_KEY` | Google Maps / Places integration | Live Google map and related map features |
| `OPENWEATHER_API_KEY` | OpenWeather | Live weather in destination details |
| `GEOAPIFY_API_KEY` | Geoapify geocoding/routing | Address autocomplete and Geoapify-backed routing |

Use provider-issued keys and enable the relevant provider APIs in their consoles. Configure API restrictions and quotas where available. A key embedded in an Android app or compiled into `BuildConfig` can be extracted from the app; do not treat client-side configuration as a secure place for privileged server secrets. Never commit `.env`, private OAuth credentials, signing credentials, or service-account files.

Some keys can also be entered through the relevant in-app settings. Keys entered in the app are stored in app-private preferences on the device; this does not make them suitable for distributing privileged credentials in a production build.

### Firebase, Google sign-in, and Gmail

Google sign-in depends on a Firebase Android app matching the application ID `com.aistudio.indiatourism.exptvl`, Firebase Authentication being enabled, and the correct Google OAuth client configuration. For a conventional Firebase Android setup, place the matching `google-services.json` under `app/` and configure the OAuth client in the Firebase/Google Cloud consoles. The checked-in `firebase-applet-config.json` is applet metadata and is not a substitute for the Android `google-services.json` configuration.

Gmail scanning additionally requires valid Gmail API authorization and an access token with the appropriate user consent/scopes. Configure and test this integration with a dedicated development account. Until service authorization is in place, use the app's available sample/fallback booking flow for demonstration.

## Build and run

1. Clone the repository and open the project root in Android Studio.
2. Add a root `.env` file if you want to use any live provider integrations.
3. Sync Gradle, install the required Android SDK platform/build tools if prompted, and select an Android 7.0/API 24 or newer device or emulator.
4. Run the `app` configuration from Android Studio.

With a compatible system Gradle installation, a debug APK can be built from the repository root with:

```bash
gradle :app:assembleDebug
```

The APK is normally written to `app/build/outputs/apk/debug/`. Without API keys, optional online features may be unavailable, but the app's locally seeded catalogue remains available.

## Tests

Run the local unit/Robolectric test task with a compatible system Gradle installation:

```bash
gradle :app:testDebugUnitTest
```

Android instrumentation tests can be run on a configured emulator/device with:

```bash
gradle :app:connectedDebugAndroidTest
```

The test sources currently include a small unit test, a Robolectric resource test, and a Compose screenshot test. Screenshot capture may write under `app/src/test/screenshots/`.

## Data and privacy notes

- Destination and itinerary data use a local Room database named `india_tourism_explorer.db`.
- The current database schema seeds curated destinations and sample itineraries when the corresponding tables are empty.
- API keys entered in-app are stored in app-private `SharedPreferences`.
- Network-backed features communicate with their configured third-party providers. Review those providers' terms, privacy policies, and data handling before using real personal data.
- Gmail access should be granted only with explicit account consent and the minimum required scopes.
- Privacy-related controls are presented in the app UI; review the implementation and test behavior for your deployment requirements before describing any control as a security guarantee.

## Contributing

Issues and pull requests are welcome. When contributing, please:

1. Keep credentials and personal data out of source control.
2. Describe any new provider setup or permissions in this README.
3. Run the relevant Gradle tests and include screenshots for significant UI changes when possible.

## License

No license file is currently included in this repository. Unless a license is added, standard copyright restrictions apply; public visibility alone does not grant permission to reuse, modify, or redistribute the code.
