/*
 * Copyright 2026 Ritense BV, the Netherlands.
 *
 * Licensed under EUPL, Version 1.2 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ritense.valtimoplugins.smartdocuments.client

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.ritense.valtimoplugins.smartdocuments.domain.SmartDocumentsRequest
import java.io.StringWriter
import javax.xml.XMLConstants
import javax.xml.stream.XMLOutputFactory
import javax.xml.stream.XMLStreamWriter

/**
 * Writes a [SmartDocumentsRequest] as XML:
 *
 * ```xml
 * <root>
 *     <customerData>...</customerData>
 *     <SmartDocument><Selection><TemplateGroup>...</TemplateGroup><Template>...</Template></Selection></SmartDocument>
 * </root>
 * ```
 *
 * - A map becomes nested elements, a list becomes one `<item>` element per entry.
 * - Text with a line break gets `xml:space="preserve"`, so SmartDocuments keeps the line breaks in the document.
 * - `null` becomes an empty element. Other values are written with `toString()`.
 * - The XML is written without indentation, so no whitespace is added to the values.
 */
object SmartDocumentsXmlRequestWriter {
    private const val ROOT = "root"
    private const val LIST_ITEM = "item"
    private val VALID_ELEMENT_NAME = Regex("""^[\p{L}_][\p{L}\p{N}_.\-]*$""")
    private val LINE_BREAK_CHARACTERS = Regex("[\r\u000B\u000C]")

    // Everything outside the characters XML 1.0 allows: tab, \n, \r and the ranges below
    private val INVALID_XML_CHARACTERS =
        Regex("""[^\x{9}\x{A}\x{D}\x{20}-\x{D7FF}\x{E000}-\x{FFFD}\x{10000}-\x{10FFFF}]""")
    private val outputFactory = XMLOutputFactory.newFactory()
    private val objectMapper = ObjectMapper()

    fun write(request: SmartDocumentsRequest): String {
        val output = StringWriter()
        val writer = outputFactory.createXMLStreamWriter(output)
        writer.writeStartDocument("UTF-8", "1.0")
        writer.element(ROOT) {
            writeMap(writer, "customerData", request.customerData, path = "")
            writer.element("SmartDocument") {
                writer.element("Selection") {
                    writeText(writer, "TemplateGroup", request.smartDocument.selection.templateGroup)
                    writeText(writer, "Template", request.smartDocument.selection.template)
                }
            }
        }
        writer.writeEndDocument()
        writer.close()
        return output.toString()
    }

    private fun writeValue(
        writer: XMLStreamWriter,
        name: String,
        value: Any?,
        path: String,
    ) {
        when (value) {
            null -> writer.writeEmptyElement(name)
            is JsonNode -> writeValue(writer, name, objectMapper.treeToValue(value, Any::class.java), path)
            is Map<*, *> -> writeMap(writer, name, value, path)
            is Iterable<*> -> writeList(writer, name, value, path)
            is Array<*> -> writeList(writer, name, value.asIterable(), path)
            else -> writeText(writer, name, value.toString())
        }
    }

    private fun writeMap(
        writer: XMLStreamWriter,
        name: String,
        map: Map<*, *>,
        path: String,
    ) {
        writer.element(name) {
            map.forEach { (key, value) ->
                val elementName = key.toString()
                val elementPath = if (path.isEmpty()) elementName else "$path.$elementName"
                requireValidElementName(elementName, elementPath)
                writeValue(writer, elementName, value, elementPath)
            }
        }
    }

    private fun writeList(
        writer: XMLStreamWriter,
        name: String,
        list: Iterable<*>,
        path: String,
    ) {
        writer.element(name) {
            list.forEachIndexed { index, value -> writeValue(writer, LIST_ITEM, value, "$path[$index]") }
        }
    }

    private fun writeText(
        writer: XMLStreamWriter,
        name: String,
        text: String,
    ) {
        val normalized = normalizeText(text)
        writer.element(name) {
            // An attribute must be written directly after the start tag, before the content
            if (normalized.contains('\n')) {
                writer.writeAttribute("xml", XMLConstants.XML_NS_URI, "space", "preserve")
            }
            writer.writeCharacters(normalized)
        }
    }

    /**
     * Makes the text valid for XML 1.0:
     * - Line breaks become `\n` (`\r\n` and `\r`, but also the vertical tab that Word uses for a soft line break and
     *   the form feed of a page break).
     * - Other characters that XML 1.0 does not allow (control characters) are removed. They are invisible and would
     *   otherwise make the request fail, while the same text works with a JSON payload.
     */
    private fun normalizeText(text: String): String =
        text
            .replace("\r\n", "\n")
            .replace(LINE_BREAK_CHARACTERS, "\n")
            .replace(INVALID_XML_CHARACTERS, "")

    /** Writes the start tag, the [content] and the matching end tag, so the code nests like the XML. */
    private inline fun XMLStreamWriter.element(
        name: String,
        content: () -> Unit,
    ) {
        writeStartElement(name)
        content()
        writeEndElement()
    }

    private fun requireValidElementName(
        name: String,
        path: String,
    ) {
        require(VALID_ELEMENT_NAME.matches(name)) {
            "Template data key '$path' is not a valid XML element name. With payload format XML, a key must start " +
                "with a letter or '_' and may only contain letters, digits, '_', '-' and '.'."
        }
    }
}
