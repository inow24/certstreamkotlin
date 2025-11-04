package com.certstream.models

import kotlinx.serialization.Serializable

@Serializable
data class CTLogInfo(
    val url: String,
    val description: String
)

@Serializable
data class CTLogListResponse(
    val operators: List<Operator>
)

@Serializable
data class Operator(
    val logs: List<LogEntry>
)

@Serializable
data class LogEntry(
    val url: String,
    val description: String,
    val state: State? = null
)

@Serializable
data class State(
    val usable: UsableState? = null
)

@Serializable
data class UsableState(
    val timestamp: String? = null
)

@Serializable
data class SignedTreeHead(
    val tree_size: Long,
    val timestamp: Long,
    val sha256_root_hash: String,
    val tree_head_signature: String
)

@Serializable
data class CTLogEntries(
    val entries: List<CTLogEntry>
)

@Serializable
data class CTLogEntry(
    val leaf_input: String,
    val extra_data: String
)

@Serializable
data class CertificateUpdate(
    val message_type: String = "certificate_update",
    val data: CertificateData
)

@Serializable
data class CertificateData(
    val update_type: String = "X509LogEntry",
    val leaf_cert: LeafCertificate,
    val chain: List<ChainCertificate>,
    val cert_index: Long,
    val seen: Double,
    val source: Source
)

@Serializable
data class LeafCertificate(
    val subject: Map<String, String>,
    val extensions: Map<String, String>,
    val not_before: Double,
    val not_after: Double,
    val serial_number: String,
    val fingerprint: String,
    val as_der: String? = null,
    val all_domains: List<String>
)

@Serializable
data class ChainCertificate(
    val subject: Map<String, String>,
    val as_der: String? = null
)

@Serializable
data class Source(
    val url: String,
    val name: String
)

@Serializable
data class DomainsOnlyUpdate(
    val message_type: String = "certificate_update",
    val data: DomainsData
)

@Serializable
data class DomainsData(
    val domains: List<String>,
    val seen: Double,
    val source: Source
)

@Serializable
data class PingMessage(
    val message_type: String = "ping"
)

@Serializable
data class PongMessage(
    val message_type: String = "pong"
)

enum class StreamType {
    FULL, LITE, DOMAINS_ONLY
}
