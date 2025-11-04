package com.certstream

import com.certstream.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Watches a single CT log for new certificate entries.
 */
class CTLogWatcher(
    private val logInfo: CTLogInfo,
    private val certificateCallback: suspend (CertificateUpdate) -> Unit
) {
    private val logUrl = logInfo.url.trimEnd('/')
    private val logName = logInfo.description
    
    @Volatile
    private var currentTreeSize = 0L
    
    @Volatile
    private var running = false
    
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        engine {
            requestTimeout = 30_000
        }
    }
    
    /**
     * Start watching the CT log.
     */
    suspend fun start() {
        running = true
        logger.info { "Started watcher for $logName" }
        
        // Initialize tree size
        updateTreeSize()
        
        // Start polling loop
        while (running) {
            try {
                pollForUpdates()
                delay(Config.POLL_INTERVAL_MS)
            } catch (e: CancellationException) {
                break
            } catch (e: Exception) {
                logger.error(e) { "Error in watcher for $logName" }
                delay(Config.POLL_INTERVAL_MS)
            }
        }
    }
    
    /**
     * Stop watching the CT log.
     */
    suspend fun stop() {
        running = false
        client.close()
        logger.info { "Stopped watcher for $logName" }
    }
    
    /**
     * Query the CT log for current tree size.
     * 
     * @return True if successful, False otherwise
     */
    private suspend fun updateTreeSize(): Boolean {
        return try {
            val url = "$logUrl/ct/v1/get-sth"
            
            val response = client.get(url)
            if (response.status.value != 200) {
                logger.warn { "Failed to get STH from $logName: HTTP ${response.status.value}" }
                return false
            }
            
            val data = response.body<SignedTreeHead>()
            val newTreeSize = data.tree_size
            
            if (newTreeSize > currentTreeSize) {
                logger.debug { "$logName: Tree size updated $currentTreeSize -> $newTreeSize" }
                return true
            }
            
            false
        } catch (e: Exception) {
            logger.warn(e) { "Failed to update tree size for $logName" }
            false
        }
    }
    
    /**
     * Poll the CT log for new certificates.
     */
    private suspend fun pollForUpdates() {
        try {
            // Get current tree size
            val url = "$logUrl/ct/v1/get-sth"
            
            val response = client.get(url)
            if (response.status.value != 200) {
                return
            }
            
            val data = response.body<SignedTreeHead>()
            val newTreeSize = data.tree_size
            
            // Check if there are new entries
            if (newTreeSize <= currentTreeSize) {
                return
            }
            
            // Download new entries in batches
            val startIndex = currentTreeSize
            var endIndex = newTreeSize - 1
            
            // Limit batch size to avoid overwhelming the server
            if (endIndex - startIndex > Config.BATCH_SIZE) {
                endIndex = startIndex + Config.BATCH_SIZE - 1
            }
            
            logger.debug { "$logName: Fetching entries $startIndex to $endIndex" }
            
            fetchEntries(startIndex, endIndex)
            
            // Update current tree size
            currentTreeSize = endIndex + 1
        } catch (e: Exception) {
            logger.error(e) { "Error polling $logName" }
        }
    }
    
    /**
     * Fetch certificate entries from the CT log.
     * 
     * @param start Starting index
     * @param end Ending index
     */
    private suspend fun fetchEntries(start: Long, end: Long) {
        try {
            val url = "$logUrl/ct/v1/get-entries?start=$start&end=$end"
            
            val response = client.get(url)
            if (response.status.value != 200) {
                logger.warn { "Failed to get entries from $logName: HTTP ${response.status.value}" }
                return
            }
            
            val data = response.body<CTLogEntries>()
            val entries = data.entries
            
            logger.info { "$logName: Processing ${entries.size} certificates" }
            
            // Parse and broadcast each certificate
            entries.forEachIndexed { idx, entry ->
                processEntry(entry, start + idx)
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch entries from $logName" }
        }
    }
    
    /**
     * Process a single certificate entry.
     * 
     * @param entry Raw CT log entry
     * @param index Certificate index
     */
    private suspend fun processEntry(entry: CTLogEntry, index: Long) {
        try {
            // Parse the certificate
            val parsedCert = CertificateParser.parseCertificateEntry(entry, logInfo, index)
            
            if (parsedCert != null) {
                // Send to callback (ClientManager)
                certificateCallback(parsedCert)
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to process entry" }
        }
    }
}

/**
 * Manages multiple CT log watchers.
 */
class CTLogManager(
    private val certificateCallback: suspend (CertificateUpdate) -> Unit
) {
    private val watchers = mutableListOf<CTLogWatcher>()
    
    @Volatile
    private var running = false
    
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }
    
    /**
     * Start all CT log watchers.
     */
    suspend fun start() = coroutineScope {
        running = true
        
        // Fetch CT log list
        val logList = fetchCTLogList()
        
        if (logList.isEmpty()) {
            logger.error { "Failed to fetch CT log list" }
            return@coroutineScope
        }
        
        logger.info { "Found ${logList.size} CT logs" }
        
        // Limit number of watchers based on config
        val limitedLogList = logList.take(Config.MAX_WORKERS)
        
        // Create and start watchers
        val jobs = limitedLogList.map { logInfo ->
            async {
                val watcher = CTLogWatcher(logInfo, certificateCallback)
                watchers.add(watcher)
                watcher.start()
            }
        }
        
        // Wait for all watchers (they run indefinitely)
        jobs.awaitAll()
    }
    
    /**
     * Stop all CT log watchers.
     */
    suspend fun stop() {
        running = false
        
        logger.info { "Stopping all watchers..." }
        
        // Stop all watchers
        watchers.forEach { watcher ->
            try {
                watcher.stop()
            } catch (e: Exception) {
                logger.error(e) { "Error stopping watcher" }
            }
        }
        
        watchers.clear()
        client.close()
    }
    
    /**
     * Fetch the list of known CT logs from Google.
     * 
     * @return List of CT log info
     */
    private suspend fun fetchCTLogList(): List<CTLogInfo> {
        return try {
            val response = client.get(Config.CT_LOG_LIST_URL)
            if (response.status.value != 200) {
                logger.error { "Failed to fetch CT log list: HTTP ${response.status.value}" }
                return emptyList()
            }
            
            val data = response.body<CTLogListResponse>()
            
            // Extract operator logs
            val logs = mutableListOf<CTLogInfo>()
            for (operator in data.operators) {
                for (log in operator.logs) {
                    // Only include logs that are currently usable
                    if (log.state?.usable != null) {
                        logs.add(
                            CTLogInfo(
                                url = log.url,
                                description = log.description
                            )
                        )
                    }
                }
            }
            
            logs
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch CT log list" }
            emptyList()
        }
    }
}
