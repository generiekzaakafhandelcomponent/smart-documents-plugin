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

dockerCompose {
    setProjectName("smartdocuments")
    isRequiredBy(project.tasks.test)

    tasks.test {
        useComposeFiles.addAll("$rootDir/docker-resources/docker-compose-base-test.yml", "docker-compose-override.yml")
    }
}

val kotlinLoggingVersion: String by project
val mockitoKotlinVersion: String by project
val valtimoVersion: String by project
val camundaVersion: String by project
val okhttpVersion: String by project
val commonsIoVersion: String by project

dependencies {
    compileOnly("com.ritense.valtimo:case")
    compileOnly("com.ritense.valtimo:contract")
    compileOnly("com.ritense.valtimo:core")
    compileOnly("com.ritense.valtimo:document")
    compileOnly("com.ritense.valtimo:plugin-valtimo")
    compileOnly("com.ritense.valtimo:process-document")
    compileOnly("com.ritense.valtimo:resource")
    compileOnly("com.ritense.valtimo:temporary-resource-storage")
    compileOnly("com.ritense.valtimo:value-resolver")
    compileOnly("org.camunda.bpm:camunda-engine:$camundaVersion")

    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.springframework.boot:spring-boot-starter-web")
    compileOnly("org.springframework.boot:spring-boot-starter-validation")
    compileOnly("org.springframework.security:spring-security-core")
    compileOnly("com.fasterxml.jackson.dataformat:jackson-dataformat-xml")
    compileOnly("org.apache.commons:commons-lang3")
    compileOnly("commons-io:commons-io:$commonsIoVersion")

    compileOnly("io.github.oshai:kotlin-logging:$kotlinLoggingVersion")

    // Testing
    testImplementation("com.ritense.valtimo:case")
    testImplementation("com.ritense.valtimo:contract")
    testImplementation("com.ritense.valtimo:core")
    testImplementation("com.ritense.valtimo:document")
    testImplementation("com.ritense.valtimo:plugin-valtimo")
    testImplementation("com.ritense.valtimo:process-document")
    testImplementation("com.ritense.valtimo:resource")
    testImplementation("com.ritense.valtimo:temporary-resource-storage")
    testImplementation("com.ritense.valtimo:value-resolver")
    testImplementation("com.ritense.valtimo:local-resource")
    testImplementation("com.ritense.valtimo:test-utils-common")

    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa")
    testImplementation("org.springframework.boot:spring-boot-starter-security")
    testImplementation("org.springframework.boot:spring-boot-starter-validation")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml")
    testImplementation("org.apache.commons:commons-lang3")
    testImplementation("commons-io:commons-io:$commonsIoVersion")
    testImplementation("com.squareup.okhttp3:mockwebserver:$okhttpVersion")
    testImplementation("com.squareup.okhttp3:okhttp:$okhttpVersion")

    testImplementation("org.mockito:mockito-core")
    testImplementation("org.mockito.kotlin:mockito-kotlin:$mockitoKotlinVersion")

    testImplementation("org.postgresql:postgresql")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
}

apply(from = "gradle/publishing.gradle")
