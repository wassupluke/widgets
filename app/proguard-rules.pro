# ──────────────────────────────────────────────
# kotlinx.serialization
# R8 must not strip the generated $$serializer classes or the Companion
# accessors used to obtain serializers for @Serializable models.
# ──────────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    *** Companion;
}
-keepclasseswithmembers class com.wassupluke.widgets.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.wassupluke.widgets.**$$serializer { *; }

# ──────────────────────────────────────────────
# WorkManager
# RefreshWeatherWorker is instantiated by name via reflection.
# ──────────────────────────────────────────────
-keep class com.wassupluke.widgets.RefreshWeatherWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
