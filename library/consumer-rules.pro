
-keep class com.lza.android.inter.process.library.interfaces.IPCNoProguard {*;}
-keepclasseswithmembernames class * implements com.lza.android.inter.process.library.interfaces.IPCNoProguard {*;}

-keep class kotlin.Function {*;}
-keepclasseswithmembernames class * implements kotlin.Function {*;}

-keepattributes *Annotation*
-keep class kotlin.** { *; }
-keep class org.jetbrains.** { *; }

-keep class com.lza.android.inter.process.library.interfaces.IPCNoProguard
-keepclasseswithmembernames class * implements com.lza.android.inter.process.library.interfaces.IPCNoProguard {*;}