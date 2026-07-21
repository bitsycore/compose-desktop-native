package apidemo

// ==================
// MARK: TLS chain / client-certificate seam
// ==================
// The real implementations drive the bundled libcurl directly (CurlMtls.kt,
// nativeMain) — Ktor's engines expose no client-cert / CERTINFO API. The jvm
// parity app has no bundled libcurl, so its actuals report the feature as
// native-only instead.

/** One certificate in the chain: its CURLINFO_CERTINFO-style fields (Subject /
Issuer / the PEM under "Cert" / dates …) and whether the server actually
presented it. Derived certs — an issuer resolved from the OS store, or a
name-only placeholder — have fromServer = false and are drawn dotted. */
class ChainCert(val fields: List<Pair<String, String>>, val fromServer: Boolean)

/** The server's TLS certificate chain. error is set instead when it couldn't be
fetched; fields vary by TLS backend. */
class TlsChain(val certs: List<ChainCert>, val error: String?)

/** Fetch the TLS certificate chain the request's server presents. */
expect fun inspectTlsChain(inReq: ApiRequest): TlsChain

/** Send the request with its client certificate attached (mTLS). */
expect fun curlSendWithClientCert(inReq: ApiRequest): ApiResponse

/** Remove temp client certs a previous crashed run left behind. Call once at
startup; no-op where the OS store isn't used. */
expect fun sweepTempClientCerts()
