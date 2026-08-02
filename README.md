# Field Weather Recorder

An Android client and local C hub server for recording and syncing weather data.

## Download Prebuilt Binaries

You can download the latest prebuilt `fieldweather-release.zip` containing both the compiled Android APK and the C Hub executable from the GitHub Releases page:

**[Download Latest Release](https://github.com/bect/weather-recorder/releases/latest)**

*(Inside the zip, you will find `fieldweather-app.apk` and the `fieldweather-hub` executable).*

---

## Compiling from Source

### Prerequisites
- JDK 17
- Android SDK (for the client)
- GCC and Make (for the server)
- SQLite development libraries (optional, the hub bundles its own)

### 1. Compile Android Client

The Android client is built using Gradle. To build the release APK:

```bash
# Navigate to the project root
chmod +x ./gradlew
./gradlew assembleRelease
```
The compiled APK will be output to: `app/build/outputs/apk/release/app-release.apk`

### 2. Compile Local Hub Server

The Hub server is written in C and handles local network syncing and database storage.

```bash
# Navigate to the hub directory
cd weather-recorder-hub-c

# Build the executable
make
```
The compiled executable will be output as `./hub`.

## Configuration

If you are using the Local Hub, you can optionally configure it to automatically sync with a Turso cloud database by creating a `.env` file in the `weather-recorder-hub-c` directory:

```env
TURSO_DB_URL=libsql://your-database-url.turso.io
TURSO_TOKEN=your_auth_token
```

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
