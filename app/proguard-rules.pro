# Regras ProGuard/R8 do módulo app.
# O MVP usa minify desligado; estas regras existem para builds release futuros.

# Moshi usa metadados Kotlin; manter para a reflexão/adaptadores gerados.
-keep class kotlin.Metadata { *; }
-keepclassmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}

# Manter os DTOs do contrato (serializados por nome de campo).
-keep class pt.droninho32.app.data.** { *; }

# osmdroid
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**
