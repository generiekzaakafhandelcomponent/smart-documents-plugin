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

/**
 * The format of the request body that is sent to SmartDocuments.
 *
 * With [XML], line breaks in text values are kept in the generated document, because those values are sent with
 * `xml:space="preserve"`. SmartDocuments has no equivalent for JSON.
 */
enum class PayloadFormatOption {
    JSON,
    XML,
    ;

    companion object {
        fun from(value: String?): PayloadFormatOption {
            if (value.isNullOrBlank()) {
                return JSON
            }
            return entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
                ?: throw IllegalArgumentException(
                    "Unknown payload format '$value'. Use one of: ${entries.joinToString { it.name }}",
                )
        }
    }
}
