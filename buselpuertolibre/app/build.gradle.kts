import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val repoRoot = rootProject.projectDir.parentFile
val snapshotDb = repoRoot.resolve("local/pipeline/output/transit.db")
val snapshotPmtiles = repoRoot.resolve("local/pipeline/assets/el_puerto.pmtiles")
val assetsDir = layout.projectDirectory.dir("src/main/assets")
val bundledDb = assetsDir.file("transit.db").asFile
val bundledPmtiles = assetsDir.file("el_puerto.pmtiles").asFile

android {
    namespace = "es.elpuertobus.app"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }

    defaultConfig {
        applicationId = "es.elpuertobus.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    val signingPropsFile = repoRoot.resolve("local/maintainer/signing.properties")
    if (signingPropsFile.isFile) {
        val props =
            signingPropsFile
                .readLines()
                .mapNotNull { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) return@mapNotNull null
                    val eq = trimmed.indexOf('=')
                    if (eq <= 0) return@mapNotNull null
                    trimmed.substring(0, eq).trim() to trimmed.substring(eq + 1).trim()
                }.toMap()
        val storePath = props["storeFile"]?.let { repoRoot.resolve(it) }
        if (storePath != null && storePath.isFile) {
            signingConfigs {
                create("release") {
                    storeFile = storePath
                    storePassword = props["storePassword"]
                    keyAlias = props["keyAlias"]
                    keyPassword = props["keyPassword"]
                }
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    androidResources {
        noCompress += listOf("pmtiles", "db")
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.maplibre.android)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
}

tasks.register<Copy>("copyTransitSnapshot") {
    group = "build setup"
    description = "Copy maintainer transit.db into app assets."
    from(snapshotDb)
    into(assetsDir)
    rename { "transit.db" }
    onlyIf { snapshotDb.isFile }
}

tasks.register<Copy>("copyBasemapPmtiles") {
    group = "build setup"
    description = "Copy municipal PMTiles into app assets."
    from(snapshotPmtiles)
    into(assetsDir)
    rename { "el_puerto.pmtiles" }
    onlyIf { snapshotPmtiles.isFile }
}

tasks.named("preBuild") {
    dependsOn("copyTransitSnapshot", "copyBasemapPmtiles")
    doFirst {
        if (!snapshotDb.isFile && !bundledDb.isFile) {
            error(
                "Missing transit snapshot at ${snapshotDb.canonicalPath} " +
                    "and no committed copy at ${bundledDb.canonicalPath}. " +
                    "Run: PYTHONPATH=pipeline python3 pipeline/run.py --offline",
            )
        }
        if (!snapshotPmtiles.isFile && !bundledPmtiles.isFile) {
            error(
                "Missing basemap at ${snapshotPmtiles.canonicalPath} " +
                    "and no committed copy at ${bundledPmtiles.canonicalPath}. " +
                    "Run: PYTHONPATH=pipeline python3 pipeline/build_basemap.py",
            )
        }
    }
}
