package com.certstream

import com.certstream.models.CertificateUpdate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Thread-safe ring buffer for storing the most recent certificates.
 */
class CertificateBuffer(private val maxSize: Int = Config.CERTIFICATE_BUFFER_SIZE) {
    private val buffer = ConcurrentLinkedDeque<CertificateUpdate>()
    private val lock = Mutex()
    
    // Statistics
    @Volatile
    private var totalCertificates = 0L
    private val startTime = Instant.now()
    
    /**
     * Add a certificate to the buffer.
     */
    suspend fun add(certificate: CertificateUpdate) {
        lock.withLock {
            buffer.addLast(certificate)
            totalCertificates++
            
            // Remove oldest if buffer is full
            while (buffer.size > maxSize) {
                buffer.removeFirst()
            }
        }
    }
    
    /**
     * Get the most recent certificates.
     * 
     * @param count Number of certificates to retrieve (default: all)
     * @return List of certificates, most recent first
     */
    suspend fun getLatest(count: Int? = null): List<CertificateUpdate> {
        return lock.withLock {
            val actualCount = count ?: buffer.size
            buffer.toList().takeLast(actualCount).reversed()
        }
    }
    
    /**
     * Get an example certificate (the most recent one).
     * 
     * @return Most recent certificate or null if buffer is empty
     */
    suspend fun getExample(): CertificateUpdate? {
        return lock.withLock {
            buffer.lastOrNull()
        }
    }
    
    /**
     * Get buffer statistics.
     * 
     * @return Map containing buffer statistics
     */
    suspend fun getStats(): BufferStats {
        return lock.withLock {
            val uptime = (Instant.now().toEpochMilli() - startTime.toEpochMilli()) / 1000.0
            val certsPerSecond = if (uptime > 0) totalCertificates / uptime else 0.0
            
            BufferStats(
                bufferSize = buffer.size,
                maxBufferSize = maxSize,
                totalCertificatesProcessed = totalCertificates,
                uptimeSeconds = uptime,
                certificatesPerSecond = String.format("%.2f", certsPerSecond).toDouble(),
                startTime = startTime.toString()
            )
        }
    }
    
    /**
     * Clear the buffer.
     */
    suspend fun clear() {
        lock.withLock {
            buffer.clear()
        }
    }
}

@Serializable
data class BufferStats(
    val bufferSize: Int,
    val maxBufferSize: Int,
    val totalCertificatesProcessed: Long,
    val uptimeSeconds: Double,
    val certificatesPerSecond: Double,
    val startTime: String
)
