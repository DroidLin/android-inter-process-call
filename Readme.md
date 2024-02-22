# How to use
```
// project build.gradle
repositories {
    maven { url = uri("https://jitpack.io") }
}

// module dependencies
dependencies {
    implementation("com.github.DroidLin.android-inter-process-call:library:${latestRelease}")

    // if you need, additional annotation and code-generation will improve running performance.
    // implementation("com.github.DroidLin.android-inter-process-call:annotation:${latestRelease}")
    // ksp("com.github.DroidLin.android-inter-process-call:ksp-compiler:${latestRelease}")
    // kapt("com.github.DroidLin.android-inter-process-call:kapt-compiler:${latestRelease}")
}
```

# TODO List
- [x] exception handler for synchronized or suspend functions call.
- [x] maybe use annotation processor to collect implementation classes.
- [x] exception handler for ksp generated implementation for better performance.