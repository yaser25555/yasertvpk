
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers

fun main() {
    val baseUrl = "https://koralive.lol/aghmdev/kooralive/api/v26"
    val endpoints = listOf(
        "",
        "/live",
        "/channels",
        "/movies",
        "/series",
        "/categories"
    )
    
    val client = HttpClient.newHttpClient()
    
    for (endpoint in endpoints) {
        val url = "$baseUrl$endpoint"
        println("\n🧪 Testing: $url")
        
        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "okhttp/5.0.0-alpha.2")
                .build()
                
            val response = client.send(request, BodyHandlers.ofString())
            
            println("Status: ${response.statusCode()}")
            if (response.statusCode() == 200) {
                println("Response (first 500 chars):")
                println(response.body().take(500))
            }
        } catch (e: Exception) {
            println("Error: ${e.message}")
        }
    }
}
