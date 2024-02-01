# How to use
```
// project build.gradle
repositories {
    maven { url = uri("https://jitpack.io") }
}

// module dependencies
dependencies {
    implementation("com.github.DroidLin:android-inter-process-call:${latestRelease}")
}
```

# TODO List
- [ ] exception handler for synchronized or suspend functions call.
- [ ] maybe use annotation processor to collect implementation interfaces.