package com.certstream

import com.certstream.models.StreamType
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import java.time.Duration

private val logger = KotlinLogging.logger {}

/**
 * Combined WebSocket and HTTP server.
 */
class CertStreamServer(
    private val clientManager: ClientManager,
    private val certBuffer: CertificateBuffer
) {
    private var httpServer: NettyApplicationEngine? = null
    private var wsLiteServer: NettyApplicationEngine? = null
    private var wsFullServer: NettyApplicationEngine? = null
    private var wsDomainsServer: NettyApplicationEngine? = null
    
    /**
     * Start the server.
     */
    suspend fun start() {
        // Start HTTP server
        httpServer = embeddedServer(Netty, port = Config.PORT, host = Config.HOST) {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                })
            }
            
            routing {
                get("/latest.json") {
                    handleLatest(call)
                }
                
                get("/example.json") {
                    handleExample(call)
                }
                
                get("/stats") {
                    handleStats(call)
                }
                
                get("/health") {
                    handleHealth(call)
                }
            }
        }.start(wait = false)
        
        logger.info { "HTTP server started on http://${Config.HOST}:${Config.PORT}" }
        
        // Start WebSocket servers on different ports
        // Lite stream on main port + 1
        wsLiteServer = embeddedServer(Netty, port = Config.PORT + 1, host = Config.HOST) {
            install(WebSockets) {
                pingPeriod = Duration.ofSeconds(60)
                timeout = Duration.ofSeconds(60)
                maxFrameSize = Long.MAX_VALUE
                masking = false
            }
            
            routing {
                webSocket("/") {
                    clientManager.handleClient(this, StreamType.LITE)
                }
            }
        }.start(wait = false)
        
        logger.info { "WebSocket lite stream started on ws://${Config.HOST}:${Config.PORT + 1}/" }
        
        // Full stream on main port + 2
        wsFullServer = embeddedServer(Netty, port = Config.PORT + 2, host = Config.HOST) {
            install(WebSockets) {
                pingPeriod = Duration.ofSeconds(60)
                timeout = Duration.ofSeconds(60)
                maxFrameSize = Long.MAX_VALUE
                masking = false
            }
            
            routing {
                webSocket("/") {
                    clientManager.handleClient(this, StreamType.FULL)
                }
            }
        }.start(wait = false)
        
        logger.info { "WebSocket full stream started on ws://${Config.HOST}:${Config.PORT + 2}/" }
        
        // Domains-only stream on main port + 3
        wsDomainsServer = embeddedServer(Netty, port = Config.PORT + 3, host = Config.HOST) {
            install(WebSockets) {
                pingPeriod = Duration.ofSeconds(60)
                timeout = Duration.ofSeconds(60)
                maxFrameSize = Long.MAX_VALUE
                masking = false
            }
            
            routing {
                webSocket("/") {
                    clientManager.handleClient(this, StreamType.DOMAINS_ONLY)
                }
            }
        }.start(wait = false)
        
        logger.info { "WebSocket domains-only stream started on ws://${Config.HOST}:${Config.PORT + 3}/" }
    }
    
    /**
     * Stop the server.
     */
    suspend fun stop() {
        logger.info { "Stopping server..." }
        
        // Stop all servers
        wsDomainsServer?.stop(1000, 2000)
        wsFullServer?.stop(1000, 2000)
        wsLiteServer?.stop(1000, 2000)
        httpServer?.stop(1000, 2000)
        
        logger.info { "Server stopped" }
    }
    
    /**
     * Handle /latest.json endpoint.
     * 
     * Returns the 25 most recent certificates.
     */
    private suspend fun handleLatest(call: ApplicationCall) {
        try {
            val certificates = certBuffer.getLatest()
            
            call.respond(
                LatestResponse(
                    certificates = certificates,
                    count = certificates.size
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Error handling /latest.json" }
            call.respond(
                io.ktor.http.HttpStatusCode.InternalServerError,
                ErrorResponse("Internal server error")
            )
        }
    }
    
    /**
     * Handle /example.json endpoint.
     * 
     * Returns an example certificate.
     */
    private suspend fun handleExample(call: ApplicationCall) {
        try {
            val example = certBuffer.getExample()
            
            if (example != null) {
                call.respond(example)
            } else {
                call.respond(
                    io.ktor.http.HttpStatusCode.NotFound,
                    ErrorResponse("No certificates available yet")
                )
            }
        } catch (e: Exception) {
            logger.error(e) { "Error handling /example.json" }
            call.respond(
                io.ktor.http.HttpStatusCode.InternalServerError,
                ErrorResponse("Internal server error")
            )
        }
    }
    
    /**
     * Handle /stats endpoint.
     * 
     * Returns server statistics.
     */
    private suspend fun handleStats(call: ApplicationCall) {
        try {
            val bufferStats = certBuffer.getStats()
            val clientStats = clientManager.getStats()
            
            val stats = StatsResponse(
                buffer = bufferStats,
                clients = clientStats,
                config = ConfigInfo(
                    poll_interval = Config.POLL_INTERVAL_MS / 1000,
                    batch_size = Config.BATCH_SIZE,
                    buffer_size = Config.CERTIFICATE_BUFFER_SIZE,
                    client_ping_timeout = Config.CLIENT_PING_TIMEOUT_MS / 1000
                )
            )
            
            call.respond(stats)
        } catch (e: Exception) {
            logger.error(e) { "Error handling /stats" }
            call.respond(
                io.ktor.http.HttpStatusCode.InternalServerError,
                ErrorResponse("Internal server error")
            )
        }
    }
    
    /**
     * Handle /health endpoint.
     * 
     * Returns server health status.
     */
    private suspend fun handleHealth(call: ApplicationCall) {
        call.respond(HealthResponse("ok"))
    }
}

@Serializable
data class LatestResponse(
    val certificates: List<com.certstream.models.CertificateUpdate>,
    val count: Int
)

@Serializable
data class ErrorResponse(
    val error: String
)

@Serializable
data class StatsResponse(
    val buffer: BufferStats,
    val clients: ClientStats,
    val config: ConfigInfo
)

@Serializable
data class ConfigInfo(
    val poll_interval: Long,
    val batch_size: Int,
    val buffer_size: Int,
    val client_ping_timeout: Long
)

@Serializable
data class HealthResponse(
    val status: String
)
