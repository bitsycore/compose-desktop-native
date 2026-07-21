package apidemo

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import com.compose.sdl.icons.MaterialSymbols
import com.compose.sdl.icons.material.symbols.MaterialSymbolsOutlined
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

// ==================
// MARK: TLS certificate-chain dialog + cert cards
// ==================

/** Dialog showing the server's TLS certificate chain (one card per cert, plus a
Copy PEM action). Fetched on demand by the lock button in the URL bar. */
@Composable
internal fun TlsChainDialog(inChain: TlsChain?, inUrl: String, inOnDismiss: () -> Unit) {
    val c = LocalAppColors.current
    Dialog(onDismissRequest = inOnDismiss) {
        Surface(color = c.panel, shape = RoundedCornerShape(10.dp), modifier = Modifier.width(540.dp)) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MaterialSymbolsOutlined(MaterialSymbols.Lock, tint = c.accent, size = 20.dp)
                    Text("TLS certificate chain", color = c.text, fontSize = 16.sp)
                }
                val vCerts = inChain?.certs.orEmpty()
                when {
                    inChain == null -> Text("Probing…", color = c.dim, fontSize = 13.sp)
                    vCerts.isEmpty() -> Text(
                        inChain.error ?: "No chain reported.",
                        color = VolticTheme.extended.warning,
                        fontSize = 13.sp
                    )

                    else -> {
                        Text(
                            "${vCerts.size} certificate(s) presented by ${hostOf(inUrl)}",
                            color = c.dim,
                            fontSize = 12.sp
                        )
                        Column(
                            modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) { vCerts.forEachIndexed { vI, vCert -> CertCard(vI, vCert) } }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val vChainPem = vCerts.mapNotNull { certField(it.fields, "Cert") }.joinToString("\n")
                        if (vChainPem.isNotEmpty()) CopyButton(vChainPem, "Copy chain")
                        OutlinedButton(onClick = inOnDismiss) { Text("Close", color = c.text) }
                    }
                }
            }
        }
    }
}

/** One certificate's summary card (subject / issuer / validity). Server-presented
certs get a solid border; derived ones (issuer pulled from the OS store, or a
name-only placeholder) get an accent-coloured border + a label. A self-signed
cert shows "Self-signed" in green instead of repeating its issuer. */
@Composable
internal fun CertCard(inIndex: Int, inCert: ChainCert) {
    val c = LocalAppColors.current
    val vFields = inCert.fields
    val vSubject = certField(vFields, "Subject")
    val vIssuer = certField(vFields, "Issuer")
    val vSelfSigned = vIssuer != null && vIssuer == vSubject
    val vPem = certField(vFields, "Cert")
    // Backends like Schannel only expose Subject/Issuer/PEM via CURLINFO_CERTINFO,
    // so parse the rest (dates, serial, SAN, algorithms) straight from the PEM.
    val vParsed = remember(vPem) { vPem?.let { parseCertDetails(it) } }
    val vFrom = certField(vFields, "Start date") ?: certField(vFields, "Start Date") ?: vParsed?.notBefore
    val vTo = certField(vFields, "Expire date") ?: certField(vFields, "Expire Date") ?: vParsed?.notAfter
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(c.field.copy(alpha = if (inCert.fromServer) 1f else 0.4f), RoundedCornerShape(8.dp))
            .border(
                if (inCert.fromServer) 1.dp else 1.5.dp,
                if (inCert.fromServer) c.border else c.accent,
                RoundedCornerShape(8.dp)
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        val vTitle = vSubject?.let { cnOf(it) } ?: if (inIndex == 0) "Leaf certificate" else "Issuer #$inIndex"
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(vTitle, color = c.accent, fontSize = 12.sp, modifier = Modifier.weight(1f))
            if (!inCert.fromServer) Text(
                if (vPem != null) "from OS store" else "not presented",
                color = c.dim,
                fontSize = 10.sp
            )
            if (vPem != null) CopyButton(vPem)
        }
        vSubject?.let { CertLine("Subject", it) }
        if (vSelfSigned) CertLine("Issuer", "Self-signed", kSelfSignedColor)
        else vIssuer?.let { CertLine("Issuer", it) }
        // Hosts this cert is valid for — the field that actually has to match the URL.
        (certField(vFields, "X509v3 Subject Alternative Name")
            ?: certField(vFields, "Subject Alternative Name")
            ?: vParsed?.sans?.takeIf { it.isNotEmpty() }?.joinToString(", "))
            ?.let { CertLine("SAN", it) }
        vFrom?.let { CertLine("Issued", it) }
        vTo?.let { CertLine("Expires", it) }
        (certField(vFields, "Serial Number") ?: vParsed?.serial)?.let { CertLine("Serial", it) }
        (certField(vFields, "Signature Algorithm") ?: vParsed?.sigAlg)?.let { CertLine("Signature", it) }
        (certField(vFields, "Public Key Algorithm") ?: vParsed?.keyAlg)?.let { CertLine("Key", it) }
        (certField(vFields, "Version") ?: vParsed?.version?.let { "v$it" })?.let { CertLine("Version", it) }
    }
}

internal val kSelfSignedColor = Color(0xFF3FB950L)

/** Copy button — rounded, hover overlay, real click that copies inText and shows
a green check. Icon-only (per cert) flashes a small floating "Copied" bubble
for 2s; the labelled variant (Copy chain) flips its label to "Copied" instead.
Uses a non-catching Popup so it never dismisses the dialog or eats clicks. */
@Composable
internal fun CopyButton(inText: String, inLabel: String? = null) {
    val c = LocalAppColors.current
    var vCopied by remember { mutableStateOf(false) }
    val vHoverSrc = remember { MutableInteractionSource() }
    val vHover by vHoverSrc.collectIsHoveredAsState()
    var vX by remember { mutableStateOf(0) }
    var vY by remember { mutableStateOf(0) }
    var vHeight by remember { mutableStateOf(0) }
    val vClipboard = LocalClipboard.current
    val vScope = rememberCoroutineScope()
    LaunchedEffect(vCopied) {
        if (vCopied) {
            delay(2000.milliseconds); vCopied = false
        }
    }
    Row(
        modifier = Modifier
            .onGloballyPositioned { vX = it.x; vY = it.y }
            .onSizeChanged { vHeight = it.height }
            .clip(RoundedCornerShape(7.dp))
            .background(if (vHover) c.accent.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(7.dp))
            .hoverable(vHoverSrc)
            .clickable {
                vScope.launch { vClipboard.setClipEntry(clipEntryOfText(inText)) }
                vCopied = true
            }
            .padding(horizontal = if (inLabel != null) 10.dp else 5.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        MaterialSymbolsOutlined(
            if (vCopied) MaterialSymbols.Check else MaterialSymbols.ContentCopy,
            contentDescription = inLabel ?: "Copy PEM",
            tint = if (vCopied) kSelfSignedColor else c.dim,
            size = 15.dp,
        )
        if (inLabel != null) Text(
            if (vCopied) "Copied" else inLabel,
            color = if (vCopied) kSelfSignedColor else c.text,
            fontSize = 12.sp
        )
    }
    // Icon-only: float a tiny "Copied" bubble (non-catching popup → no dismiss).
    if (vCopied && inLabel == null) {
        Popup {
            Box(
                modifier = Modifier.offset(vX.dp, (vY + vHeight + 4).dp)
                    .clip(RoundedCornerShape(4.dp)).background(Color(0xE6111111L), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) { Text("Copied", color = Color.White, fontSize = 11.sp) }
        }
    }
}

@Composable
internal fun CertLine(inLabel: String, inValue: String, inValueColor: Color? = null) {
    val c = LocalAppColors.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(inLabel, color = c.dim, fontSize = 11.sp, modifier = Modifier.width(64.dp))
        Text(inValue, color = inValueColor ?: c.text, fontSize = 12.sp, modifier = Modifier.weight(1f))
    }
}

/** Look up a CURLINFO_CERTINFO field by name (case-insensitive). */
internal fun certField(inFields: List<Pair<String, String>>, inName: String): String? =
    inFields.firstOrNull { it.first.equals(inName, ignoreCase = true) }?.second

/** The CN= value out of a distinguished-name string. Handles every
format the platform backends emit:
"CN=R3, O=Let's Encrypt, C=US"        (Windows CertGetNameStringA)
"/C=US/O=Let's Encrypt/CN=R3"          (OpenSSL X509_NAME_oneline)
"CN = R3, O = Let's Encrypt, C = US"   (OpenSSL X509_NAME_print_ex
default, with spaces — what
libcurl on macOS emits)
Null when there's no CN. */
internal fun cnOf(inDn: String): String? {
    val vMatch = Regex("""(?i)\bCN\s*=\s*([^,/]+)""").find(inDn) ?: return null
    return vMatch.groupValues[1].trim().ifBlank { null }
}

/** The host portion of a URL (for the chain dialog header). */
internal fun hostOf(inUrl: String): String =
    inUrl.substringAfter("://", inUrl).substringBefore("/").substringBefore("?").ifBlank { inUrl }
