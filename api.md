# Weather Recorder API Recommendation

This document outlines the recommended API design for the local server running at port `8888` to receive weather records from the FieldWeather Android app.

## Endpoint: Sync Weather Data

**URL:** `http://<server-ip>:8888/sync`  
**Method:** `POST`  
**Content-Type:** `application/json`

### Description
Receives an array of newly recorded weather logs. The app will send all records that have not yet been successfully synced to the server.

### Request Body
The request should contain a JSON array of weather record objects, along with the timestamp of when the sync was initiated by the client.

```json
{
  "client_sync_timestamp": "2026-07-30T15:25:00Z",
  "records": [
    {
      "local_id": 1,
      "timestamp": "2026-07-30T14:00:00Z",
      "condition": "Sunny",
      "latitude": -7.250445,
      "longitude": 112.768845,
      "location_name": "S. PASTURE"
    },
    {
      "local_id": 2,
      "timestamp": "2026-07-30T16:15:00Z",
      "condition": "Cloudy",
      "latitude": -7.250445,
      "longitude": 112.768845,
      "location_name": "N. FIELD"
    }
  ]
}
```

#### Field Definitions
- `client_sync_timestamp` (String/ISO 8601): The date and time when the app initiated this synchronization request. This allows the server to record exactly when the client considers the data synced.
- `local_id` (Integer): The local SQLite primary key from the Android device. This can be used by the server to identify unique records or handle duplicates if a sync retry occurs.
- `timestamp` (String/ISO 8601): The exact UTC date and time the weather observation was logged.
- `condition` (String): The observed weather condition (e.g., "Sunny", "Cloudy", "Raining", "Stormy", "Windy", "Foggy").
- `latitude` (Float/Double): GPS latitude of the observation.
- `longitude` (Float/Double): GPS longitude of the observation.
- `location_name` (String): User-provided or selected location name (e.g., "S. PASTURE").

### Response

#### Success (200 OK)
Returns a success message along with the number of records synced. If successful, the Android app will mark these specific `local_id`s as `isSynced = true` so they aren't sent again.

```json
{
  "status": "success",
  "synced_count": 2,
  "synced_ids": [1, 2]
}
```

#### Error (400 Bad Request / 500 Internal Server Error)
```json
{
  "status": "error",
  "message": "Invalid data format or database error."
}
```
