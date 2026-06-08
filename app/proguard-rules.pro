# Add project specific ProGuard rules here.

# Keep app model classes
-keep class com.enmanuelgil.androidsecurity.model.** { *; }

# Keep ViewModels
-keep class com.enmanuelgil.androidsecurity.ui.viewmodel.** { *; }

# Keep service
-keep class com.enmanuelgil.androidsecurity.guard.CamMicGuardService { *; }

# Kotlin coroutines
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Jetpack Compose
-dontwarn androidx.compose.**

# Suppress warnings for missing classes
-dontwarn javax.annotation.**
