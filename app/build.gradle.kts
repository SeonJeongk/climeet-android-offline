import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import java.util.Properties

val localProperties = Properties().apply {
    rootProject.file("local.properties")
        .takeIf { it.isFile }
        ?.inputStream()
        ?.use(::load)
}

fun configuredUrl(propertyKey: String): String {
    val value = providers.gradleProperty(propertyKey).orNull
        ?: localProperties.getProperty(propertyKey)
        ?: "https://example.invalid/"
    val normalized = value.trim().removeSurrounding("\"")

    require(normalized.startsWith("http://") || normalized.startsWith("https://")) {
        "$propertyKey must use http:// or https://"
    }
    require(normalized.endsWith('/')) {
        "$propertyKey must end with a trailing slash"
    }
    return normalized
}

val testServerUrl = providers.gradleProperty("testServerUrl").orNull?.also { url ->
    require(url.startsWith("http://") || url.startsWith("https://")) {
        "testServerUrl must use http:// or https://"
    }
    require(url.endsWith('/')) {
        "testServerUrl must end with a trailing slash"
    }
}

fun buildConfigString(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("dagger.hilt.android.plugin")
    id("androidx.navigation.safeargs.kotlin")
    id("org.jlleitschuh.gradle.ktlint")
    id("io.gitlab.arturbosch.detekt")
}

if (file("google-services.json").isFile) {
    apply(plugin = "com.google.gms.google-services")
}

val baseDevUrl = configuredUrl("baseDevUrl")
val baseProdUrl = configuredUrl("baseProdUrl")

android {
    namespace = "com.climus.climeet"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.climus.climeet"
        minSdk = 26
        targetSdk = 34
        versionCode = 17
        versionName = "0.2.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "BASE_API_URL", buildConfigString("https://example.invalid/"))
    }

    buildTypes {
        debug {
            buildConfigField(
                "String",
                "BASE_API_URL",
                buildConfigString(testServerUrl ?: baseDevUrl),
            )
        }
        release {
            buildConfigField("String", "BASE_API_URL", buildConfigString(baseProdUrl))
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        dataBinding = true
        buildConfig = true
    }
}

ktlint {
    version.set("1.5.0")
    android.set(true)
    outputToConsole.set(true)
    ignoreFailures.set(false)
    baseline.set(file("config/ktlint/baseline.xml"))
    filter {
        exclude("**/build/**")
        exclude("**/generated/**")
    }
    kotlinScriptAdditionalPaths {
        include(
            rootProject.fileTree(rootProject.projectDir) {
                include("*.gradle.kts")
            },
        )
    }
}

detekt {
    toolVersion = "1.23.8"
    source.setFrom("src/main/java", "src/test/java", "src/androidTest/java")
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    baseline = rootProject.file("config/detekt/baseline.xml")
    buildUponDefaultConfig = true
    parallel = true
    ignoreFailures = false
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "1.8"
    exclude("**/build/**", "**/generated/**")
    reports {
        xml.required.set(false)
        html.required.set(false)
        txt.required.set(false)
        sarif.required.set(false)
        md.required.set(false)
    }
}

tasks.withType<DetektCreateBaselineTask>().configureEach {
    jvmTarget = "1.8"
    exclude("**/build/**", "**/generated/**")
}

dependencies {

    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // hilt
    val hiltVersion = "2.48"
    implementation("com.google.dagger:hilt-android:${hiltVersion}")
    kapt("com.google.dagger:hilt-android-compiler:${hiltVersion}")

    // retrofit
    val retrofitVersion = "2.9.0"
    implementation("com.squareup.retrofit2:retrofit:$retrofitVersion")
    implementation("com.squareup.retrofit2:converter-gson:$retrofitVersion")

    // okHttp
    val okHttpVersion = "5.0.0-alpha.2"
    implementation("com.squareup.okhttp3:okhttp:$okHttpVersion")
    implementation("com.squareup.okhttp3:logging-interceptor:$okHttpVersion")
    implementation("com.squareup.okhttp3:okhttp-urlconnection:$okHttpVersion")
    testImplementation("com.squareup.okhttp3:mockwebserver:$okHttpVersion")

    // glide
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.github.bumptech.glide:compose:1.0.0-beta01")

    // room
    val roomVersion = "2.6.0"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")

    // navigation
    implementation("androidx.navigation:navigation-fragment-ktx:2.5.1")
    implementation("androidx.navigation:navigation-ui-ktx:2.5.1")

    // Circle Indicator
    implementation("me.relex:circleindicator:2.1.6")

    // CircleImageView
    implementation("de.hdodenhof:circleimageview:3.1.0")

    // Live Data
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.5.1")

    // Calendar
    implementation ("com.kizitonwose.calendar:view:2.4.1")

    // View Pager
    implementation ("androidx.viewpager2:viewpager2:1.0.0")

    // firebase
    implementation(platform("com.google.firebase:firebase-bom:32.6.0"))

    // video compressor
    implementation("com.github.AbedElazizShe:LightCompressor:1.3.2")

    // media3 | exoplayer2
    val media3Version = "1.2.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")

    // FCM
    implementation("com.google.firebase:firebase-messaging-ktx")

    // MPAndroidChart
    implementation ("com.github.PhilJay:MPAndroidChart:v3.1.0")
}
