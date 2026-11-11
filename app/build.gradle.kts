import org.jetbrains.kotlin.konan.properties.loadProperties
import java.util.Properties

plugins {
    alias(libs.plugins.agp.app)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
}

val keystoreDir: String = "$rootDir/keystore"

fun loadKeystoreProps(vararg names: String): Properties? {
    val props = Properties()
    for (name in names) {
        val f: File = file("$keystoreDir/$name")
        if (!f.exists()) continue
        props.load(f.inputStream())
        return props
    }
    return null
}

/** 调试用签名：只在 debug 构建上使用 */
val debugKeystoreProps: Properties? = loadKeystoreProps("r0s.properties", "debug.properties")

/**
 * 发布签名：自己的密钥，不是 test / debug 那一份。
 *
 * 在 keystore/release.properties 里写：
 *   storeFile=release.jks
 *   storePassword=...
 *   keyAlias=...
 *   keyPassword=...
 *
 * 没有配置时退回调试签名并打一条警告 —— 这样仓库里不带密钥也能编出包，
 * 但正式发布前必须配好，否则用户升级时会因签名不一致而安装失败。
 */
val releaseKeystoreProps: Properties? = loadKeystoreProps("release.properties", "r0s.properties")

/**
 * TEST ONLY 标签总开关（默认关闭）。
 * 打开：gradle.properties 里设 neoTestOnly=true 后重新编译。
 * 一处同时驱动 AndroidManifest 的 android:testOnly 与 BuildConfig.TEST_ONLY。
 * 界面上的 TEST ONLY 药丸已移除，此开关现在只影响 manifest 与安装方式。
 * 注意：android:testOnly=true 的 APK 只能用 adb install -t（覆盖安装 -r -t）
 * 或 Android Studio 安装；应用内「下载 APK → 点开安装」会因普通安装器
 * 不带 INSTALL_ALLOW_TEST 而失败。正式发布前务必改回 false。
 */
val testOnlyFlag: Boolean = (project.findProperty("neoTestOnly") as? String)?.toBoolean() ?: false

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

        // TEST ONLY：manifest 属性与首页药丸共用这一个构建期开关
        manifestPlaceholders["testOnly"] = testOnlyFlag.toString()
        buildConfigField("boolean", "TEST_ONLY", testOnlyFlag.toString())

        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
    }

    signingConfigs {
        debugKeystoreProps?.let { props ->
            getByName("debug") {
                storeFile = file("$keystoreDir/${props.getProperty("storeFile")}")
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }

        create("release") {
            val props = releaseKeystoreProps
            if (props == null) {
                logger.warn(
                    "未找到 keystore/release.properties，release 将退回调试签名。" +
                        "正式发布前请配置自己的密钥，否则用户无法覆盖安装。"
                )
                debugKeystoreProps?.let { debug ->
                    storeFile = file("$keystoreDir/${debug.getProperty("storeFile")}")
                    storePassword = debug.getProperty("storePassword")
                    keyAlias = debug.getProperty("keyAlias")
                    keyPassword = debug.getProperty("keyPassword")
                }
            } else {
                storeFile = file("$keystoreDir/${props.getProperty("storeFile")}")
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        configureEach {
            isMinifyEnabled = false
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
        }
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
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

    // DAX 化 Xposed 模块：只编译、不打包。
    // de.robv.android.xposed.* 由 LSPosed / LSPatch 框架在运行时提供；
    // 主 APK 里没有这些类，普通设备永远加载不到 HookInit，因此完全无感。
    // 优先用随仓库附带的编译桩：完全离线，不依赖 version catalog / Maven 仓库。
    // 桩类只在编译期可见，运行时由 LSPosed / LSPatch 框架提供真实实现。
    val xposedStub = file("libs/xposed-api-82-stub.jar")
    if (xposedStub.exists()) {
        compileOnly(files(xposedStub))
    } else {
        // 兜底：官方 API 82（仓库在 settings.gradle.kts 的 api.xposed.info）
        compileOnly("de.robv.android.xposed:api:82")
    }
    // com.rosan.dhizuku.api.Dhizuku 等客户端类已经通过
    // :Dhizuku-SERVER_API -> api project(":Dhizuku-API") 间接提供，无需重复依赖。

    implementation(libs.androidx.core)
    implementation(libs.androidx.lifecycle)
    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.navigation)
    implementation(libs.compose.materialIcons)
    implementation(libs.material)
    implementation(libs.compose.material3)

    // 液态玻璃底栏：真实背景采样 + 折射
    implementation(libs.miuix.blur)
    implementation(libs.miuix.ui)

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
