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

package com.ritense.valtimoplugins.smartdocuments.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class PayloadFormatOptionTest {
    @Test
    fun `should default to JSON when no payload format is configured`() {
        assertThat(PayloadFormatOption.from(null)).isEqualTo(PayloadFormatOption.JSON)
        assertThat(PayloadFormatOption.from("")).isEqualTo(PayloadFormatOption.JSON)
        assertThat(PayloadFormatOption.from("  ")).isEqualTo(PayloadFormatOption.JSON)
    }

    @Test
    fun `should accept the payload format in any case`() {
        assertThat(PayloadFormatOption.from("XML")).isEqualTo(PayloadFormatOption.XML)
        assertThat(PayloadFormatOption.from(" xml ")).isEqualTo(PayloadFormatOption.XML)
        assertThat(PayloadFormatOption.from("json")).isEqualTo(PayloadFormatOption.JSON)
    }

    @Test
    fun `should fail for an unknown payload format`() {
        val exception = assertThrows<IllegalArgumentException> { PayloadFormatOption.from("YAML") }

        assertThat(exception.message).contains("'YAML'").contains("JSON, XML")
    }
}
