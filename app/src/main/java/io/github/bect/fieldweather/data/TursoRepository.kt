package io.github.bect.fieldweather.data

import io.github.bect.fieldweather.network.TursoApi
import io.github.bect.fieldweather.network.TursoArg
import io.github.bect.fieldweather.network.TursoRequest
import io.github.bect.fieldweather.network.TursoSql
import io.github.bect.fieldweather.network.TursoStatement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class TursoRepository(private val dbUrl: String, private val token: String) {
    private val api: TursoApi

    init {
        val baseUrl = if (dbUrl.startsWith("libsql://")) {
            "https://" + dbUrl.removePrefix("libsql://")
        } else {
            dbUrl
        }
        
        val retrofit = Retrofit.Builder()
            .baseUrl("https://turso.io/") // Dummy base URL, we use @Url
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        api = retrofit.create(TursoApi::class.java)
    }

    private val actualUrl = if (dbUrl.startsWith("libsql://")) {
        "https://" + dbUrl.removePrefix("libsql://") + "/v2/pipeline"
    } else {
        dbUrl.trimEnd('/') + "/v2/pipeline"
    }

    suspend fun syncRecords(records: List<WeatherRecord>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val statements = mutableListOf<TursoStatement>()
            
            // First statement: Create table if not exists
            statements.add(
                TursoStatement(
                    type = "execute",
                    stmt = TursoSql(
                        sql = "CREATE TABLE IF NOT EXISTS weather_records (" +
                                "local_id INTEGER PRIMARY KEY," +
                                "timestamp TEXT," +
                                "condition TEXT," +
                                "latitude REAL," +
                                "longitude REAL," +
                                "location_name TEXT" +
                                ");"
                    )
                )
            )

            // Following statements: Insert or replace records
            records.forEach { record ->
                statements.add(
                    TursoStatement(
                        type = "execute",
                        stmt = TursoSql(
                            sql = "INSERT OR REPLACE INTO weather_records (local_id, timestamp, condition, latitude, longitude, location_name) VALUES (?, ?, ?, ?, ?, ?)",
                            args = listOf(
                                TursoArg("integer", record.id.toString()), // SQLite actually prefers strings for big ints in Turso JSON API
                                TursoArg("text", record.timestamp),
                                TursoArg("text", record.condition),
                                TursoArg("float", record.latitude),
                                TursoArg("float", record.longitude),
                                TursoArg("text", record.locationName)
                            )
                        )
                    )
                )
            }
            
            statements.add(TursoStatement(type = "close"))

            val request = TursoRequest(requests = statements)
            val authHeader = "Bearer $token"

            val response = api.executePipeline(actualUrl, authHeader, request)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Turso sync failed: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
