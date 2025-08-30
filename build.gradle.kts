
plugins {
    id("java")
    id("application")
}

group = "whatisMGC"
version = "1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

dependencies {
    dependencies {
        implementation("org.jsoup:jsoup:1.17.2")
        implementation("mysql:mysql-connector-java:8.0.29")
        testImplementation(platform("org.junit:junit-bom:5.10.0"))
        testImplementation("org.junit.jupiter:junit-jupiter")
        implementation ("io.github.cdimascio:java-dotenv:5.2.2")
    }

}
tasks.jar{
    manifest{
        attributes["Main-Class"]="whatisMGC.WebCrawlerApp"
    }
    from(sourceSets.main.get().output)
}

application {
    mainClass.set("whatisMGC.WebCrawlerApp")
}

tasks.test {
    useJUnitPlatform()
}