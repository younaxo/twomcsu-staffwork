import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.attributes.java.TargetJvmVersion

plugins {
    java
    checkstyle
    id("com.gradleup.shadow") version "9.6.1"
    id("com.diffplug.spotless") version "7.0.4"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
    maven("https://repo.extendedclip.com/releases/") {
        name = "extendedclip-placeholderapi"
    }
}

dependencies {
    // Paper API: минимальная поддерживаемая версия сборки (1.20.x). Компиляция против неё
    // гарантирует, что используемые методы существуют на всех версиях 1.20-1.21.11,
    // так как Paper API расширяет возможности аддитивно и обратно совместим по исходникам.
    compileOnly("io.papermc.paper:paper-api:1.20.6-R0.1-SNAPSHOT")

    // PlaceholderAPI — мягкая (опциональная) зависимость, плагин обязан работать без неё.
    compileOnly("me.clip:placeholderapi:2.12.3")

    // Пул соединений для SQL-хранилищ. Относительно безопасен для релокации (чистая Java,
    // без загрузки нативных библиотек и без reflection по строковым путям ресурсов).
    implementation("com.zaxxer:HikariCP:6.3.0")

    // Разбор JSON-ответов Telegram Bot API. Ручной парсинг регулярными выражениями был бы
    // ненадёжен (вложенные объекты, экранирование) — Gson лёгкий и решает это корректно.
    implementation("com.google.code.gson:gson:2.13.1") {
        // Аннотации только для статического анализа на этапе сборки Gson — в рантайме не нужны.
        exclude(group = "com.google.errorprone", module = "error_prone_annotations")
    }

    // JDBC-драйверы. Намеренно НЕ релоцируются: sqlite-jdbc грузит нативные библиотеки по
    // пути ресурса вида "/org/sqlite/native/..." и релокация пакета ломает этот механизм;
    // H2 и MariaDB-драйвер тоже используют reflection-регистрацию по исходным именам пакетов.
    // Риск конфликта версий с другими плагинами существует, но является общепринятым
    // компромиссом для JDBC-драйверов в экосистеме Bukkit-плагинов.
    implementation("org.xerial:sqlite-jdbc:3.49.1.0")
    implementation("com.h2database:h2:2.3.232")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.3")

    // Реальная реализация YamlConfiguration нужна тестам YAML-хранилища; на сервере она
    // предоставляется самим Paper/Spigot, поэтому в основном коде остаётся compileOnly выше.
    testImplementation("io.papermc.paper:paper-api:1.20.6-R0.1-SNAPSHOT")

    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito:mockito-core:5.18.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.18.0")
}

// Опубликованный io.papermc.paper:paper-api несёт метаданные Gradle Module Metadata,
// заявляющие требование JVM 21 для сборки инструментами Paper — это не касается фактической
// рантайм-совместимости сервера (paper-api используется только compileOnly, в JAR не попадает),
// а лишь мешает разрешению classpath, если сам проект собирается с --release 17. Явно просим
// вариант под JVM 21, чтобы разрешение зависимостей не блокировалось этим несоответствием.
listOf("compileClasspath", "testCompileClasspath", "testRuntimeClasspath").forEach { name ->
    configurations.named(name) {
        attributes {
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21)
        }
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    // Байткод уровня 17: единый JAR запускается и на Java 17 (MC 1.20-1.20.4), и на Java 21.
    options.release.set(17)
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.processResources {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching(listOf("plugin.yml", "config.yml")) {
        expand(props)
    }
}

tasks.shadowJar {
    archiveBaseName.set("TMCStaffWork")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())

    relocate("com.zaxxer.hikari", "su.twomc.staffwork.libs.hikari")
    relocate("com.google.gson", "su.twomc.staffwork.libs.gson")

    minimize {
        exclude(dependency("org.xerial:sqlite-jdbc:.*"))
        exclude(dependency("com.h2database:h2:.*"))
        exclude(dependency("org.mariadb.jdbc:mariadb-java-client:.*"))
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.jar {
    // Обычный jar (без зависимостей) не публикуется и не используется — итоговый артефакт
    // это shadowJar, но отключать сам jar не нужно, так как shadowJar от него зависит.
    archiveClassifier.set("thin")
}

checkstyle {
    toolVersion = "10.26.1"
    configFile = file("checkstyle/checkstyle.xml")
    isIgnoreFailures = false
    maxWarnings = 0
}

configure<SpotlessExtension> {
    java {
        target("src/*/java/**/*.java")
        removeUnusedImports()
        importOrder()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.named("check") {
    dependsOn(tasks.named("spotlessCheck"))
}
