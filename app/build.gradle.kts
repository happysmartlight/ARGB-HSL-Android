import java.io.File

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.wledmanager.qpxzks"
    minSdk = 24
    targetSdk = 36
    versionCode = 2
    versionName = "1.1"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  // implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.play.billing.ktx)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

tasks.register("downloadDefaultAudio") {
    val targetFile = File(project.projectDir, "src/main/res/raw/default_audio.mp3")
    // Chỉ tải khi file chưa tồn tại (file đã được commit vào git). Nhờ vậy build
    // bình thường không cần internet và không phụ thuộc vào soundhelix.com.
    onlyIf { !targetFile.exists() }
    doLast {
        targetFile.parentFile.mkdirs()

        var currentUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3"
        println("Downloading $currentUrl...")
        
        try {
            val urlClass = Class.forName("java.net.URL")
            val connClass = Class.forName("java.net.URLConnection")
            val httpConnClass = Class.forName("java.net.HttpURLConnection")
            
            var connection: Any? = null
            var redirect = true
            var count = 0
            
            while (redirect && count < 5) {
                val urlObj = urlClass.getConstructor(String::class.java).newInstance(currentUrl)
                connection = urlClass.getMethod("openConnection").invoke(urlObj)
                
                httpConnClass.getMethod("setInstanceFollowRedirects", Boolean::class.javaPrimitiveType).invoke(connection, true)
                connClass.getMethod("setRequestProperty", String::class.java, String::class.java).invoke(connection, "User-Agent", "Mozilla/5.0")
                
                connClass.getMethod("connect").invoke(connection)
                
                val responseCode = httpConnClass.getMethod("getResponseCode").invoke(connection) as Int
                println("Response code for $currentUrl: $responseCode")
                
                if (responseCode == 301 || responseCode == 302 || responseCode == 303 || responseCode == 307 || responseCode == 308) {
                    val headerField = connClass.getMethod("getHeaderField", String::class.java).invoke(connection, "Location") as String
                    currentUrl = headerField
                    println("Redirecting to: $currentUrl")
                    count++
                } else {
                    redirect = false
                }
            }
            
            val input = connClass.getMethod("getInputStream").invoke(connection) as java.io.InputStream
            val bytes = input.readBytes()
            input.close()
            
            targetFile.writeBytes(bytes)
            println("Download completed. Size: ${targetFile.length()} bytes")
        } catch (e: Exception) {
            println("Download failed: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
}

tasks.named("preBuild") {
    dependsOn("downloadDefaultAudio")
}
