/*
 * Copyright 2015-2024 Ritense BV, the Netherlands.
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

package com.ritense.valtimoplugins.smartdocuments.plugin

import com.ritense.document.domain.Document
import com.ritense.processdocument.service.DocumentDelegateService
import com.ritense.resource.service.TemporaryResourceStorageService
import com.ritense.valtimoplugins.smartdocuments.client.SmartDocumentsClient
import com.ritense.valtimoplugins.smartdocuments.domain.DocumentFormatOption
import com.ritense.valtimoplugins.smartdocuments.domain.DocumentsStructure
import com.ritense.valtimoplugins.smartdocuments.domain.FileStreamResponse
import com.ritense.valtimoplugins.smartdocuments.domain.PayloadFormatOption
import com.ritense.valtimoplugins.smartdocuments.domain.SmartDocumentsTemplateData
import com.ritense.valtimoplugins.smartdocuments.domain.Template
import com.ritense.valtimoplugins.smartdocuments.domain.TemplateGroup
import com.ritense.valtimoplugins.smartdocuments.domain.TemplatesStructure
import com.ritense.valueresolver.ValueResolverService
import org.assertj.core.api.Assertions.assertThat
import org.camunda.bpm.engine.delegate.DelegateExecution
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import java.io.ByteArrayInputStream

const val TEMPLATE_NAME_LIST = "templateNameList"
const val TEMPLATE_GROUP_NAME = "Group 1"
const val TEMPLATE_NAME = "Template 1"

@ExtendWith(MockitoExtension::class)
internal class SmartDocumentsPluginTest {
    @Mock
    lateinit var documentDelegateService: DocumentDelegateService

    @Mock
    lateinit var applicationEventPublisher: ApplicationEventPublisher

    @Mock
    lateinit var smartDocumentsClient: SmartDocumentsClient

    @Mock
    lateinit var valueResolverService: ValueResolverService

    @Mock
    lateinit var temporaryResourceStorageService: TemporaryResourceStorageService

    @InjectMocks
    lateinit var smartDocumentsPlugin: SmartDocumentsPlugin

    private lateinit var delegateExecution: DelegateExecution

    @BeforeEach
    fun setup() {
        // given
        delegateExecution = delegateExecution()
    }

    @Test
    fun `should get template names`() {
        // given
        whenever(smartDocumentsClient.getSmartDocumentsTemplateData(any())).thenReturn(smartDocumentsTemplateData())
        smartDocumentsPlugin.url = "test.com"
        smartDocumentsPlugin.username = "username"
        smartDocumentsPlugin.password = "password"

        // when
        smartDocumentsPlugin.getTemplateNames(
            execution = delegateExecution,
            templateGroupName = TEMPLATE_GROUP_NAME,
            resultingTemplateNameListProcessVariableName = TEMPLATE_NAME_LIST,
        )

        // then
        val result = delegateExecution.getVariable(TEMPLATE_NAME_LIST) as List<String>

        assertThat(result).isNotNull
        assertThat(result.size).isEqualTo(3)
        assertThat(result.first()).isEqualTo(TEMPLATE_NAME)
    }

    @Test
    fun `list should be empty`() {
        // given
        whenever(smartDocumentsClient.getSmartDocumentsTemplateData(any())).thenReturn(smartDocumentsTemplateData())
        smartDocumentsPlugin.url = "test.com"
        smartDocumentsPlugin.username = "username"
        smartDocumentsPlugin.password = "password"

        // when
        smartDocumentsPlugin.getTemplateNames(
            execution = delegateExecution,
            templateGroupName = "Nope",
            resultingTemplateNameListProcessVariableName = TEMPLATE_NAME_LIST,
        )

        // then
        val result = delegateExecution.getVariable(TEMPLATE_NAME_LIST) as List<String>

        assertThat(result).isNotNull
        assertThat(result).isEmpty()
    }

    @Test
    fun `should generate the document with an XML payload when payload format is XML`() {
        givenGenerateDocumentMocks()

        generate(payloadFormat = "XML")

        verify(
            smartDocumentsClient,
        ).generateDocumentStream(any(), any(), eq(DocumentFormatOption.PDF), eq(PayloadFormatOption.XML))
        assertThat(delegateExecution.getVariable("generatedDocument")).isEqualTo("resource-id")
    }

    @Test
    fun `should generate the document with a JSON payload when no payload format is configured`() {
        givenGenerateDocumentMocks()

        generate(payloadFormat = null)

        verify(
            smartDocumentsClient,
        ).generateDocumentStream(any(), any(), eq(DocumentFormatOption.PDF), eq(PayloadFormatOption.JSON))
    }

    @Test
    fun `should fail before calling SmartDocuments when the payload format is unknown`() {
        assertThrows<IllegalArgumentException> { generate(payloadFormat = "YAML") }

        verify(smartDocumentsClient, never()).generateDocumentStream(any(), any(), any(), any())
    }

    private fun givenGenerateDocumentMocks() {
        val document = mock<Document>()
        whenever(document.id()).thenReturn(mock())
        whenever(documentDelegateService.getDocument(any<DelegateExecution>())).thenReturn(document)
        whenever(valueResolverService.resolveValues(anyOrNull(), any(), any())).thenReturn(
            mapOf(
                "doc:/toelichting" to "regel 1\nregel 2",
            ),
        )
        whenever(smartDocumentsClient.generateDocumentStream(any(), any(), any(), any()))
            .thenReturn(FileStreamResponse("brief.pdf", "pdf", ByteArrayInputStream("pdf".toByteArray())))
        whenever(temporaryResourceStorageService.store(any(), any())).thenReturn("resource-id")
    }

    private fun generate(payloadFormat: String?) {
        smartDocumentsPlugin.url = "http://localhost"
        smartDocumentsPlugin.username = "username"
        smartDocumentsPlugin.password = "password"
        smartDocumentsPlugin.generate(
            execution = delegateExecution,
            templateGroup = TEMPLATE_GROUP_NAME,
            templateName = TEMPLATE_NAME,
            format = "PDF",
            templateData = arrayOf(TemplateDataEntry("toelichting", "doc:/toelichting")),
            resultingDocumentProcessVariableName = "generatedDocument",
            payloadFormat = payloadFormat,
        )
    }

    private fun smartDocumentsTemplateData() =
        SmartDocumentsTemplateData(
            DocumentsStructure(
                TemplatesStructure(
                    listOf(
                        TemplateGroup(
                            TEMPLATE_GROUP_NAME,
                            null,
                            listOf(
                                Template(
                                    "BA72ACC982C042A5B285DF91F684C214",
                                    TEMPLATE_NAME,
                                ),
                                Template(
                                    "6B39F51603474130B8DF7CE7ED58309F",
                                    "Plan intakegesprek1",
                                ),
                                Template(
                                    "9014A7F2AD12453DBE2AE055773642E0",
                                    "Plan intakegesprek2",
                                ),
                            ),
                        ),
                        TemplateGroup(
                            "test",
                            null,
                            listOf(
                                Template(
                                    "A99A1DD46F204EDA9988EE7F54C99B6E",
                                    "Plan intakegesprek3",
                                ),
                                Template(
                                    "E9BBADF6C0964CB69A0165E26509DAF0",
                                    "Plan intakegesprek4",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

    private fun delegateExecution(): DelegateExecution {
        val variables = mutableMapOf<String, Any?>()
        val execution = mock<DelegateExecution>(lenient = true)
        whenever(execution.processInstanceId).thenReturn("process-instance-id")
        whenever(execution.setVariable(any(), any())).thenAnswer { invocation ->
            variables[invocation.arguments[0] as String] = invocation.arguments[1]
        }
        whenever(execution.getVariable(any())).thenAnswer { invocation ->
            variables[invocation.arguments[0] as String]
        }
        return execution
    }
}
