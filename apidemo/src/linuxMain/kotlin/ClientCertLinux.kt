package apidemo

// Linux: Ktor bundles an OpenSSL-backed libcurl, which reads PEM / DER / PKCS#12
// certificate and key files directly — no certificate-store dance needed.

/** Point libcurl straight at the certificate / key files. */
actual fun prepareClientCert(inReq: ApiRequest): PreparedCert =
	PreparedCert(
		sslCert     = inReq.certPath,
		sslCertType = inReq.certFormat.curlName,
		sslKey      = inReq.keyPath.ifBlank { null },
		sslKeyType  = if (inReq.keyPath.isNotBlank()) inReq.keyFormat.curlName else null,
		keyPassword = inReq.certPassword.ifEmpty { null },
		cleanup     = {},
	)

/** No temporary certificate store off Windows. */
actual fun sweepTempClientCerts() {}

/** No OS-store issuer resolution here — just continue with the name-only issuer. */
actual fun extendChain(inServerCerts: List<List<Pair<String, String>>>): List<ChainCert> =
	serverChainWithIssuerName(inServerCerts)
