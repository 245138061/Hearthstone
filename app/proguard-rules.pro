# Preserve kotlinx serialization metadata for offline strategy parsing.
-keepclassmembers class kotlinx.serialization.** {
    *;
}

-keep class com.bgtactician.app.data.model.** { *; }
