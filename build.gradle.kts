plugins {
    kotlin("jvm") version "1.9.10"
    application
    kotlin("plugin.serialization") version "1.9.10"
    id("net.mamoe.mirai-console") version "2.15.0"
}

group = "net.cutereimu.maplebots"
version = "1.0.1"

repositories {
    maven("https://maven.aliyun.com/repository/public")
    mavenCentral()
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("org.jfree:jfreechart:1.0.19")
}

mirai {
    jvmTarget = JavaVersion.VERSION_17
}

application {
    mainClass.set("net.cutereimu.maplebots.Main")
}
