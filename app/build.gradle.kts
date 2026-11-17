import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 本地敏感配置（测试网关 Key 等）只从 local.properties 读取，该文件已被 .gitignore 排除，
// 不进入版本库；仓库内默认渠道 Key 留空，由用户运行时填写。
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val localTestKey = localProps.getProperty("test.chat.key", "").orEmpty()

android {
    namespace = "com.ferry.pet"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ferry.pet"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "1.5.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // 热更新清单：update.json 存于仓库主干，经 jsDelivr 多 CDN 轮询回退（逗号分隔）。
        buildConfigField("String", "UPDATE_ENDPOINT",
            "\"https://cdn.jsdelivr.net/gh/fnnas-2026/deepseek-pet@main/update.json," +
                "https://fastly.jsdelivr.net/gh/fnnas-2026/deepseek-pet@main/update.json," +
                "https://gcore.jsdelivr.net/gh/fnnas-2026/deepseek-pet@main/update.json\"")
    }

    // Release 签名占位：正式证书后续提供后，把 keystore 与口令写进 keystore.properties
    // （不纳入版本库）；当前缺省回退到 Debug 签名，保证 Release 可出包。
    signingConfigs {
        create("release") {
            val storeFile = rootProject.file("release.keystore")
            if (storeFile.exists()) {
                this.storeFile = storeFile
                storePassword = project.findProperty("RELEASE_STORE_PASSWORD") as? String ?: "android"
                keyAlias = project.findProperty("RELEASE_KEY_ALIAS") as? String ?: "release"
                keyPassword = project.findProperty("RELEASE_KEY_PASSWORD") as? String ?: "android"
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            // 测试版：测试网关 Key 从本地 local.properties 注入（不入库）；缺省留空。
            buildConfigField("String", "BUILD_FLAVOR", "\"test\"")
            buildConfigField("String", "DEFAULT_CHAT_KEY", "\"$localTestKey\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (rootProject.file("release.keystore").exists())
                signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            // 正式版：默认渠道 Key 留空，由用户运行时填写，不携带内置密钥。
            buildConfigField("String", "BUILD_FLAVOR", "\"formal\"")
            buildConfigField("String", "DEFAULT_CHAT_KEY", "\"\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/petAssets"))
    testOptions { unitTests.isReturnDefaultValues = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

val exportPetAssets by tasks.registering(Exec::class) {
    description = "把角色 webm 转成 WebP 帧序列并生成 manifest（离线执行，见 scripts/README）"
    group = "pet"
    val root = rootProject.projectDir
    inputs.dir(root.resolve("pet-assets/videos"))
    inputs.file(root.resolve("scripts/export_android_assets.py"))
    outputs.dir(layout.buildDirectory.dir("generated/petAssets"))
    workingDir(root)
    commandLine("python3", "scripts/export_android_assets.py",
        "--output", layout.buildDirectory.dir("generated/petAssets").get().asFile.absolutePath)
}
tasks.named("preBuild").configure { dependsOn(exportPetAssets) }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.05.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    // AI 聊天：HTTP 与 JSON（OpenAI 兼容 /chat/completions）
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20240303")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
