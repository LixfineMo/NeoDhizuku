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

    // 两个包名分支：
    //   neo      → com.neodhizuku.more （本项目自身包名）
    //   official → com.rosan.dhizuku  （原版包名，用于兼容硬编码官方包名的客户端）
    //
    // namespace 保持不变（源码包名、R 类、BuildConfig 都在 com.neodhizuku.more 下），
    // 只有 applicationId 随分支变化，因此源码零改动。
    flavorDimensions += "pkg"

    // 当前激活的分支。默认只保留 neoCompat，这样 assembleDebug / assembleRelease
    // 以及 IDE 的 Run 按钮都会直接出接管版，无需手动切换 Build Variant。
    //
    // 三个分支的配置都完整保留，需要哪个就把它加回这个集合：
    //   setOf("neo", "neoCompat", "official")
    val activeFlavors = setOf("neoCompat")

    // 三个分支共用同一份源码，只有 applicationId 与 provider 的 authorities 不同。
    //
    //   neo       —— 本项目包名，只注册自己的 authority
    //                → 可与原版 Dhizuku 共存
    //   neoCompat —— 同上，额外注册原版 Dhizuku 的固定 authority 与授权 action
    //                → 硬编码官方包名的客户端（部分安装器、工具箱）也能连到本应用
    //                → 原版 Dhizuku 必须卸载，否则 authority 冲突装不上
    //   official  —— 直接用原版包名，天然兼容全部客户端
    //                → 原版 Dhizuku 必须卸载（同包名无法共存）
    productFlavors {
        create("neo") {
            dimension = "pkg"
            applicationId = "com.neodhizuku.more"
            manifestPlaceholders["dhizukuAuthorities"] =
                "com.neodhizuku.more.dhizuku_server.provider;" +
                "com.neodhizuku.more.server.provider"
        }
        create("neoCompat") {
            dimension = "pkg"
            applicationId = "com.neodhizuku.more"
            // 版本号带后缀，便于在界面上区分装的是哪个分支
            versionNameSuffix = "-compat"
            manifestPlaceholders["dhizukuAuthorities"] =
                "com.neodhizuku.more.dhizuku_server.provider;" +
                "com.neodhizuku.more.server.provider;" +
                "com.rosan.dhizuku.server.provider"
        }
        create("official") {
            dimension = "pkg"
            applicationId = "com.rosan.dhizuku"
            versionNameSuffix = "-official"
            // 官方包名下 ${applicationId}.server.provider 已经是原版 authority，
            // 无需再额外声明（重复声明会导致 authority 冲突）。
            manifestPlaceholders["dhizukuAuthorities"] =
                "com.rosan.dhizuku.dhizuku_server.provider;" +
                "com.rosan.dhizuku.server.provider"
        }
    }

    defaultConfig {
        minSdk = 26
        targetSdk = compileSdk
        ndkVersion = "29.0.14206865"

        val versionProps: Properties = org.jetbrains.kotlin.konan.file.File("$rootDir/version.properties").loadProperties()
        versionCode = versionProps.getProperty("versionCode").toInt()
        versionName = versionProps.getProperty("versionName")

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
