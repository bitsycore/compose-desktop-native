package org.jetbrains.compose.resources.vector.xmldom

// ==================
// MARK: DomXmlParser - pure-Kotlin replacement for upstream's Darwin parser
// ==================

/* Upstream's nativeMain DomXmlParser is built on Foundation's NSXMLParser and
   only compiles on Darwin; this port also targets mingwX64/linux, so the same
   `parse(xml): Element` entry point is implemented as a small recursive-descent
   XML parser instead (project file - the upstream one is excluded in
   compose-fork.txt).

   Scope: exactly what XmlVectorParser + the resource string XMLs need -
   elements, namespaced attributes (xmlns scoping, getAttributeNS,
   lookupPrefix), text nodes (textContent), comments / prolog / DOCTYPE /
   CDATA skipping, and the five predefined entities plus numeric character
   references. No DTD expansion, no processing-instruction handling beyond
   skipping - malformed input throws MalformedXMLException like upstream. */
// VENDOR-BASE(COMPOSE_REF): components/resources/library/src/nativeMain/kotlin/org/jetbrains/compose/resources/vector/xmldom/DomXmlParser.kt @ v1.12.1
// (fresh REIMPL, not a copy-edit - the base ref marks the upstream API it tracks)

internal fun parse(xml: String): Element {
	val vParser = XmlDomParser(xml)
	vParser.skipProlog()
	val vRoot = vParser.parseElement(null)
		?: throw MalformedXMLException("no root element")
	return vRoot
}

// One attribute as written: `prefix:local="value"` (prefix empty when none).
private class Attr(val prefix: String, val localName: String, val nsUri: String, val value: String) {
	val qualifiedName: String get() = if (prefix.isEmpty()) localName else "$prefix:$localName"
}

private class NodeListImpl(private val mNodes: List<Node>) : NodeList {
	override fun item(i: Int): Node = mNodes[i]
	override val length: Int get() = mNodes.size
}

private class TextNodeImpl(private val mText: String) : Node {
	override val textContent: String? get() = mText
	override val nodeName: String get() = "#text"
	override val localName: String get() = ""
	override val childNodes: NodeList = NodeListImpl(emptyList())
	override val namespaceURI: String get() = ""
	override fun lookupPrefix(namespaceURI: String): String = ""
}

private class ElementImpl(
	private val mPrefix: String,
	override val localName: String,
	override val namespaceURI: String,
	private val mAttrs: List<Attr>,
	// Innermost-first chain of xmlns scopes: each is prefix -> uri ("" = default ns).
	private val mNsScope: List<Map<String, String>>,
) : Element {
	var children: List<Node> = emptyList()

	override val nodeName: String get() = if (mPrefix.isEmpty()) localName else "$mPrefix:$localName"
	override val childNodes: NodeList get() = NodeListImpl(children)

	// Concatenated descendant text - what DOM's textContent does.
	override val textContent: String?
		get() = buildString {
			for (vChild in children) {
				when (vChild) {
					is TextNodeImpl -> append(vChild.textContent)
					is ElementImpl -> append(vChild.textContent ?: "")
				}
			}
		}

	override fun lookupPrefix(namespaceURI: String): String {
		if (namespaceURI.isEmpty()) return ""
		for (vScope in mNsScope) {
			for ((vPrefix, vUri) in vScope) {
				if (vUri == namespaceURI && vPrefix.isNotEmpty()) return vPrefix
			}
		}
		return ""
	}

	override fun getAttributeNS(nameSpaceURI: String, localName: String): String =
		mAttrs.firstOrNull { it.nsUri == nameSpaceURI && it.localName == localName }?.value ?: ""

	override fun getAttribute(name: String): String =
		mAttrs.firstOrNull { it.qualifiedName == name }?.value ?: ""
}

// ============
//  The parser - one pass over the string with a cursor.
private class XmlDomParser(private val mXml: String) {
	private var mPos = 0

	fun skipProlog() {
		while (true) {
			skipWhitespace()
			when {
				lookingAt("﻿") -> mPos++
				lookingAt("<?") -> skipUntil("?>")
				lookingAt("<!--") -> skipUntil("-->")
				lookingAt("<!DOCTYPE") -> skipDoctype()
				else -> return
			}
		}
	}

	/* Parses one element (cursor at its '<'); null when the cursor sits on a
	   closing tag instead. inParentScope = the enclosing xmlns scope chain. */
	fun parseElement(inParentScope: List<Map<String, String>>?): ElementImpl? {
		skipWhitespace()
		if (!lookingAt("<") || lookingAt("</")) return null
		expect("<")
		val vQName = readName()
		if (vQName.isEmpty()) throw MalformedXMLException("empty element name at $mPos")

		// Attributes (collect raw first - xmlns declarations shape the scope
		// that THIS element's own prefix resolves against).
		val vRawAttrs = ArrayList<Pair<String, String>>()   // qualifiedName to value
		while (true) {
			skipWhitespace()
			if (lookingAt("/>") || lookingAt(">")) break
			val vAttrName = readName()
			if (vAttrName.isEmpty()) throw MalformedXMLException("bad attribute at $mPos")
			skipWhitespace(); expect("="); skipWhitespace()
			vRawAttrs.add(vAttrName to readQuotedValue())
		}

		// xmlns scope for this element: declarations here + the parent chain.
		val vLocalNs = HashMap<String, String>()
		for ((vName, vValue) in vRawAttrs) {
			if (vName == "xmlns") vLocalNs[""] = vValue
			else if (vName.startsWith("xmlns:")) vLocalNs[vName.substringAfter(':')] = vValue
		}
		val vScope: List<Map<String, String>> =
			if (vLocalNs.isEmpty()) (inParentScope ?: emptyList())
			else listOf(vLocalNs) + (inParentScope ?: emptyList())

		fun resolveNs(inPrefix: String): String {
			for (vS in vScope) vS[inPrefix]?.let { return it }
			return ""
		}

		val vPrefix = vQName.substringBefore(':', "")
		val vLocal = vQName.substringAfter(':')
		// Per XML-Names: unprefixed ATTRIBUTES have no namespace (unlike elements,
		// which take the default xmlns).
		val vAttrs = vRawAttrs
			.filter { it.first != "xmlns" && !it.first.startsWith("xmlns:") }
			.map { (vName, vValue) ->
				val vAPrefix = vName.substringBefore(':', "")
				Attr(
					prefix = vAPrefix,
					localName = vName.substringAfter(':'),
					nsUri = if (vAPrefix.isEmpty()) "" else resolveNs(vAPrefix),
					value = vValue,
				)
			}

		val vElement = ElementImpl(vPrefix, vLocal, resolveNs(vPrefix), vAttrs, vScope)

		if (lookingAt("/>")) {
			mPos += 2
			return vElement
		}
		expect(">")

		// Children until our closing tag.
		val vChildren = ArrayList<Node>()
		while (true) {
			when {
				mPos >= mXml.length -> throw MalformedXMLException("unclosed <$vQName>")
				lookingAt("</") -> {
					mPos += 2
					val vClose = readName()
					skipWhitespace(); expect(">")
					if (vClose != vQName) throw MalformedXMLException("mismatched </$vClose> for <$vQName>")
					vElement.children = vChildren
					return vElement
				}
				lookingAt("<!--") -> skipUntil("-->")
				lookingAt("<![CDATA[") -> {
					val vEnd = mXml.indexOf("]]>", mPos + 9)
					if (vEnd < 0) throw MalformedXMLException("unclosed CDATA")
					vChildren.add(TextNodeImpl(mXml.substring(mPos + 9, vEnd)))
					mPos = vEnd + 3
				}
				lookingAt("<?") -> skipUntil("?>")
				lookingAt("<") -> parseElement(vScope)?.let { vChildren.add(it) }
				else -> {
					val vNext = mXml.indexOf('<', mPos).let { if (it < 0) mXml.length else it }
					val vText = decodeEntities(mXml.substring(mPos, vNext))
					if (vText.isNotBlank()) vChildren.add(TextNodeImpl(vText))
					mPos = vNext
				}
			}
		}
	}

	// ============
	//  Lexing helpers

	private fun lookingAt(inToken: String): Boolean = mXml.startsWith(inToken, mPos)

	private fun expect(inToken: String) {
		if (!lookingAt(inToken)) throw MalformedXMLException("expected '$inToken' at $mPos")
		mPos += inToken.length
	}

	private fun skipWhitespace() {
		while (mPos < mXml.length && mXml[mPos].isWhitespace()) mPos++
	}

	private fun skipUntil(inToken: String) {
		val vEnd = mXml.indexOf(inToken, mPos)
		if (vEnd < 0) throw MalformedXMLException("unterminated '$inToken' section")
		mPos = vEnd + inToken.length
	}

	/* DOCTYPE may nest an internal subset in [ ] - skip to the matching '>'. */
	private fun skipDoctype() {
		var vDepth = 0
		while (mPos < mXml.length) {
			val c = mXml[mPos++]
			if (c == '[') vDepth++
			else if (c == ']') vDepth--
			else if (c == '>' && vDepth <= 0) return
		}
		throw MalformedXMLException("unterminated DOCTYPE")
	}

	private fun readName(): String {
		val vStart = mPos
		while (mPos < mXml.length) {
			val c = mXml[mPos]
			if (c.isWhitespace() || c == '=' || c == '>' || c == '/' || c == '<') break
			mPos++
		}
		return mXml.substring(vStart, mPos)
	}

	private fun readQuotedValue(): String {
		if (mPos >= mXml.length) throw MalformedXMLException("unterminated attribute value")
		val vQuote = mXml[mPos]
		if (vQuote != '"' && vQuote != '\'') throw MalformedXMLException("attribute value must be quoted at $mPos")
		mPos++
		val vEnd = mXml.indexOf(vQuote, mPos)
		if (vEnd < 0) throw MalformedXMLException("unterminated attribute value")
		val vRaw = mXml.substring(mPos, vEnd)
		mPos = vEnd + 1
		return decodeEntities(vRaw)
	}

	/* The five predefined entities + decimal/hex character references. */
	private fun decodeEntities(inText: String): String {
		if ('&' !in inText) return inText
		val vOut = StringBuilder(inText.length)
		var i = 0
		while (i < inText.length) {
			val c = inText[i]
			if (c != '&') { vOut.append(c); i++; continue }
			val vEnd = inText.indexOf(';', i + 1)
			if (vEnd < 0) { vOut.append(c); i++; continue }
			val vEntity = inText.substring(i + 1, vEnd)
			val vDecoded = when {
				vEntity == "amp" -> "&"
				vEntity == "lt" -> "<"
				vEntity == "gt" -> ">"
				vEntity == "quot" -> "\""
				vEntity == "apos" -> "'"
				vEntity.startsWith("#x") || vEntity.startsWith("#X") ->
					vEntity.substring(2).toIntOrNull(16)?.let { charSequenceOf(it) }
				vEntity.startsWith("#") ->
					vEntity.substring(1).toIntOrNull()?.let { charSequenceOf(it) }
				else -> null
			}
			if (vDecoded == null) { vOut.append(c); i++ } else { vOut.append(vDecoded); i = vEnd + 1 }
		}
		return vOut.toString()
	}

	private fun charSequenceOf(inCodepoint: Int): String {
		if (inCodepoint < 0x10000) return inCodepoint.toChar().toString()
		val vAdj = inCodepoint - 0x10000
		return charArrayOf(
			(0xD800 or (vAdj shr 10)).toChar(),
			(0xDC00 or (vAdj and 0x3FF)).toChar(),
		).concatToString()
	}
}
