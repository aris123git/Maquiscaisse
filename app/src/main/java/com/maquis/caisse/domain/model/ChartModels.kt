package com.maquis.caisse.domain.model

/** Point de série (courbe / barres / histogramme). */
data class ChartPoint(
    val label: String,
    val value: Float,
    val secondaryValue: Float = 0f,
    val bucketStartMs: Long = 0L,
)

/** Part pour camembert. */
data class ChartSlice(
    val label: String,
    val value: Float,
)

enum class ChartType(val label: String) {
    CURVE("Courbe"),
    BAR_VERTICAL("Barres verticales"),
    BAR_HORIZONTAL("Barres horizontales"),
    HISTOGRAM("Histogramme"),
    PIE("Camembert"),
}

enum class StatsPeriod(val label: String) {
    TODAY("Aujourd'hui"),
    YESTERDAY("Hier"),
    TOMORROW("Demain"),
    WEEK("Cette semaine"),
    MONTH("Ce mois"),
    CUSTOM_DAY("Jour…"),
    CUSTOM_RANGE("Intervalle…"),
    ALL("Tout"),
}

enum class BucketGrain {
    HOUR,
    DAY,
    WEEK,
    MONTH,
}
