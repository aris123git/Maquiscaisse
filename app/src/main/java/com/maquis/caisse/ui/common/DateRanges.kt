package com.maquis.caisse.ui.common

import com.maquis.caisse.domain.model.BucketGrain
import com.maquis.caisse.domain.model.StatsPeriod
import java.util.Calendar

object DateRanges {
    fun dayBounds(calendar: Calendar = Calendar.getInstance()): Pair<Long, Long> {
        val start = (calendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = (calendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
        return start to end
    }

    fun todayBounds(): Pair<Long, Long> = dayBounds()

    fun yesterdayBounds(): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        return dayBounds(cal)
    }

    fun tomorrowBounds(): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        return dayBounds(cal)
    }

    fun lastDaysBounds(days: Int): Pair<Long, Long> {
        val end = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
        val start = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -(days - 1))
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return start to end
    }

    fun allTimeBounds(): Pair<Long, Long> = 0L to Long.MAX_VALUE

    fun boundsFor(
        period: StatsPeriod,
        customDayMs: Long? = null,
        customFromMs: Long? = null,
        customToMs: Long? = null,
    ): Pair<Long, Long> = when (period) {
        StatsPeriod.TODAY -> todayBounds()
        StatsPeriod.YESTERDAY -> yesterdayBounds()
        StatsPeriod.TOMORROW -> tomorrowBounds()
        StatsPeriod.WEEK -> lastDaysBounds(7)
        StatsPeriod.MONTH -> lastDaysBounds(30)
        StatsPeriod.CUSTOM_DAY -> {
            val day = customDayMs ?: System.currentTimeMillis()
            val cal = Calendar.getInstance().apply { timeInMillis = day }
            dayBounds(cal)
        }
        StatsPeriod.CUSTOM_RANGE -> {
            val from = customFromMs ?: todayBounds().first
            val to = customToMs ?: todayBounds().second
            // Fin de journée pour la borne haute si l'utilisateur a choisi un jour
            val toCal = Calendar.getInstance().apply { timeInMillis = maxOf(from, to) }
            val endOfTo = dayBounds(toCal).second
            minOf(from, to) to endOfTo
        }
        StatsPeriod.ALL -> allTimeBounds()
    }

    /** true = agrégation par heure (1 jour), false = par jour / semaine / mois. */
    fun useHourlyBuckets(period: StatsPeriod, fromMs: Long, toMs: Long): Boolean {
        if (period == StatsPeriod.TODAY || period == StatsPeriod.YESTERDAY ||
            period == StatsPeriod.TOMORROW || period == StatsPeriod.CUSTOM_DAY
        ) {
            return true
        }
        val span = toMs - fromMs
        return span <= 36L * 60L * 60L * 1000L
    }

    fun grainFor(period: StatsPeriod, fromMs: Long, toMs: Long): BucketGrain {
        if (useHourlyBuckets(period, fromMs, toMs)) return BucketGrain.HOUR
        val days = ((toMs - fromMs).coerceAtLeast(0L) / (24L * 60L * 60L * 1000L)) + 1
        return when {
            days <= 45 -> BucketGrain.DAY
            days <= 180 -> BucketGrain.WEEK
            else -> BucketGrain.MONTH
        }
    }
}
