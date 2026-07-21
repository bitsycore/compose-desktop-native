package apidemo

// ==================
// MARK: TLS chain / client-certificate — JVM actuals (native-only feature)
// ==================
// The native builds drive the bundled libcurl (CURLOPT_SSLCERT / CERTINFO);
// there is no libcurl on the jvm parity stack, so both entry points report
// the limitation instead of half-implementing it over JSSE.

private const val kNativeOnly =
    "Client-certificate (mTLS) features need the native build — they drive the bundled libcurl directly."

actual fun inspectTlsChain(inReq: ApiRequest): TlsChain = TlsChain(emptyList(), kNativeOnly)

actual fun curlSendWithClientCert(inReq: ApiRequest): ApiResponse = ApiResponse(
    ok = false,
    status = 0,
    statusText = "",
    timeMs = 0,
    sizeBytes = 0,
    headers = emptyList(),
    body = "",
    error = kNativeOnly,
)

actual fun sweepTempClientCerts() {}
