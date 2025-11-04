package com.certstream

object Config {
    // Server settings
    const val HOST = "0.0.0.0"
    const val PORT = 9000
    
    // CT Log settings
    const val CT_LOG_LIST_URL = "https://www.gstatic.com/ct/log_list/v3/log_list.json"
    const val POLL_INTERVAL_MS = 10_000L
    const val BATCH_SIZE = 256
    
    // Buffer settings
    const val CERTIFICATE_BUFFER_SIZE = 25
    
    // Client settings
    const val CLIENT_PING_TIMEOUT_MS = 60_000L
    const val MAX_CLIENTS_PER_ENDPOINT = 1000
    const val CLIENT_QUEUE_SIZE = 100
    
    // Performance settings
    const val MAX_WORKERS = 50
}
