# How to use

## First
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

## Second

```
val initConfig = ProcessCallInitConfig.Builder()
    .setContext(context = this)
    .setProcessIdentifier(
        identifier = object : ProcessIdentifier {
            override val keyForCurrentProcess: String
                get() = // unique key for current process to identify who am i.
        }
    )
    .setConnectionAdapter(
        connectionAdapter = object : ProcessConnectionAdapter {
            override fun onAttachToRemote(context: Context, bundle: ProcessRequestBundle) {
                // your way to connection to remote process.
                // we provide 3 ways: Service, BroadcastReceiver, ContentProvider
                // see com.lza.android.inter.process.library.component.IntentExtensions.kt file for more information.
            }
        }
    )
    .build()

ProcessCenter.init(initConfig = initConfig)
ProcessCenter.putService([InterfaceNameYouWant]::class.java, [ImplementationInstanceOfTheInterfaceNameYouProvided])
```

## Third
```
ProcessCenter.getService([InterfaceNameYouWant]::class.java).[functionYouWant]([parametersYouWant])
```

# TODO List
- [x] exception handler for synchronized or suspend functions call.
- [x] maybe use annotation processor to collect implementation classes.
- [x] exception handler for ksp generated implementation for better performance.