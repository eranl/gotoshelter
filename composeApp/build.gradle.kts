import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.android.library)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlinx.serialization)
}

kotlin {
  androidTarget {
    compilerOptions {
      jvmTarget.set(JvmTarget.JVM_11)
    }
  }

  listOf(
    iosX64(),
    iosArm64(),
    iosSimulatorArm64()
  ).forEach { iosTarget ->
    iosTarget.binaries.framework {
      baseName = "ComposeApp"
      isStatic = true
    }
  }

  sourceSets {
    commonMain.dependencies {
      implementation(compose.runtime)
      implementation(compose.foundation)
      implementation(compose.material3)
      implementation(compose.ui)
      implementation(compose.components.resources)
      implementation(compose.components.uiToolingPreview)
      implementation(libs.kotlinx.coroutines.core)
      implementation(libs.kotlinx.serialization.json)
      implementation(libs.kotlinx.datetime)
      implementation(libs.bundles.ktor.common)
      implementation(libs.multiplatform.settings)
    }
    val commonTest by getting {
      dependencies {
        implementation(kotlin("test"))
        implementation(libs.kotlinx.coroutines.test)
      }
    }
    androidMain.dependencies {
      implementation(libs.androidx.core.ktx)
      implementation(libs.androidx.activity.compose)
      implementation(libs.androidx.lifecycle.runtime.ktx)
      implementation(libs.kotlinx.coroutines.android)
      implementation(libs.kotlinx.coroutines.play.services)
      implementation(libs.play.services.location)
      implementation(libs.android.maps.utils)
      implementation(libs.okhttp)
      implementation(libs.ktor.client.okhttp)
      api(libs.androidx.compose.ui)
      api(libs.androidx.compose.material3)
      api(libs.androidx.compose.ui.tooling.preview)
      // Use debugApi for tooling to ensure it's available in the app module's debug build
      api(libs.androidx.compose.ui.tooling)
      
      // Firebase dependencies
      implementation(libs.firebase.crashlytics)
    }
    iosMain.dependencies {
      implementation(libs.ktor.client.darwin)
    }
  }
}

android {
  namespace = "io.github.eranl.gotoshelter.shared"
  compileSdk = 36

  defaultConfig {
    minSdk = 21
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    buildConfig = true
  }
}
