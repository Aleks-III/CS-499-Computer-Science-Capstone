package com.zybooks.weight_tracker;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Provides analytics over historical weight entries.
 * <p>
 * Category Two (Algorithms & Data Structures):
 * - sorting entries by date
 * - windowed averaging (7/30 day rolling windows)
 * - rate-of-change calculations
 * - goal ETA prediction based on recent trend
 */
public final class WeightAnalytics {

    public enum TrendType { LOSS, GAIN, FLAT, INSUFFICIENT }

    private static final String DATE_PATTERN = "MM/dd/yyyy";
    private static final double FLAT_EPSILON_LBS_PER_DAY = 0.02; // treat tiny slope as "flat"

    private WeightAnalytics() { }

    public static final class Result {
        public final int totalEntries;
        public final Double weeklyAverage;   // 7-day rolling average (date window)
        public final Double monthlyAverage;  // 30-day rolling average (date window)
        public final Double rateLbsPerDay;   // lbs/day (prefer 7-day slope; fall back to overall slope)
        public final TrendType trendType;    // Derived from rateLbsPerDay (LOSS/GAIN/FLAT/INSUFFICIENT)
        public final Integer etaDays;        // Null when ETA cannot be estimated

        private Result(int totalEntries,
                       Double weeklyAverage,
                       Double monthlyAverage,
                       Double rateLbsPerDay,
                       TrendType trendType,
                       Integer etaDays) {
            this.totalEntries = totalEntries;
            this.weeklyAverage = weeklyAverage;
            this.monthlyAverage = monthlyAverage;
            this.rateLbsPerDay = rateLbsPerDay;
            this.trendType = trendType;
            this.etaDays = etaDays;
        }
    }

    /**
     * Computes analytics from stored entries.
     *
     * @param entries user's weight history
     * @param goalWeight user's current goal (<=0 means no goal)
     * @return computed analytics result
     */
    public static Result analyze(List<WeightEntry> entries, double goalWeight) {
        List<DatedWeight> dated = toDatedWeights(entries);

        // Filter out entries with invalid/unparseable dates to avoid crashing analytics.
        if (dated.isEmpty()) {
            return new Result(
                    0,
                    null,
                    null,
                    null,
                    TrendType.INSUFFICIENT,
                    null);
        }

        // Sort by date ascending (oldest -> newest) for trend/slope computations.
        dated.sort(Comparator.comparingLong(dw -> dw.epochDay));

        Double weeklyAvg = rollingAverage(dated, 7);
        Double monthlyAvg = rollingAverage(dated, 30);

        long spanDays = dated.get(dated.size() - 1).epochDay - dated.get(0).epochDay;

        // Enforce at least 7 days of coverage before showing rate/trend/ETA
        if (spanDays < 7) {
            return new Result(
                    dated.size(),
                    weeklyAvg,
                    monthlyAvg,
                    null,
                    TrendType.INSUFFICIENT,
                    null
            );
        }

        RateInfo recent = rateOfChangeInLast7Days(dated);
        RateInfo fallback = rateOfChange(dated);

        RateInfo chosen = (recent != null) ? recent : fallback;
        Double rate = (chosen != null) ? chosen.lbsPerDay : null;

        TrendType trend = trendType(rate);
        EtaInfo eta = estimateGoalEta(dated, goalWeight, rate);

        return new Result(
                dated.size(),
                weeklyAvg,
                monthlyAvg,
                rate,
                trend,
                eta != null ? eta.etaDays : null
        );
    }

    // =====================================================
    // Internal data structure
    // =====================================================

    /**
     * Internal representation for analytics computations.
     * Storing epochDay enables fast comparisons and day-difference math.
     */
    private static final class DatedWeight {
        final long epochDay;
        final double weight;

        DatedWeight(long epochDay, double weight) {
            this.epochDay = epochDay;
            this.weight = weight;
        }
    }

    private static List<DatedWeight> toDatedWeights(List<WeightEntry> entries) {
        List<DatedWeight> list = new ArrayList<>();
        if (entries == null) return list;

        for (WeightEntry e : entries) {
            Long day = parseToEpochDay(e.getDate());
            if (day != null) {
                list.add(new DatedWeight(day, e.getWeight()));
            }
        }
        return list;
    }

    /**
     * Converts "MM/dd/yyyy" into epoch day (days since 1970-01-01).
     * Using epoch-day keeps arithmetic simple and avoids timezone issues.
     */
    private static Long parseToEpochDay(String dateStr) {
        if (dateStr == null) return null;

        try {
            SimpleDateFormat sdf = new SimpleDateFormat(DATE_PATTERN, Locale.getDefault());
            sdf.setLenient(false);
            Date d = sdf.parse(dateStr);

            if (d == null) return null;

            long millis = d.getTime();
            return millis / (24L * 60L * 60L * 1000L);
        } catch (ParseException ex) {
            return null;
        }
    }

    // =====================================================
    // Rolling averages
    // =====================================================

    /**
     * Computes the average weight across entries that fall within the last {@code daysWindow} days,
     * anchored to the most recent entry date.
     */
    private static Double rollingAverage(List<DatedWeight> sortedByDateAsc, int daysWindow) {
        if (sortedByDateAsc.isEmpty()) return null;

        long lastDay = sortedByDateAsc.get(sortedByDateAsc.size() - 1).epochDay;
        long cutoff = lastDay - (daysWindow - 1);

        double sum = 0.0;
        int count = 0;

        // Walk backward from the newest entry until the cutoff date is reached.
        for (int i = sortedByDateAsc.size() - 1; i >= 0; i--) {
            DatedWeight dw = sortedByDateAsc.get(i);
            if (dw.epochDay < cutoff) break;
            sum += dw.weight;
            count++;
        }

        return count > 0 ? (sum / count) : null;
    }

    // =====================================================
    // Trend & rate-of-change
    // =====================================================

    private static TrendType trendType(Double lbsPerDay) {
        if (lbsPerDay == null) return TrendType.INSUFFICIENT;
        if (Math.abs(lbsPerDay) < FLAT_EPSILON_LBS_PER_DAY) return TrendType.FLAT;
        return lbsPerDay < 0 ? TrendType.LOSS : TrendType.GAIN;
    }

    private static final class RateInfo {
        final double lbsPerDay;

        RateInfo(double lbsPerDay) {
            this.lbsPerDay = lbsPerDay;
        }
    }

    /**
     * Computes slope using the most recent entry and the most recent earlier distinct day.
     */
    private static RateInfo rateOfChange(List<DatedWeight> sortedByDateAsc) {
        if (sortedByDateAsc.size() < 2) return null;

        DatedWeight last = sortedByDateAsc.get(sortedByDateAsc.size() - 1);

        // Find the most recent entry that occurred on an earlier day than the last entry.
        DatedWeight prevDistinctDay = null;
        for (int i = sortedByDateAsc.size() - 2; i >= 0; i--) {
            DatedWeight candidate = sortedByDateAsc.get(i);
            if (candidate.epochDay < last.epochDay) {
                prevDistinctDay = candidate;
                break;
            }
        }

        if (prevDistinctDay == null) {
            // All entries are on the same day -> can't compute lbs/day
            return null;
        }

        long days = last.epochDay - prevDistinctDay.epochDay;
        double delta = last.weight - prevDistinctDay.weight;

        return new RateInfo(delta / (double) days);
    }

    /** Computes lbs/day slope using entries within the last 7 days (by date window). */
    private static RateInfo rateOfChangeInLast7Days(List<DatedWeight> sortedByDateAsc) {
        final int daysWindow = 7;

        if (sortedByDateAsc.size() < 2) return null;

        long lastDay = sortedByDateAsc.get(sortedByDateAsc.size() - 1).epochDay;
        long cutoff = lastDay - (daysWindow - 1);

        // Find first entry within the window
        int startIdx = -1;
        for (int i = 0; i < sortedByDateAsc.size(); i++) {
            if (sortedByDateAsc.get(i).epochDay >= cutoff) {
                startIdx = i;
                break;
            }
        }
        if (startIdx == -1) return null;

        // Use the latest entry as the end, and find an earlier distinct day within window
        DatedWeight last = sortedByDateAsc.get(sortedByDateAsc.size() - 1);

        DatedWeight firstDistinct = null;
        for (int i = startIdx; i < sortedByDateAsc.size(); i++) {
            DatedWeight candidate = sortedByDateAsc.get(i);
            if (candidate.epochDay < last.epochDay) {
                firstDistinct = candidate;
                break;
            }
        }
        if (firstDistinct == null) return null;

        long days = last.epochDay - firstDistinct.epochDay;
        if (days <= 0) return null;

        double delta = last.weight - firstDistinct.weight;
        return new RateInfo(delta / (double) days);
    }

    // =====================================================
    // Goal ETA prediction
    // =====================================================

    private static final class EtaInfo {
        final int etaDays;

        EtaInfo(int etaDays) {
            this.etaDays = etaDays;
        }
    }

    /**
     * Estimates goal completion assuming linear trend continues.
     * <p>
     * Rules:
     * - Requires a valid goalWeight (>0), a latest weight, and a non-flat slope.
     * - Only predicts ETA if the slope is moving toward the goal.
     */
    private static EtaInfo estimateGoalEta(List<DatedWeight> sortedByDateAsc,
                                           double goalWeight,
                                           Double lbsPerDay) {
        if (goalWeight <= 0) return null;
        if (sortedByDateAsc.isEmpty()) return null;
        if (lbsPerDay == null) return null;
        if (Math.abs(lbsPerDay) < FLAT_EPSILON_LBS_PER_DAY) return null;

        DatedWeight last = sortedByDateAsc.get(sortedByDateAsc.size() - 1);

        double remaining = goalWeight - last.weight;

        if (Math.abs(remaining) < 0.0001) {
            return new EtaInfo(0);
        }

        // Only estimate ETA if the current trend is moving toward the goal.
        if (remaining < 0 && lbsPerDay >= 0) return null;
        if (remaining > 0 && lbsPerDay <= 0) return null;

        double daysDouble = remaining / lbsPerDay;
        int days = (int) Math.ceil(Math.abs(daysDouble));

        return new EtaInfo(days);
    }
}