import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Simple test client for connecting to CertStream WebSocket endpoints.
 * 
 * Usage:
 *   kotlinc TestClient.kt -include-runtime -d TestClient.jar
 *   java -jar TestClient.jar [lite|full|domains]
 */
fun main(args: Array<String>) = runBlocking {
    val streamType = args.getOrNull(0) ?: "lite"
    
    val port = when (streamType) {
        "lite" -> 9001
        "full" -> 9002
        "domains" -> 9003
        else -> {
            println("Unknown stream type: $streamType")
            println("Usage: java -jar TestClient.jar [lite|full|domains]")
            return@runBlocking
        }
    }
    
    println("Connecting to $streamType stream on port $port...")
    
    val client = HttpClient(CIO) {
        install(WebSockets)
    }
    
    try {
        client.webSocket("ws://localhost:$port/") {
            println("Connected! Listening for certificates...")
            
            var count = 0
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    
                    try {
                        val json = Json.parseToJsonElement(text).jsonObject
                        val messageType = json["message_type"]?.jsonPrimitive?.content
                        
                        if (messageType == "certificate_update") {
                            count++
                            
                            val data = json["data"]?.jsonObject
                            
                            when (streamType) {
                                "domains" -> {
                                    val domains = data?.get("domains")
                                    println("[$count] Domains: $domains")
                                }
                                else -> {
                                    val leafCert = data?.get("leaf_cert")?.jsonObject
                                    val allDomains = leafCert?.get("all_domains")
                                    val source = data?.get("source")?.jsonObject
                                    val sourceName = source?.get("name")?.jsonPrimitive?.content
                                    
                                    println("[$count] Certificate from $sourceName")
                                    println("       Domains: $allDomains")
                                }
                            }
                            
                            // Print every 10th certificate
                            if (count % 10 == 0) {
                                println("--- Received $count certificates ---")
                            }
                        }
                    } catch (e: Exception) {
                        println("Error parsing message: ${e.message}")
                    }
                }
            }
        }
    } catch (e: Exception) {
        println("Error: ${e.message}")
    } finally {
        client.close()
    }
}
