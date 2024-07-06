/*
 * This file was generated by the Gradle 'init' task.
 *
 * This generated file contains a sample Java application project to get you started.
 * For more details take a look at the 'Building Java & JVM projects' chapter in the Gradle
 * User Manual available at https://docs.gradle.org/7.3/userguide/building_java_projects.html
 */

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.nio.file.Paths;

plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.owasp.dependencycheck") version "8.4.0" apply false
}

allprojects {
    apply(plugin = "org.owasp.dependencycheck")
}

configure<org.owasp.dependencycheck.gradle.extension.DependencyCheckExtension> {
    format = org.owasp.dependencycheck.reporting.ReportGenerator.Format.ALL.toString()
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies {
    // Use JUnit Jupiter for testing.
    testImplementation("org.junit.jupiter:junit-jupiter:5.7.2")
    // https://mvnrepository.com/artifact/com.github.docker-java/docker-java-core
    testImplementation("com.github.docker-java:docker-java-core:3.3.0")
    // https://mvnrepository.com/artifact/com.github.docker-java/docker-java-transport-httpclient5
    testImplementation("com.github.docker-java:docker-java-transport-httpclient5:3.3.0")
    // https://mvnrepository.com/artifact/com.google.http-client/google-http-client
    testImplementation("com.google.http-client:google-http-client:1.43.1")

    // https://mvnrepository.com/artifact/com.google.guava/guava
    implementation("com.google.guava:guava:32.1.2-jre")

    implementation("org.ow2.asm:asm:9.3")
    implementation("org.ow2.asm:asm-util:9.3")
    implementation("org.ow2.asm:asm-commons:9.3")
    
    // https://mvnrepository.com/artifact/javax.servlet.jsp/javax.servlet.jsp-api
    compileOnly("javax.servlet.jsp:javax.servlet.jsp-api:2.3.3")
    // https://mvnrepository.com/artifact/javax.servlet/javax.servlet-api
    compileOnly("javax.servlet:javax.servlet-api:3.1.0") // same as lucee deps


    compileOnly(files("extern/lucee-5.3.9.158-SNAPSHOT.jar"))
    compileOnly(files("extern/5.3.9.158-SNAPSHOT.lco"))

    // https://mvnrepository.com/artifact/org.eclipse.lsp4j/org.eclipse.lsp4j.debug
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j.debug:0.23.1")

}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

tasks.compileJava {
    options.compilerArgs.add("-Xlint:unchecked")
    options.compilerArgs.add("-Xlint:deprecation")
}

tasks.test {
    dependsOn("shadowJar")

    useJUnitPlatform()

    // maxHeapSize = "1G" // infinite, don't care

    maxParallelForks = (Runtime.getRuntime().availableProcessors()).coerceAtLeast(1).also {
        println("Setting maxParallelForks to $it")
    }

    testLogging {
        events("passed")
        events("failed")
        showStandardStreams = true
    }
}

tasks.jar {
    manifest {
        attributes(
            mapOf(
                "Premain-Class" to "luceedebug.Agent",
                "Can-Redefine-Classes" to "true",
                "Bundle-SymbolicName" to "luceedebug-osgi",
                "Bundle-Version" to "2.0.1.1",
                "Export-Package" to "luceedebug.*"
            )
        )
    }
}

tasks.shadowJar {
    configurations = listOf(project.configurations.runtimeClasspath.get())
    setEnableRelocation(true)
    relocationPrefix = "luceedebug_shadow"
    archiveFileName.set("luceedebug.jar") // overwrites the non-shadowed jar but that's OK
}
