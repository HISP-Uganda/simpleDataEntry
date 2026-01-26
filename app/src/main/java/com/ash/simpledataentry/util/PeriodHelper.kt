package com.ash.simpledataentry.util

import org.hisp.dhis.android.core.common.RelativePeriod
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class PeriodHelper {
    fun getDateRange(relativePeriod: RelativePeriod): Pair<Date, Date> {
        val calendar = Calendar.getInstance()
        val now = Date()

        fun startOfDay(date: Date): Date {
            calendar.time = date
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            return calendar.time
        }

        fun endOfDay(date: Date): Date {
            calendar.time = date
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            calendar.set(Calendar.MILLISECOND, 999)
            return calendar.time
        }

        return when (relativePeriod) {
            RelativePeriod.TODAY -> {
                val start = startOfDay(now)
                Pair(start, endOfDay(now))
            }
            RelativePeriod.YESTERDAY -> {
                calendar.time = now
                calendar.add(Calendar.DAY_OF_YEAR, -1)
                val start = startOfDay(calendar.time)
                Pair(start, endOfDay(calendar.time))
            }
            RelativePeriod.LAST_3_DAYS -> {
                calendar.time = now
                calendar.add(Calendar.DAY_OF_YEAR, -3)
                Pair(startOfDay(calendar.time), now)
            }
            RelativePeriod.LAST_7_DAYS -> {
                calendar.time = now
                calendar.add(Calendar.DAY_OF_YEAR, -7)
                Pair(startOfDay(calendar.time), now)
            }
            RelativePeriod.LAST_14_DAYS -> {
                calendar.time = now
                calendar.add(Calendar.DAY_OF_YEAR, -14)
                Pair(startOfDay(calendar.time), now)
            }
            RelativePeriod.LAST_30_DAYS -> {
                calendar.time = now
                calendar.add(Calendar.DAY_OF_YEAR, -30)
                Pair(startOfDay(calendar.time), now)
            }
            RelativePeriod.LAST_60_DAYS -> {
                calendar.time = now
                calendar.add(Calendar.DAY_OF_YEAR, -60)
                Pair(startOfDay(calendar.time), now)
            }
            RelativePeriod.LAST_90_DAYS -> {
                calendar.time = now
                calendar.add(Calendar.DAY_OF_YEAR, -90)
                Pair(startOfDay(calendar.time), now)
            }
            RelativePeriod.LAST_180_DAYS -> {
                calendar.time = now
                calendar.add(Calendar.DAY_OF_YEAR, -180)
                Pair(startOfDay(calendar.time), now)
            }
            RelativePeriod.THIS_WEEK -> {
                calendar.time = now
                calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                val start = startOfDay(calendar.time)
                calendar.add(Calendar.DAY_OF_YEAR, 6)
                Pair(start, endOfDay(calendar.time))
            }
            RelativePeriod.LAST_WEEK -> {
                calendar.time = now
                calendar.add(Calendar.WEEK_OF_YEAR, -1)
                calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                val start = startOfDay(calendar.time)
                calendar.add(Calendar.DAY_OF_YEAR, 6)
                Pair(start, endOfDay(calendar.time))
            }
            RelativePeriod.LAST_4_WEEKS -> {
                calendar.time = now
                calendar.add(Calendar.WEEK_OF_YEAR, -4)
                calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                Pair(startOfDay(calendar.time), now)
            }
            RelativePeriod.LAST_12_WEEKS -> {
                calendar.time = now
                calendar.add(Calendar.WEEK_OF_YEAR, -12)
                calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                Pair(startOfDay(calendar.time), now)
            }
            RelativePeriod.LAST_52_WEEKS -> {
                calendar.time = now
                calendar.add(Calendar.WEEK_OF_YEAR, -52)
                calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                Pair(startOfDay(calendar.time), now)
            }
            RelativePeriod.THIS_MONTH -> {
                calendar.time = now
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                val start = startOfDay(calendar.time)
                calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                Pair(start, endOfDay(calendar.time))
            }
            RelativePeriod.LAST_MONTH -> {
                calendar.time = now
                calendar.add(Calendar.MONTH, -1)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                val start = startOfDay(calendar.time)
                calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                Pair(start, endOfDay(calendar.time))
            }
            RelativePeriod.LAST_3_MONTHS -> {
                calendar.time = now
                calendar.add(Calendar.MONTH, -3)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                Pair(startOfDay(calendar.time), now)
            }
            RelativePeriod.LAST_6_MONTHS -> {
                calendar.time = now
                calendar.add(Calendar.MONTH, -6)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                Pair(startOfDay(calendar.time), now)
            }
            RelativePeriod.LAST_12_MONTHS -> {
                calendar.time = now
                calendar.add(Calendar.MONTH, -12)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                Pair(startOfDay(calendar.time), now)
            }
            RelativePeriod.THIS_BIMONTH -> {
                calendar.time = now
                val month = calendar.get(Calendar.MONTH)
                val startMonth = month - (month % 2)
                calendar.set(Calendar.MONTH, startMonth)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                val start = startOfDay(calendar.time)
                calendar.add(Calendar.MONTH, 1)
                calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                Pair(start, endOfDay(calendar.time))
            }
            RelativePeriod.LAST_BIMONTH -> {
                calendar.time = now
                calendar.add(Calendar.MONTH, -2)
                val month = calendar.get(Calendar.MONTH)
                val startMonth = month - (month % 2)
                calendar.set(Calendar.MONTH, startMonth)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                val start = startOfDay(calendar.time)
                calendar.add(Calendar.MONTH, 1)
                calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                Pair(start, endOfDay(calendar.time))
            }
            RelativePeriod.LAST_6_BIMONTHS -> {
                calendar.time = now
                calendar.add(Calendar.MONTH, -12)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                Pair(startOfDay(calendar.time), now)
            }
            RelativePeriod.THIS_QUARTER -> {
                calendar.time = now
                val month = calendar.get(Calendar.MONTH)
                val startMonth = month - (month % 3)
                calendar.set(Calendar.MONTH, startMonth)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                val start = startOfDay(calendar.time)
                calendar.add(Calendar.MONTH, 2)
                calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                Pair(start, endOfDay(calendar.time))
            }
            RelativePeriod.LAST_QUARTER -> {
                calendar.time = now
                calendar.add(Calendar.MONTH, -3)
                val month = calendar.get(Calendar.MONTH)
                val startMonth = month - (month % 3)
                calendar.set(Calendar.MONTH, startMonth)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                val start = startOfDay(calendar.time)
                calendar.add(Calendar.MONTH, 2)
                calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                Pair(start, endOfDay(calendar.time))
            }
            RelativePeriod.LAST_4_QUARTERS -> {
                calendar.time = now
                calendar.add(Calendar.MONTH, -12)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                Pair(startOfDay(calendar.time), now)
            }
            RelativePeriod.THIS_SIX_MONTH -> {
                calendar.time = now
                val month = calendar.get(Calendar.MONTH)
                val startMonth = if (month < 6) 0 else 6
                calendar.set(Calendar.MONTH, startMonth)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                val start = startOfDay(calendar.time)
                calendar.add(Calendar.MONTH, 5)
                calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                Pair(start, endOfDay(calendar.time))
            }
            RelativePeriod.LAST_SIX_MONTH -> {
                calendar.time = now
                calendar.add(Calendar.MONTH, -6)
                val month = calendar.get(Calendar.MONTH)
                val startMonth = if (month < 6) 0 else 6
                calendar.set(Calendar.MONTH, startMonth)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                val start = startOfDay(calendar.time)
                calendar.add(Calendar.MONTH, 5)
                calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                Pair(start, endOfDay(calendar.time))
            }
            RelativePeriod.LAST_2_SIXMONTHS -> {
                calendar.time = now
                calendar.add(Calendar.MONTH, -12)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                Pair(startOfDay(calendar.time), now)
            }
            RelativePeriod.THIS_YEAR -> {
                calendar.time = now
                calendar.set(Calendar.MONTH, Calendar.JANUARY)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                val start = startOfDay(calendar.time)
                calendar.set(Calendar.MONTH, Calendar.DECEMBER)
                calendar.set(Calendar.DAY_OF_MONTH, 31)
                Pair(start, endOfDay(calendar.time))
            }
            RelativePeriod.LAST_YEAR -> {
                calendar.time = now
                calendar.add(Calendar.YEAR, -1)
                calendar.set(Calendar.MONTH, Calendar.JANUARY)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                val start = startOfDay(calendar.time)
                calendar.set(Calendar.MONTH, Calendar.DECEMBER)
                calendar.set(Calendar.DAY_OF_MONTH, 31)
                Pair(start, endOfDay(calendar.time))
            }
            RelativePeriod.LAST_5_YEARS -> {
                calendar.time = now
                calendar.add(Calendar.YEAR, -5)
                calendar.set(Calendar.MONTH, Calendar.JANUARY)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                Pair(startOfDay(calendar.time), now)
            }
            RelativePeriod.LAST_10_YEARS -> {
                calendar.time = now
                calendar.add(Calendar.YEAR, -10)
                calendar.set(Calendar.MONTH, Calendar.JANUARY)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                Pair(startOfDay(calendar.time), now)
            }
            RelativePeriod.THIS_FINANCIAL_YEAR -> {
                val year = calendar.get(Calendar.YEAR)
                calendar.set(year, Calendar.APRIL, 1, 0, 0, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.time
                calendar.set(year + 1, Calendar.MARCH, 31, 23, 59, 59)
                Pair(start, calendar.time)
            }
            RelativePeriod.LAST_FINANCIAL_YEAR -> {
                val year = calendar.get(Calendar.YEAR) - 1
                calendar.set(year, Calendar.APRIL, 1, 0, 0, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.time
                calendar.set(year + 1, Calendar.MARCH, 31, 23, 59, 59)
                Pair(start, calendar.time)
            }
            RelativePeriod.LAST_5_FINANCIAL_YEARS -> {
                val year = calendar.get(Calendar.YEAR) - 5
                calendar.set(year, Calendar.APRIL, 1, 0, 0, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                Pair(calendar.time, now)
            }
            RelativePeriod.LAST_10_FINANCIAL_YEARS -> {
                val year = calendar.get(Calendar.YEAR) - 10
                calendar.set(year, Calendar.APRIL, 1, 0, 0, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                Pair(calendar.time, now)
            }
            RelativePeriod.WEEKS_THIS_YEAR,
            RelativePeriod.MONTHS_THIS_YEAR,
            RelativePeriod.BIMONTHS_THIS_YEAR,
            RelativePeriod.QUARTERS_THIS_YEAR,
            RelativePeriod.MONTHS_LAST_YEAR,
            RelativePeriod.QUARTERS_LAST_YEAR,
            RelativePeriod.THIS_BIWEEK,
            RelativePeriod.LAST_BIWEEK,
            RelativePeriod.LAST_4_BIWEEKS -> {
                val start = startOfDay(now)
                Pair(start, now)
            }
        }
    }

    fun isPeriodIdWithinRange(periodId: String, startDate: Date, endDate: Date): Boolean {
        val periodDate = parseDhis2PeriodToDate(periodId) ?: return false
        return !periodDate.before(startDate) && !periodDate.after(endDate)
    }

    fun parseDhis2PeriodToDate(periodId: String): Date? {
        return try {
            when {
                Regex("^\\d{4}$").matches(periodId) -> {
                    SimpleDateFormat("yyyy", Locale.ENGLISH).parse(periodId)
                }
                Regex("^\\d{6}$").matches(periodId) -> {
                    SimpleDateFormat("yyyyMM", Locale.ENGLISH).parse(periodId)
                }
                Regex("^\\d{8}$").matches(periodId) -> {
                    SimpleDateFormat("yyyyMMdd", Locale.ENGLISH).parse(periodId)
                }
                Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(periodId) -> {
                    SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).parse(periodId)
                }
                Regex("^\\d{4}W\\d{1,2}$").matches(periodId) -> {
                    val year = periodId.substring(0, 4).toInt()
                    val week = periodId.substring(5).toInt()
                    val cal = Calendar.getInstance(Locale.ENGLISH)
                    cal.clear()
                    cal.set(Calendar.YEAR, year)
                    cal.set(Calendar.WEEK_OF_YEAR, week)
                    cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    cal.time
                }
                Regex("^\\d{4}Q[1-4]$").matches(periodId) -> {
                    val year = periodId.substring(0, 4).toInt()
                    val quarter = periodId.substring(5).toInt()
                    val month = (quarter - 1) * 3
                    val cal = Calendar.getInstance(Locale.ENGLISH)
                    cal.clear()
                    cal.set(Calendar.YEAR, year)
                    cal.set(Calendar.MONTH, month)
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    cal.time
                }
                Regex("^\\d{4}S[1-2]$").matches(periodId) -> {
                    val year = periodId.substring(0, 4).toInt()
                    val semester = periodId.substring(5).toInt()
                    val month = if (semester == 1) 0 else 6
                    val cal = Calendar.getInstance(Locale.ENGLISH)
                    cal.clear()
                    cal.set(Calendar.YEAR, year)
                    cal.set(Calendar.MONTH, month)
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    cal.time
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
