// 顶层构建脚本：只提供便捷任务，模块自身的配置都在各模块的 build.gradle.kts 中。
//
// 项目为单版本结构：包名固定 com.neodhizuku.more，并额外注册原版 Dhizuku 的
// provider authority 与授权 action，使硬编码官方包名的客户端也能连到本应用。
// 因此原版 Dhizuku 必须卸载，否则 authority 冲突装不上。
//
// 直接执行 ./gradlew assembleDebug 即可，下面两个任务只是为了让它们在
// IDE 的 Gradle 面板里可见。

tasks.register("assembleDebug") {
    group = "build"
    description = "编译调试版"
    dependsOn(":app:assembleDebug")
}

tasks.register("assembleRelease") {
    group = "build"
    description = "编译发布版"
    dependsOn(":app:assembleRelease")
}
