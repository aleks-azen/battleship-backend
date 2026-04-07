plugins {
    kotlin("jvm") version "2.3.20"
    kotlin("plugin.noarg") version "2.3.20"
    id("com.gradleup.shadow") version "9.3.2"
    application
}

group = "co.amazensolutions.battleship"
version = "1.0.0"

repositories {
    mavenCentral()
}

noArg {
    annotation("software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean")
}

dependencies {
    implementation(platform("software.amazon.awssdk:bom:2.42.28"))
    implementation("software.amazon.awssdk:dynamodb")
    implementation("software.amazon.awssdk:dynamodb-enhanced")
    implementation("software.amazon.awssdk:url-connection-client")

    implementation("com.amazonaws:aws-lambda-java-core:1.4.0")
    implementation("com.amazonaws:aws-lambda-java-events:3.16.1")
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("com.google.inject:guice:7.0.0")

    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.14.3")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("co.amazensolutions.battleship.LocalRunnerKt")
}

tasks.shadowJar {
    archiveBaseName.set("battleship-backend")
    archiveClassifier.set("all")
    archiveVersion.set("")
    mergeServiceFiles()
    exclude("co/amazensolutions/battleship/LocalContext*")
    exclude("co/amazensolutions/battleship/LocalRunnerKt*")
}
