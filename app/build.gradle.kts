plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// ---- Real AdMob ids, used by RELEASE builds only (debug builds always use Google's test ids) ----
// Gemblo has no AdMob app yet: create it at apps.admob.com (app id has "~", ad unit ids have "/") and paste them here.
val admobAppId = "ca-app-pub-3940256099942544~3347511713"
val admobBannerId = "ca-app-pub-3940256099942544/9214589741"   // bottom banner (adaptive)
val admobInterstitialId = "ca-app-pub-3940256099942544/1033173712"   // between games
val admobRewardedId = "ca-app-pub-3940256099942544/5224354917"   // Revive video

// A release with test ids would earn nothing, so refuse to build one.
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        require(listOf(admobAppId, admobBannerId, admobInterstitialId, admobRewardedId).all { it.startsWith("ca-app-pub-") && !it.contains("3940256099942544") }) {
            "Fill in the real AdMob ids at the top of app/build.gradle.kts before building a release."
        }
    }
}

android {
    namespace = "com.papercranelabs.gemblo"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.papercranelabs.gemblo"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            // Google's public TEST ids: safe to click, never earn, never risk the AdMob account
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
            buildConfigField("String", "ADMOB_BANNER", "\"ca-app-pub-3940256099942544/9214589741\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL", "\"ca-app-pub-3940256099942544/1033173712\"")
            buildConfigField("String", "ADMOB_REWARDED", "\"ca-app-pub-3940256099942544/5224354917\"")
        }
        release {
            manifestPlaceholders["admobAppId"] = admobAppId
            buildConfigField("String", "ADMOB_BANNER", "\"$admobBannerId\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL", "\"$admobInterstitialId\"")
            buildConfigField("String", "ADMOB_REWARDED", "\"$admobRewardedId\"")
        }
    }

    buildFeatures { compose = true; buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.animation)

    implementation(libs.play.services.ads)
    implementation(libs.ump)
    implementation(libs.billing)
    implementation(libs.androidx.work)

    testImplementation("junit:junit:4.13.2")
}
