package io.github.bect.fieldweather.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

data class TursoRequest(
    val requests: List<TursoStatement>
)

data class TursoStatement(
    val type: String, // "execute" or "close"
    val stmt: TursoSql? = null
)

data class TursoSql(
    val sql: String,
    val args: List<TursoArg> = emptyList()
)

data class TursoArg(
    val type: String,
    val value: Any
)

interface TursoApi {
    @POST
    suspend fun executePipeline(
        @Url url: String, // e.g., "https://<db>.turso.io/v2/pipeline"
        @Header("Authorization") authHeader: String,
        @Body request: TursoRequest
    ): Response<Any>
}
