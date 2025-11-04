package com.certstream

import com.certstream.models.*
import mu.KotlinLogging
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Instant
import java.util.*
import javax.security.auth.x500.X500Principal

private val logger = KotlinLogging.logger {}

/**
 * Parses X.509 certificates and extracts relevant fields.
 */
object CertificateParser {
    
    private val certificateFactory = CertificateFactory.getInstance("X.509")
    
    /**
     * Parse a CT log entry and extract certificate information.
     * 
     * @param entry Raw CT log entry containing leaf_input and extra_data
     * @param logInfo Information about the source CT log
     * @param index Certificate index in the log
     * @return Parsed certificate data in CertStream format, or null if parsing fails
     */
    fun parseCertificateEntry(
        entry: CTLogEntry,
        logInfo: CTLogInfo,
        index: Long
    ): CertificateUpdate? {
        return try {
            // Decode the certificate and extra_data from base64
            val leafInput = Base64.getDecoder().decode(entry.leaf_input)
            val extraData = Base64.getDecoder().decode(entry.extra_data)
            
            // Extract certificate from leaf_input
            val certData = extractCertFromLeaf(leafInput) ?: run {
                logger.debug { "Could not extract cert data from leaf_input (len=${leafInput.size})" }
                return null
            }
            
            // Parse the X.509 certificate
            val cert = certificateFactory.generateCertificate(
                ByteArrayInputStream(certData)
            ) as X509Certificate
            
            // Extract all domains (CN + SANs)
            val allDomains = extractDomains(cert)
            
            // Build the certificate data structure
            CertificateUpdate(
                data = CertificateData(
                    leaf_cert = LeafCertificate(
                        subject = extractSubject(cert),
                        extensions = extractExtensions(cert),
                        not_before = cert.notBefore.time / 1000.0,
                        not_after = cert.notAfter.time / 1000.0,
                        serial_number = cert.serialNumber.toString(),
                        fingerprint = calculateFingerprint(certData),
                        as_der = Base64.getEncoder().encodeToString(certData),
                        all_domains = allDomains
                    ),
                    chain = extractChain(extraData),
                    cert_index = index,
                    seen = Instant.now().toEpochMilli() / 1000.0,
                    source = Source(
                        url = logInfo.url,
                        name = logInfo.description
                    )
                )
            )
        } catch (e: Exception) {
            logger.debug(e) { "Failed to parse certificate entry" }
            null
        }
    }
    
    /**
     * Extract certificate data from CT log leaf input.
     */
    private fun extractCertFromLeaf(leafInput: ByteArray): ByteArray? {
        return try {
            // MerkleTreeLeaf structure:
            // - version (1 byte)
            // - leaf_type (1 byte)
            // - timestamp (8 bytes)
            // - entry_type (2 bytes): 0 = X509Entry, 1 = PrecertEntry
            // - certificate_length (3 bytes)
            // - certificate (variable)
            
            if (leafInput.size < 15) { // Minimum header size
                return null
            }
            
            // Check entry type (bytes 10-11)
            val entryType = ((leafInput[10].toInt() and 0xFF) shl 8) or 
                           (leafInput[11].toInt() and 0xFF)
            
            // For precerts (entry_type = 1), extract the TBS certificate
            if (entryType == 1) {
                // Precert structure after entry_type:
                // - issuer_key_hash (32 bytes)
                // - tbs_certificate_length (3 bytes)
                // - tbs_certificate (DER-encoded X.509 certificate)
                if (leafInput.size < 47) { // 12 + 32 + 3
                    return null
                }
                
                // Get TBS certificate length (3 bytes at offset 44)
                val tbsCertLength = ((leafInput[44].toInt() and 0xFF) shl 16) or
                                   ((leafInput[45].toInt() and 0xFF) shl 8) or
                                   (leafInput[46].toInt() and 0xFF)
                val tbsCertStart = 47
                val tbsCertEnd = tbsCertStart + tbsCertLength
                
                if (leafInput.size < tbsCertEnd) {
                    return null
                }
                
                return leafInput.copyOfRange(tbsCertStart, tbsCertEnd)
            }
            
            // For X509Entry (entry_type = 0)
            // Certificate length starts at byte 12
            val certLength = ((leafInput[12].toInt() and 0xFF) shl 16) or
                           ((leafInput[13].toInt() and 0xFF) shl 8) or
                           (leafInput[14].toInt() and 0xFF)
            
            // Certificate data starts at byte 15
            val certStart = 15
            val certEnd = certStart + certLength
            
            if (leafInput.size < certEnd) {
                logger.debug { "Leaf input too short: ${leafInput.size} < $certEnd" }
                return null
            }
            
            leafInput.copyOfRange(certStart, certEnd)
        } catch (e: Exception) {
            logger.debug(e) { "Failed to extract cert from leaf" }
            null
        }
    }
    
    /**
     * Extract all domains from certificate (CN + SANs).
     */
    private fun extractDomains(cert: X509Certificate): List<String> {
        val domains = mutableListOf<String>()
        
        // Extract CN from subject
        try {
            val subjectDN = cert.subjectX500Principal.name
            val cn = extractCNFromDN(subjectDN)
            if (cn != null && cn !in domains) {
                domains.add(cn)
            }
        } catch (e: Exception) {
            // Ignore
        }
        
        // Extract SANs
        try {
            val sans = cert.subjectAlternativeNames
            sans?.forEach { san ->
                // SAN type 2 is DNS name
                if (san[0] == 2) {
                    val dnsName = san[1].toString()
                    if (dnsName !in domains) {
                        domains.add(dnsName)
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        
        return domains
    }
    
    /**
     * Extract CN from DN string.
     */
    private fun extractCNFromDN(dn: String): String? {
        return dn.split(",")
            .map { it.trim() }
            .firstOrNull { it.startsWith("CN=") }
            ?.substring(3)
    }
    
    /**
     * Extract subject fields from certificate.
     */
    private fun extractSubject(cert: X509Certificate): Map<String, String> {
        val subject = mutableMapOf<String, String>()
        
        try {
            val subjectDN = cert.subjectX500Principal.name
            val parts = subjectDN.split(",").map { it.trim() }
            
            for (part in parts) {
                val keyValue = part.split("=", limit = 2)
                if (keyValue.size == 2) {
                    subject[keyValue[0]] = keyValue[1]
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        
        return subject
    }
    
    /**
     * Extract certificate extensions.
     */
    private fun extractExtensions(cert: X509Certificate): Map<String, String> {
        val extensions = mutableMapOf<String, String>()
        
        try {
            // Key Usage
            cert.keyUsage?.let {
                extensions["keyUsage"] = it.joinToString(",")
            }
            
            // Extended Key Usage
            cert.extendedKeyUsage?.let {
                extensions["extendedKeyUsage"] = it.joinToString(",")
            }
            
            // Basic Constraints
            cert.basicConstraints.let { bc ->
                if (bc >= 0) {
                    extensions["basicConstraints"] = "CA:true"
                } else if (bc == -1) {
                    extensions["basicConstraints"] = "CA:false"
                }
            }
            
            // Subject Alternative Names
            cert.subjectAlternativeNames?.let { sans ->
                val sanList = sans.map { san ->
                    when (san[0]) {
                        2 -> "DNS:${san[1]}"
                        else -> san[1].toString()
                    }
                }
                extensions["subjectAltName"] = sanList.joinToString(",")
            }
        } catch (e: Exception) {
            // Ignore
        }
        
        return extensions
    }
    
    /**
     * Calculate certificate fingerprint.
     */
    private fun calculateFingerprint(certData: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(certData)
        return digest.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Extract certificate chain from extra_data.
     */
    private fun extractChain(extraData: ByteArray): List<ChainCertificate> {
        val chain = mutableListOf<ChainCertificate>()
        
        try {
            var offset = 0
            
            // Read chain length (3 bytes)
            if (extraData.size < 3) {
                return chain
            }
            
            val chainLength = ((extraData[offset].toInt() and 0xFF) shl 16) or
                            ((extraData[offset + 1].toInt() and 0xFF) shl 8) or
                            (extraData[offset + 2].toInt() and 0xFF)
            offset += 3
            
            // Parse each certificate in the chain
            while (offset < extraData.size && offset < chainLength + 3) {
                // Read cert length (3 bytes)
                if (offset + 3 > extraData.size) {
                    break
                }
                
                val certLength = ((extraData[offset].toInt() and 0xFF) shl 16) or
                               ((extraData[offset + 1].toInt() and 0xFF) shl 8) or
                               (extraData[offset + 2].toInt() and 0xFF)
                offset += 3
                
                if (offset + certLength > extraData.size) {
                    break
                }
                
                val certData = extraData.copyOfRange(offset, offset + certLength)
                offset += certLength
                
                // Parse the certificate
                try {
                    val cert = certificateFactory.generateCertificate(
                        ByteArrayInputStream(certData)
                    ) as X509Certificate
                    
                    chain.add(
                        ChainCertificate(
                            subject = extractSubject(cert),
                            as_der = Base64.getEncoder().encodeToString(certData)
                        )
                    )
                } catch (e: Exception) {
                    // Ignore individual cert parsing errors
                }
            }
        } catch (e: Exception) {
            logger.debug(e) { "Failed to extract chain" }
        }
        
        return chain
    }
    
    /**
     * Create lite format by removing DER-encoded certificate data.
     */
    fun createLiteFormat(fullCert: CertificateUpdate): CertificateUpdate {
        return fullCert.copy(
            data = fullCert.data.copy(
                leaf_cert = fullCert.data.leaf_cert.copy(as_der = null),
                chain = fullCert.data.chain.map { it.copy(as_der = null) }
            )
        )
    }
    
    /**
     * Create domains-only format with just domain names.
     */
    fun createDomainsOnlyFormat(fullCert: CertificateUpdate): DomainsOnlyUpdate {
        return DomainsOnlyUpdate(
            data = DomainsData(
                domains = fullCert.data.leaf_cert.all_domains,
                seen = fullCert.data.seen,
                source = fullCert.data.source
            )
        )
    }
}
