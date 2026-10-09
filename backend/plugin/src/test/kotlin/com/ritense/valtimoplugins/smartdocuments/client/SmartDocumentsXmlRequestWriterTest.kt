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

import com.fasterxml.jackson.databind.ObjectMapper
import com.ritense.valtimoplugins.smartdocuments.domain.SmartDocumentsRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

internal class SmartDocumentsXmlRequestWriterTest {
    @Test
    fun `should write the request envelope with customerData and selection`() {
        val xml = write(mapOf("name" to "Jane"), templateGroup = "Brieven", template = "Informatieverzoek")

        assertThat(xml).startsWith("<?xml").contains("encoding='UTF-8'?><root><customerData>")
        val doc = parse(xml)
        assertThat(text(doc, "/root/customerData/name")).isEqualTo("Jane")
        assertThat(text(doc, "/root/SmartDocument/Selection/TemplateGroup")).isEqualTo("Brieven")
        assertThat(text(doc, "/root/SmartDocument/Selection/Template")).isEqualTo("Informatieverzoek")
    }

    @Test
    fun `should add xml space preserve only to text with line breaks`() {
        val toelichting =
            "Huidige saldo van alle crypto munten.\n1. Geef aan welke munt (coin) het betreft.\n2. vermeld het aantal coins,"

        val doc = parse(write(mapOf("toelichting" to toelichting, "omschrijving" to "Eén regel")))

        val multiLine = element(doc, "/root/customerData/toelichting")
        assertThat(multiLine.getAttributeNS(XMLConstants.XML_NS_URI, "space")).isEqualTo("preserve")
        assertThat(multiLine.textContent).isEqualTo(toelichting)
        val singleLine = element(doc, "/root/customerData/omschrijving")
        assertThat(singleLine.hasAttributeNS(XMLConstants.XML_NS_URI, "space")).isFalse()
        assertThat(singleLine.textContent).isEqualTo("Eén regel")
    }

    @Test
    fun `should write the attribute as xml space preserve`() {
        val xml = write(mapOf("toelichting" to "regel 1\nregel 2"))

        assertThat(xml).contains("<toelichting xml:space=\"preserve\">regel 1\nregel 2</toelichting>")
    }

    @Test
    fun `should normalize windows and old mac line breaks`() {
        val doc = parse(write(mapOf("windows" to "a\r\nb", "mac" to "c\rd")))

        assertThat(text(doc, "/root/customerData/windows")).isEqualTo("a\nb")
        assertThat(text(doc, "/root/customerData/mac")).isEqualTo("c\nd")
        assertThat(element(doc, "/root/customerData/mac").getAttributeNS(XMLConstants.XML_NS_URI, "space"))
            .isEqualTo("preserve")
    }

    @Test
    fun `should turn a vertical tab and a form feed into line breaks`() {
        // Word uses a vertical tab for a soft line break (Shift+Enter) and a form feed for a page break
        val doc = parse(write(mapOf("softBreak" to "regel 1\u000Bregel 2", "pageBreak" to "pagina 1\u000Cpagina 2")))

        val softBreak = element(doc, "/root/customerData/softBreak")
        assertThat(softBreak.textContent).isEqualTo("regel 1\nregel 2")
        assertThat(softBreak.getAttributeNS(XMLConstants.XML_NS_URI, "space")).isEqualTo("preserve")
        assertThat(text(doc, "/root/customerData/pageBreak")).isEqualTo("pagina 1\npagina 2")
    }

    @Test
    fun `should remove control characters that are not allowed in XML`() {
        val doc = parse(write(mapOf("tekst" to "a\u0000b\u0007c\u001Fd\uFFFEe")))

        assertThat(text(doc, "/root/customerData/tekst")).isEqualTo("abcde")
    }

    @Test
    fun `should keep tabs and characters outside the basic multilingual plane`() {
        val doc = parse(write(mapOf("tekst" to "kolom 1\tkolom 2 😀")))

        assertThat(text(doc, "/root/customerData/tekst")).isEqualTo("kolom 1\tkolom 2 😀")
    }

    @Test
    fun `should write the example message from the story`() {
        val customerData =
            mapOf(
                "openzaaknummer" to "ZAAK-2025-0000001932",
                "verzendadres" to
                    mapOf(
                        "huisnummer" to 70,
                        "postcode" to "2511 BT",
                        "straatnaam" to "Spui",
                        "woonplaatsnaam" to "'s-Gravenhage",
                    ),
                "opgevraagdeStukken" to
                    listOf(
                        mapOf(
                            "informatieverzoekItem" to
                                mapOf(
                                    "omschrijvingAanvrager" to "Crypto munten overzicht",
                                    "toelichting" to "Huidige saldo.\n1. Geef aan welke munt het betreft.",
                                ),
                        ),
                        mapOf(
                            "informatieverzoekItem" to
                                mapOf(
                                    "omschrijvingAanvrager" to "Kopie overzicht aandelen",
                                    "toelichting" to "Alle aandelen en obligaties, geen crypto munten.",
                                ),
                        ),
                    ),
                "datumAanvraag" to "2025-02-13T12:51:38.000Z",
            )

        val doc = parse(write(customerData))

        assertThat(text(doc, "/root/customerData/verzendadres/huisnummer")).isEqualTo("70")
        assertThat(text(doc, "/root/customerData/verzendadres/woonplaatsnaam")).isEqualTo("'s-Gravenhage")
        val items = "/root/customerData/opgevraagdeStukken/item/informatieverzoekItem"
        assertThat(count(doc, items)).isEqualTo(2)
        val first = element(doc, "($items)[1]/toelichting")
        assertThat(first.getAttributeNS(XMLConstants.XML_NS_URI, "space")).isEqualTo("preserve")
        assertThat(first.textContent).isEqualTo("Huidige saldo.\n1. Geef aan welke munt het betreft.")
        assertThat(element(doc, "($items)[2]/toelichting").hasAttributeNS(XMLConstants.XML_NS_URI, "space")).isFalse()
    }

    @Test
    fun `should escape special characters`() {
        val value = "Tom & Jerry <script>\"quotes\"</script>"

        val xml = write(mapOf("tekst" to value))

        assertThat(xml).contains("Tom &amp; Jerry &lt;script>")
        assertThat(text(parse(xml), "/root/customerData/tekst")).isEqualTo(value)
    }

    @Test
    fun `should write null as empty element and other values as text`() {
        val doc = parse(write(mapOf("leeg" to null, "aantal" to 3, "bedrag" to 12.5, "akkoord" to true)))

        val empty = element(doc, "/root/customerData/leeg")
        assertThat(empty.hasChildNodes()).isFalse()
        assertThat(text(doc, "/root/customerData/aantal")).isEqualTo("3")
        assertThat(text(doc, "/root/customerData/bedrag")).isEqualTo("12.5")
        assertThat(text(doc, "/root/customerData/akkoord")).isEqualTo("true")
    }

    @Test
    fun `should write lists of plain values and arrays as items`() {
        val doc = parse(write(mapOf("namen" to listOf("a", "b"), "codes" to arrayOf(1, 2, 3))))

        assertThat(count(doc, "/root/customerData/namen/item")).isEqualTo(2)
        assertThat(text(doc, "/root/customerData/namen/item[2]")).isEqualTo("b")
        assertThat(count(doc, "/root/customerData/codes/item")).isEqualTo(3)
    }

    @Test
    fun `should write json nodes like maps and lists`() {
        val node = ObjectMapper().readTree("""{"adres":{"straat":"Spui","regels":["r1","r2\nr3"]}}""")

        val doc = parse(write(mapOf("gegevens" to node)))

        assertThat(text(doc, "/root/customerData/gegevens/adres/straat")).isEqualTo("Spui")
        assertThat(count(doc, "/root/customerData/gegevens/adres/regels/item")).isEqualTo(2)
        assertThat(
            element(
                doc,
                "/root/customerData/gegevens/adres/regels/item[2]",
            ).getAttributeNS(XMLConstants.XML_NS_URI, "space"),
        ).isEqualTo("preserve")
    }

    @Test
    fun `should accept keys with letters, digits, underscore, dash and dot`() {
        val doc = parse(write(mapOf("straat_naam-1.a" to "x", "_intern" to "y", "café" to "z")))

        assertThat(text(doc, "/root/customerData/straat_naam-1.a")).isEqualTo("x")
        assertThat(text(doc, "/root/customerData/_intern")).isEqualTo("y")
    }

    @Test
    fun `should fail with a clear message for a key with a space`() {
        val exception = assertThrows<IllegalArgumentException> { write(mapOf("my field" to "x")) }

        assertThat(exception.message).contains("'my field'").contains("not a valid XML element name")
    }

    @Test
    fun `should fail for a key that starts with a digit`() {
        assertThrows<IllegalArgumentException> { write(mapOf("1st" to "x")) }
    }

    @Test
    fun `should name the full path of an invalid nested key`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                write(mapOf("verzendadres" to mapOf("huis nummer" to 70)))
            }

        assertThat(exception.message).contains("'verzendadres.huis nummer'")
    }

    @Test
    fun `should name the list index of an invalid key inside a list`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                write(mapOf("stukken" to listOf(mapOf("ok" to 1), mapOf("niet ok" to 2))))
            }

        assertThat(exception.message).contains("'stukken[1].niet ok'")
    }

    private fun write(
        customerData: Map<String, Any?>,
        templateGroup: String = "group",
        template: String = "template",
    ): String =
        SmartDocumentsXmlRequestWriter.write(
            SmartDocumentsRequest(
                customerData,
                SmartDocumentsRequest.SmartDocument(SmartDocumentsRequest.Selection(templateGroup, template)),
            ),
        )

    private fun parse(xml: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
    }

    private fun element(
        doc: Document,
        xpath: String,
    ): Element = XPathFactory.newInstance().newXPath().evaluate(xpath, doc, XPathConstants.NODE) as Element

    private fun text(
        doc: Document,
        xpath: String,
    ): String = element(doc, xpath).textContent

    private fun count(
        doc: Document,
        xpath: String,
    ): Int =
        (XPathFactory.newInstance().newXPath().evaluate("count($xpath)", doc, XPathConstants.NUMBER) as Double).toInt()
}
