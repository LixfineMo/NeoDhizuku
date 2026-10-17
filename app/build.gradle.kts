import org.jetbrains.kotlin.konan.properties.loadProperties
import java.util.Properties

plugins {
    alias(libs.plugins.agp.app)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
}

val keystoreDir: String = "$rootDir/keystore"

val keystoreProps: Properties = Properties()
for (name in arrayOf("r0s.properties", "debug.properties")) {
    val f: File = file("$keystoreDir/$name")
    if (!f.exists()) continue
    keystoreProps.load(f.inputStream())
    break
}

android {
    namespace = "com.neodhizuku.more"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    // 包名固定为 com.neodhizuku.more（namespace、R 类、BuildConfig 都在其下）。
    //
    // provider 注册三个 authority：
    //   1. com.neodhizuku.more.dhizuku_server.provider  —— 本应用自身，供动态推导 authority 的客户端
    //   2. com.neodhizuku.more.server.provider          —— 同上，兜底
    //   3. com.rosan.dhizuku.server.provider            —— 原版 Dhizuku 的固定 authority
    //
    // 第 3 项让硬编码官方包名的客户端（如 amarok、爱玩机工具箱）也能连到本应用。
    // ContentProvider authority 全局唯一，因此原版 Dhizuku 必须卸载，
    // 否则安装时报 INSTALL_FAILED_CONFLICTING_PROVIDER。
    //
    // 授权 action 同理，见 AndroidManifest.xml 中的 RequestPermissionOfficialEntry。

    defaultConfig {
        minSdk = 26
        targetSdk = compileSdk
        ndkVersion = "29.0.14206865"

        applicationId = "com.neodhizuku.more"
        manifestPlaceholders["dhizukuAuthorities"] =
            "com.neodhizuku.more.dhizuku_server.provider;" +
            "com.neodhizuku.more.server.provider;" +
            "com.rosan.dhizuku.server.provider"

        val versionProps: Properties = org.jetbrains.kotlin.konan.file.File("$rootDir/version.properties").loadProperties()
        versionCode = versionProps.getProperty("versionCode").toInt()
        versionName = versionProps.getProperty("versionName")

        // D-04：构建期注入自身签名白名单，供启动时完整性校验使用。
        // 未配置时为空字符串，ManagerIntegrity 会自动跳过校验（方便自行编译调试）。
        val signatureAllowlist = (
            System.getenv("DHIZUKU_SIGNATURE_SHA256")
                ?: project.findProperty("dhizukuSignatureSha256")?.toString()
                ?: ""
            )
            .split(',', ';', ' ', '\n', '\r', '\t')
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(",")
        buildConfigField("String", "SIGNATURE_SHA256_ALLOWLIST", "\"$signatureAllowlist\"")

        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
    }

    signingConfigs {
        val storeFile: File = file("$keystoreDir/${keystoreProps.getProperty("storeFile")}")
        val storePassword: String = keystoreProps.getProperty("storePassword")
        val keyAlias: String = keystoreProps.getProperty("keyAlias")
        val keyPassword: String = keystoreProps.getProperty("keyPassword")
        getByName("debug") {
            this.storeFile = storeFile
            this.storePassword = storePassword
            this.keyAlias = keyAlias
            this.keyPassword = keyPassword
        }
    }

    buildTypes {
        configureEach {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    androidResources {
        @Suppress("UnstableApiUsage")
        generateLocaleConfig = true
    }

    packaging {
        jniLibs.excludes.add("lib/*/libandroidx.graphics.path.so")
        resources.excludes.addAll(arrayOf("META-INF/**", "DebugProbesKt.bin", "kotlin-tooling-metadata.json", "kotlin/**"))
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain(21)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    compileOnly(project(":hidden-api"))

    implementation(libs.androidx.core)
    implementation(libs.androidx.lifecycle)
    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.ui)
    implementation(libs.compose.navigation)
    implementation(libs.compose.materialIcons)
    implementation(libs.material)
    implementation(libs.compose.material3)

    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.room.ktx)

    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)

    implementation(libs.accompanist.drawablepainter)

    implementation(libs.lsposed.hiddenapibypass)

    implementation(libs.rikka.shizuku.api)
    implementation(libs.rikka.shizuku.provider)

    implementation(project(":Dhizuku-SERVER_API"))

    // C-05：compose ui tooling 只应在 debug 构建中存在，原先打进了 release APK
    debugImplementation(libs.compose.ui.tooling)
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(arrayOf("-Xlint:deprecation", "-Xlint:unchecked"))
}
