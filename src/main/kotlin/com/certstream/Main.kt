package com.certstream

import kotlinx.coroutines.*
import mu.KotlinLogging
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}

/**
 * Main application orchestrator.
 */
class CertStreamApplication {
    private val certBuffer = CertificateBuffer()
    private val clientManager = ClientManager(certBuffer)
    private val ctLogManager = CTLogManager { certificate ->
        clientManager.broadcastCertificate(certificate)
    }
    private val server = CertStreamServer(clientManager, certBuffer)
    
    @Volatile
    private var running = false
    
    /**
     * Start the application.
     */
    suspend fun start() {
        logger.info { "Starting CertStream server..." }
        logger.info { "Configuration:" }
        logger.info { "  - Host: ${Config.HOST}" }
        logger.info { "  - Port: ${Config.PORT}" }
        logger.info { "  - Poll interval: ${Config.POLL_INTERVAL_MS / 1000}s" }
        logger.info { "  - Batch size: ${Config.BATCH_SIZE}" }
        logger.info { "  - Buffer size: ${Config.CERTIFICATE_BUFFER_SIZE}" }
        logger.info { "  - Max workers: ${Config.MAX_WORKERS}" }
        
        running = true
        
        // Start HTTP/WebSocket server
        server.start()
        
        // Start CT log watchers
        logger.info { "Starting CT log watchers..." }
        val watcherJob = CoroutineScope(Dispatchers.Default).launch {
            ctLogManager.start()
        }
        
        logger.info { "CertStream server is running!" }
        logger.info { "HTTP endpoints:" }
        logger.info { "  - http://${Config.HOST}:${Config.PORT}/latest.json" }
        logger.info { "  - http://${Config.HOST}:${Config.PORT}/example.json" }
        logger.info { "  - http://${Config.HOST}:${Config.PORT}/stats" }
        logger.info { "  - http://${Config.HOST}:${Config.PORT}/health" }
        logger.info { "WebSocket endpoints:" }
        logger.info { "  - ws://${Config.HOST}:${Config.PORT + 1}/ (lite stream)" }
        logger.info { "  - ws://${Config.HOST}:${Config.PORT + 2}/ (full stream)" }
        logger.info { "  - ws://${Config.HOST}:${Config.PORT + 3}/ (domains-only stream)" }
        
        // Wait for watcher job
        try {
            watcherJob.join()
        } catch (e: CancellationException) {
            // Expected on shutdown
        }
    }
    
    /**
     * Stop the application.
     */
    suspend fun stop() {
        if (!running) {
            return
        }
        
        logger.info { "Shutting down CertStream server..." }
        running = false
        
        // Stop CT log watchers
        ctLogManager.stop()
        
        // Stop server
        server.stop()
        
        logger.info { "CertStream server stopped" }
    }
}

/**
 * Main entry point.
 */
suspend fun main() {
    val app = CertStreamApplication()
    
    // Setup shutdown hook
    Runtime.getRuntime().addShutdownHook(Thread {
        runBlocking {
            app.stop()
        }
    })
    
    try {
        app.start()
    } catch (e: Exception) {
        logger.error(e) { "Fatal error" }
        exitProcess(1)
    }
}
