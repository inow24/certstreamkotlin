package com.certstream

import com.certstream.models.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Represents a connected websocket client.
 */
class WebSocketClient(
    val session: DefaultWebSocketServerSession,
    val streamType: StreamType
) {
    private val queue = Channel<String>(Config.CLIENT_QUEUE_SIZE)
    
    @Volatile
    var lastPing = Instant.now()
    
    @Volatile
    var connected = true
    
    /**
     * Send a message to the client.
     */
    suspend fun send(message: String) {
        try {
            // Try to send to channel without blocking
            if (!queue.trySend(message).isSuccess) {
                // Queue is full - implement load shedding
                logger.warn { "Client queue full, dropping message for ${session.call.request.local.remoteHost}" }
                // Drop oldest message and add new one
                queue.tryReceive()
                queue.trySend(message)
            }
        } catch (e: Exception) {
            logger.debug(e) { "Error sending to queue" }
        }
    }
    
    /**
     * Send messages from queue to websocket.
     */
    suspend fun sendLoop() {
        try {
            while (connected) {
                try {
                    // Get message from queue with timeout
                    val message = withTimeoutOrNull(1000) {
                        queue.receive()
                    }
                    
                    if (message != null) {
                        session.send(Frame.Text(message))
                    }
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    logger.debug(e) { "Error in send loop" }
                    break
                }
            }
        } finally {
            connected = false
        }
    }
    
    /**
     * Monitor client ping/pong.
     */
    suspend fun pingLoop() {
        try {
            while (connected) {
                delay(Config.CLIENT_PING_TIMEOUT_MS)
                
                // Check if client has sent a ping recently
                val timeSincePing = (Instant.now().toEpochMilli() - lastPing.toEpochMilli()) / 1000.0
                
                if (timeSincePing > Config.CLIENT_PING_TIMEOUT_MS / 1000.0) {
                    logger.info { "Client timeout (no ping for ${timeSincePing}s): ${session.call.request.local.remoteHost}" }
                    session.close(CloseReason(CloseReason.Codes.NORMAL, "Timeout"))
                    connected = false
                    break
                }
            }
        } catch (e: Exception) {
            logger.debug(e) { "Error in ping loop" }
        } finally {
            connected = false
        }
    }
    
    /**
     * Update last ping timestamp.
     */
    fun updatePing() {
        lastPing = Instant.now()
    }
}

/**
 * Manages websocket clients and broadcasts certificates.
 */
class ClientManager(private val certBuffer: CertificateBuffer) {
    private val clients = mutableMapOf<StreamType, MutableSet<WebSocketClient>>().apply {
        put(StreamType.FULL, mutableSetOf())
        put(StreamType.LITE, mutableSetOf())
        put(StreamType.DOMAINS_ONLY, mutableSetOf())
    }
    
    private val lock = Mutex()
    private val json = Json { encodeDefaults = true }
    
    /**
     * Add a new client.
     */
    suspend fun addClient(
        session: DefaultWebSocketServerSession,
        streamType: StreamType
    ): WebSocketClient? {
        return lock.withLock {
            // Check client limit
            val clientSet = clients[streamType]!!
            if (clientSet.size >= Config.MAX_CLIENTS_PER_ENDPOINT) {
                logger.warn { "Max clients reached for ${streamType.name} stream" }
                session.close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Max clients reached"))
                return null
            }
            
            val client = WebSocketClient(session, streamType)
            clientSet.add(client)
            
            logger.info {
                "Client connected to ${streamType.name} stream: " +
                "${session.call.request.local.remoteHost} " +
                "(total: ${clientSet.size})"
            }
            
            client
        }
    }
    
    /**
     * Remove a client.
     */
    suspend fun removeClient(client: WebSocketClient) {
        lock.withLock {
            val clientSet = clients[client.streamType]!!
            if (clientSet.remove(client)) {
                logger.info {
                    "Client disconnected from ${client.streamType.name} stream: " +
                    "${client.session.call.request.local.remoteHost} " +
                    "(total: ${clientSet.size})"
                }
            }
        }
    }
    
    /**
     * Broadcast a certificate to all connected clients.
     */
    suspend fun broadcastCertificate(certificate: CertificateUpdate) {
        // Add to buffer
        certBuffer.add(certificate)
        
        // Broadcast to clients based on stream type
        lock.withLock {
            // Full stream - send as-is
            val fullJson = json.encodeToString(certificate)
            clients[StreamType.FULL]?.forEach { client ->
                client.send(fullJson)
            }
            
            // Lite stream - remove DER data
            if (clients[StreamType.LITE]?.isNotEmpty() == true) {
                val liteCert = CertificateParser.createLiteFormat(certificate)
                val liteJson = json.encodeToString(liteCert)
                clients[StreamType.LITE]?.forEach { client ->
                    client.send(liteJson)
                }
            }
            
            // Domains-only stream
            if (clients[StreamType.DOMAINS_ONLY]?.isNotEmpty() == true) {
                val domainsCert = CertificateParser.createDomainsOnlyFormat(certificate)
                val domainsJson = json.encodeToString(domainsCert)
                clients[StreamType.DOMAINS_ONLY]?.forEach { client ->
                    client.send(domainsJson)
                }
            }
        }
    }
    
    /**
     * Handle a websocket client connection.
     */
    suspend fun handleClient(
        session: DefaultWebSocketServerSession,
        streamType: StreamType
    ) {
        val client = addClient(session, streamType) ?: return
        
        try {
            // Start send and ping loops
            val sendJob = CoroutineScope(Dispatchers.IO).launch {
                client.sendLoop()
            }
            
            val pingJob = CoroutineScope(Dispatchers.IO).launch {
                client.pingLoop()
            }
            
            // Handle incoming messages (pings)
            for (frame in session.incoming) {
                try {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        val message = json.decodeFromString<PingMessage>(text)
                        
                        // Handle ping messages
                        if (message.message_type == "ping") {
                            client.updatePing()
                            session.send(Frame.Text(json.encodeToString(PongMessage())))
                        }
                    }
                } catch (e: Exception) {
                    logger.debug(e) { "Error handling client message" }
                }
            }
            
            // Cancel tasks
            sendJob.cancel()
            pingJob.cancel()
            
            sendJob.join()
            pingJob.join()
        } catch (e: Exception) {
            logger.debug(e) { "Error handling client" }
        } finally {
            removeClient(client)
        }
    }
    
    /**
     * Get client statistics.
     */
    suspend fun getStats(): ClientStats {
        return lock.withLock {
            ClientStats(
                clients = ClientCounts(
                    full_stream = clients[StreamType.FULL]?.size ?: 0,
                    lite_stream = clients[StreamType.LITE]?.size ?: 0,
                    domains_only_stream = clients[StreamType.DOMAINS_ONLY]?.size ?: 0,
                    total = clients.values.sumOf { it.size }
                ),
                max_clients_per_endpoint = Config.MAX_CLIENTS_PER_ENDPOINT
            )
        }
    }
}

@kotlinx.serialization.Serializable
data class ClientStats(
    val clients: ClientCounts,
    val max_clients_per_endpoint: Int
)

@kotlinx.serialization.Serializable
data class ClientCounts(
    val full_stream: Int,
    val lite_stream: Int,
    val domains_only_stream: Int,
    val total: Int
)
