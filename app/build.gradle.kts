plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val copyLicenseTask = tasks.register<Copy>("copyLicenseToAssets") {
    from(rootDir.resolve("LICENSE"))
    into(layout.projectDirectory.dir("src/main/assets"))
}

tasks.named("preBuild") {
    dependsOn(copyLicenseTask)
}

android {
    namespace = "com.brarchive.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.brarchive.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"
    }

    // ========== 新增：签名配置 ==========
    signingConfigs {
        create("release") {
            storeFile = file("debugmt.keystore")
            storePassword = "android"       // 密钥库密码
            keyAlias = "mt"    // 密钥别名
            keyPassword = "android"         // 密钥密码
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-files.pro")
            // ========== 新增：将签名配置应用到 release ==========
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
}
