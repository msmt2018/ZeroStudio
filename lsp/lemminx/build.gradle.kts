plugins {
    java
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.bndtools.osgi)
    // alias(libs.plugins.graalvm.buildtools.native)
    
}
dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.google.guava:guava:32.0.1-jre")
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:1.0.0")
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j.jsonrpc:1.0.0")

    implementation("xerces:xercesImpl:2.12.2")
    implementation("xml-apis:xml-apis:1.4.01")
    implementation("com.kotcrab.remark:remark:1.0.0")
    implementation("org.jsoup:jsoup:1.14.2")
    runtimeOnly("xml-resolver:xml-resolver:1.2")

    implementation("org.relaxng:jing:20220510") {
        exclude(group = "net.sf.saxon", module = "Saxon-HE")
        exclude(group = "xerces", module = "xercesImpl")
        exclude(group = "xml-apis", module = "xml-apis")
    }

    // 测试依赖
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.10.1")
    testRuntimeOnly("org.junit.jupiterjunit-jupiter-params:5.10.1")
    testImplementation("org.eclipse.jetty:jetty-server:11.0.24")
}

tasks.test {
    useJUnitPlatform()
}

// tasks.jar {
    // manifest {
        // attributes(
            // "Main-Class" to "org.eclipse.lemminx.XMLServerLauncher",
            // "Bundle-ManifestVersion" to "2",
            // "Bundle-SymbolicName" to "org.eclipse.lemminx.uber-jar",
            // "Bundle-ActivationPolicy" to "lazy",
            // "Bundle-Version" to project.version.toString().replace("-SNAPSHOT", ""),
            // "Bundle-Vendor" to "Eclipse.org",
            // "Bundle-Name" to "lemminx uber jar bundle",
            // "Service-Component" to "OSGI-INF/*.xml"
        // )
    // }
// }

// if (project.hasProperty("native") && project.property("native") == "true") {
    // plugins.apply("org.graalvm.buildtools.native")

    // graalvmNative {
        // binaries {
            // named("main") {
                // imageName.set("lemminx-${System.getProperty("os.name").toLowerCase()}-${project.version}")
                // fallback.set(false)
                // buildArgs.addAll(
                    // "-march=compatibility",
                    // "--enable-http",
                    // "--enable-https"
                // )
            // }
        // }
    // }
// }
