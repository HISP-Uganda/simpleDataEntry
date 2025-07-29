package com.ash.simpledataentry.util

import com.ash.simpledataentry.domain.model.RelativePeriod
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class PeriodHelper {
    fun getPeriodIds(relativePeriod: RelativePeriod): List<String> {
        val (startDate, endDate) = getDateRangeForRelativePeriod(relativePeriod)
        return getPeriodIds(startDate, endDate)
    }

    fun getPeriodIds(startDate: Date, endDate: Date): List<String> {
        val periodIds = mutableListOf<String>()
        val calendar = Calendar.getInstance()
        calendar.time = startDate

        val endCalendar = Calendar.getInstance()
        endCalendar.time = endDate

        val format = SimpleDateFormat("yyyyMMdd", Locale.getDefault())

        while (calendar.before(endCalendar) || calendar.equals(endCalendar)) {
            periodIds.add(format.format(calendar.time))
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return periodIds
    }

    private fun getDateRangeForRelativePeriod(relativePeriod: RelativePeriod): Pair<Date, Date> {
        val calendar = Calendar.getInstance()
        val now = Date()

        when (relativePeriod) {
            // Daily periods
            RelativePeriod.TODAY -> {
                calendar.time = now
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.time
                
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                return Pair(startDate, calendar.time)
            }
            
            RelativePeriod.YESTERDAY -> {
                calendar.time = now
                calendar.add(Calendar.DAY_OF_YEAR, -1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.time
                
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                return Pair(startDate, calendar.time)
            }
            
            RelativePeriod.LAST_3_DAYS -> {
                calendar.time = now
                calendar.add(Calendar.DAY_OF_YEAR, -3)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.time
                return Pair(startDate, now)
            }
            
            RelativePeriod.LAST_7_DAYS -> {
                calendar.time = now
                calendar.add(Calendar.DAY_OF_YEAR, -7)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.time
                return Pair(startDate, now)
            }
            
            RelativePeriod.LAST_14_DAYS -> {
                calendar.time = now
                calendar.add(Calendar.DAY_OF_YEAR, -14)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.time
                return Pair(startDate, now)
            }
            
            // Weekly periods
            RelativePeriod.THIS_WEEK -> {
                calendar.time = now
                calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.time
                
                calendar.add(Calendar.DAY_OF_YEAR, 6)
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                return Pair(startDate, calendar.time)
            }
            
            RelativePeriod.LAST_WEEK -> {
                calendar.time = now
                calendar.add(Calendar.WEEK_OF_YEAR, -1)
                calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.time
                
                calendar.add(Calendar.DAY_OF_YEAR, 6)
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                return Pair(startDate, calendar.time)
            }
            
            RelativePeriod.LAST_4_WEEKS -> {
                calendar.time = now
                calendar.add(Calendar.WEEK_OF_YEAR, -4)
                calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.time
                return Pair(startDate, now)
            }
            
            RelativePeriod.LAST_12_WEEKS -> {
                calendar.time = now
                calendar.add(Calendar.WEEK_OF_YEAR, -12)
                calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.time
                return Pair(startDate, now)
            }
            
            // Monthly periods
            RelativePeriod.THIS_MONTH -> {
                calendar.time = now
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.time
                
                calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                return Pair(startDate, calendar.time)
            }
            
            RelativePeriod.LAST_MONTH -> {
                calendar.time = now
                calendar.add(Calendar.MONTH, -1)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.time
                
                calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                return Pair(startDate, calendar.time)
            }
            
            RelativePeriod.LAST_3_MONTHS -> {
                calendar.time = now
                calendar.add(Calendar.MONTH, -3)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.time
                return Pair(startDate, now)
            }
            
            RelativePeriod.LAST_6_MONTHS -> {
                calendar.time = now
                calendar.add(Calendar.MONTH, -6)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.time
                return Pair(startDate, now)
            }
            
            RelativePeriod.LAST_12_MONTHS -> {
                calendar.time = now
                calendar.add(Calendar.MONTH, -12)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.time
                return Pair(startDate, now)
            }
            
            // Bi-monthly periods
            RelativePeriod.THIS_BIMONTH -> {
                calendar.time = now
                val currentMonth = calendar.get(Calendar.MONTH)
                val bimonthStart = (currentMonth / 2) * 2
                calendar.set(Calendar.MONTH, bimonthStart)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.time
                
                calendar.add(Calendar.MONTH, 2)
                calendar.add(Calendar.DAY_OF_MONTH, -1)
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                return Pair(startDate, calendar.time)
            }
            
            RelativePeriod.LAST_BIMONTH -> {
                calendar.time = now
                val currentMonth = calendar.get(Calendar.MONTH)
                val lastBimonthStart = ((currentMonth / 2) - 1) * 2
                calendar.set(Calendar.MONTH, lastBimonthStart)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.time
                
                calendar.add(Calendar.MONTH, 2)
                calendar.add(Calendar.DAY_OF_MONTH, -1)
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                return Pair(startDate, calendar.time)
            }
            
            RelativePeriod.LAST_6_BIMONTHS -> {
                calendar.time = now
                calendar.add(Calendar.MONTH, -12)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.time
                return Pair(startDate, now)
            }
            
            // Quarterly periods
            RelativePeriod.THIS_QUARTER -> {
                calendar.time = now
                val currentMonth = calendar.get(Calendar.MONTH)
                val quarterStart = (currentMonth / 3) * 3
                calendar.set(Calendar.MONTH, quarterStart)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.time
                
                calendar.add(Calendar.MONTH, 3)
                calendar.add(Calendar.DAY_OF_MONTH, -1)
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                return Pair(startDate, calendar.time)
            }
            
            RelativePeriod.LAST_QUARTER -> {
                calendar.time = now
                val currentMonth = calendar.get(Calendar.MONTH)
                val lastQuarterStart = ((currentMonth / 3) - 1) * 3
                calendar.set(Calendar.MONTH, lastQuarterStart)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.time
                
                calendar.add(Calendar.MONTH, 3)
                calendar.add(Calendar.DAY_OF_MONTH, -1)
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                return Pair(startDate, calendar.time)
            }
            
            RelativePeriod.LAST_4_QUARTERS -> {
                calendar.time = now
                calendar.add(Calendar.MONTH, -12)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.time
                return Pair(startDate, now)
            }
            
            // Six-monthly periods
            RelativePeriod.THIS_SIX_MONTH -> {
                calendar.time = now
                val currentMonth = calendar.get(Calendar.MONTH)
                val sixMonthStart = if (currentMonth < 6) 0 else 6
                calendar.set(Calendar.MONTH, sixMonthStart)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.time
                
                calendar.add(Calendar.MONTH, 6)
                calendar.add(Calendar.DAY_OF_MONTH, -1)
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                return Pair(startDate, calendar.time)
            }
            
            RelativePeriod.LAST_SIX_MONTH -> {
                calendar.time = now
                val currentMonth = calendar.get(Calendar.MONTH)
                val lastSixMonthStart = if (currentMonth < 6) -6 else 0
                calendar.add(Calendar.MONTH, lastSixMonthStart)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.time
                
                calendar.add(Calendar.MONTH, 6)
                calendar.add(Calendar.DAY_OF_MONTH, -1)
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                return Pair(startDate, calendar.time)
            }
            
            RelativePeriod.LAST_2_SIXMONTHS -> {
                calendar.time = now
                calendar.add(Calendar.MONTH, -12)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.time
                return Pair(startDate, now)
            }
            
            // Yearly periods
            RelativePeriod.THIS_YEAR -> {
                calendar.time = now
                calendar.set(Calendar.DAY_OF_YEAR, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.time
                
                calendar.set(Calendar.DAY_OF_YEAR, calendar.getActualMaximum(Calendar.DAY_OF_YEAR))
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                return Pair(startDate, calendar.time)
            }
            
            RelativePeriod.LAST_YEAR -> {
                calendar.time = now
                calendar.add(Calendar.YEAR, -1)
                calendar.set(Calendar.DAY_OF_YEAR, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.time
                
                calendar.set(Calendar.DAY_OF_YEAR, calendar.getActualMaximum(Calendar.DAY_OF_YEAR))
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                return Pair(startDate, calendar.time)
            }
            
            RelativePeriod.LAST_5_YEARS -> {
                calendar.time = now
                calendar.add(Calendar.YEAR, -5)
                calendar.set(Calendar.DAY_OF_YEAR, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.time
                return Pair(startDate, now)
            }
            
            // Financial years (assuming April-March financial year)
            RelativePeriod.THIS_FINANCIAL_YEAR -> {
                calendar.time = now
                val currentMonth = calendar.get(Calendar.MONTH)
                if (currentMonth >= Calendar.APRIL) {
                    // Current financial year started in April of this year
                    calendar.set(Calendar.MONTH, Calendar.APRIL)
                } else {
                    // Current financial year started in April of last year
                    calendar.add(Calendar.YEAR, -1)
                    calendar.set(Calendar.MONTH, Calendar.APRIL)
                }
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.time
                
                calendar.add(Calendar.YEAR, 1)
                calendar.add(Calendar.DAY_OF_MONTH, -1)
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                return Pair(startDate, calendar.time)
            }
            
            RelativePeriod.LAST_FINANCIAL_YEAR -> {
                calendar.time = now
                val currentMonth = calendar.get(Calendar.MONTH)
                if (currentMonth >= Calendar.APRIL) {
                    // Last financial year was April of last year to March of this year
                    calendar.add(Calendar.YEAR, -1)
                    calendar.set(Calendar.MONTH, Calendar.APRIL)
                } else {
                    // Last financial year was April of two years ago to March of last year
                    calendar.add(Calendar.YEAR, -2)
                    calendar.set(Calendar.MONTH, Calendar.APRIL)
                }
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.time
                
                calendar.add(Calendar.YEAR, 1)
                calendar.add(Calendar.DAY_OF_MONTH, -1)
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                return Pair(startDate, calendar.time)
            }
        }
    }
    
    fun formatDateRange(startDate: Date, endDate: Date): String {
        val calendar = Calendar.getInstance()
        calendar.time = startDate
        val startMonth = calendar.get(Calendar.MONTH) + 1
        val startYear = calendar.get(Calendar.YEAR)
        
        calendar.time = endDate
        val endMonth = calendar.get(Calendar.MONTH) + 1
        val endYear = calendar.get(Calendar.YEAR)
        
        return if (startYear == endYear) {
            if (startMonth == endMonth) {
                "$startMonth/$startYear"
            } else {
                "$startMonth-$endMonth/$startYear"
            }
        } else {
            "$startMonth/$startYear - $endMonth/$endYear"
        }
    }
}
