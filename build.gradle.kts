// Build script de topo. As versões dos plugins são fixadas aqui e aplicadas nos módulos.
// Combinação estável e testada (Kotlin 2.0.20 + AGP 8.5.2 + Compose BOM 2024.09).
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    // Plugin do compilador Compose: obrigatório a partir de Kotlin 2.0.
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
    // KSP para o gerador de código do Moshi (moshi-kotlin-codegen).
    id("com.google.devtools.ksp") version "2.0.20-1.0.25" apply false
}
