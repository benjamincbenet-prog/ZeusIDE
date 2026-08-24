package com.bcbprog.zeuside.network

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BuildRequest(val files: Map<String, String>)

@Serializable
data class BuildResponse(
    val success: Boolean,
    val logs: String? = null,
    val artifacts: List<String>? = null,
    val error: String? = null
)

class CodeSandboxClient(private val serverUrl: String) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun triggerBuild(filesMap: Map<String, String>): BuildResponse {
        return try {
            val response: HttpResponse = client.post("$serverUrl/api/build") {
                contentType(ContentType.Application.Json)
                setBody(BuildRequest(files = filesMap))
            }
            Json.decodeFromString(response.bodyAsText())
        } catch (e: Exception) {
            BuildResponse(success = false, error = e.localizedMessage ?: "Network error")
        }
    }
}
