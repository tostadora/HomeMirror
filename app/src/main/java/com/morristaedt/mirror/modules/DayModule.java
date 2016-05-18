package com.morristaedt.mirror.modules;

import android.content.res.Resources;
import android.support.annotation.NonNull;
import android.text.Html;
import android.text.Spanned;

import com.morristaedt.mirror.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Created by HannahMitt on 8/23/15.
 */
public class DayModule {

    public static Spanned getDay() {
        SimpleDateFormat formatDayOfMonth = new SimpleDateFormat("EEEE");
        SimpleDateFormat month = new SimpleDateFormat("MMMM");

        Calendar now = Calendar.getInstance();
        int dayOfMonth = now.get(Calendar.DAY_OF_MONTH);

        if (Locale.getDefault() == Locale.US) {
            return Html.fromHtml(formatDayOfMonth.format(now.getTime()) + " the " + dayOfMonth + "<sup><small>" + getDayOfMonthSuffix(dayOfMonth) + "</small></sup>");
        }
        else {
            return Html.fromHtml(formatDayOfMonth.format(now.getTime()) + ", " + dayOfMonth + " " + month.format(now.getTime()));
        }
    }

    @NonNull
    public static String getTimeOfDayWelcome(@NonNull Resources resources) {
        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);
        int timeRes;
        if (hour < 4) {
            timeRes = R.string.late;
        } else if (hour < 12) {
            timeRes = R.string.good_morning;
        } else if (hour < 17) { // 5pm
            timeRes = R.string.good_afternoon;
        } else if (hour < 22) { // 10pm
            timeRes = R.string.good_evening;
        } else { // 10pm - midnight is bedtime
            timeRes = R.string.bedtime;
        }

        return resources.getString(timeRes, resources.getString(R.string.owners));
    }

    private static String getDayOfMonthSuffix(final int n) {
        if (n >= 11 && n <= 13) {
            return "th";
        }
        switch (n % 10) {
            case 1:
                return "st";
            case 2:
                return "nd";
            case 3:
                return "rd";
            default:
                return "th";
        }
    }
}
