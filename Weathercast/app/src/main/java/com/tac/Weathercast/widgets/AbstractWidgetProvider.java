package com.tac.Weathercast.widgets;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.RemoteViews;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import com.tac.Weathercast.BuildConfig;
import com.tac.Weathercast.activities.MainActivity;
import com.tac.Weathercast.R;
import com.tac.Weathercast.models.Weather;
import com.tac.Weathercast.utils.UnitConvertor;

public abstract class AbstractWidgetProvider extends AppWidgetProvider {
    protected static final long DURATION_MINUTE = TimeUnit.SECONDS.toMillis(30);
    protected static final String ACTION_UPDATE_TIME = "cz.martykan.com.tac.Weathercast.UPDATE_TIME";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_UPDATE_TIME.equals(intent.getAction())) {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            ComponentName provider = new ComponentName(context.getPackageName(), getClass().getName());
            int ids[] = appWidgetManager.getAppWidgetIds(provider);
            onUpdate(context, appWidgetManager, ids);
        } else {
            super.onReceive(context, intent);
        }
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);

        Log.d(this.getClass().getSimpleName(), "Disable updates for this widget");
        cancelUpdate(context);
    }

    protected Bitmap getWeatherIcon(String text, Context context) {
        Bitmap myBitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_4444);
        Canvas myCanvas = new Canvas(myBitmap);
        Paint paint = new Paint();
        Typeface clock = Typeface.createFromAsset(context.getAssets(), "fonts/weather.ttf");
        paint.setAntiAlias(true);
        paint.setSubpixelText(true);
        paint.setTypeface(clock);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setTextSize(150);
        paint.setTextAlign(Paint.Align.CENTER);
        myCanvas.drawText(text, 128, 180, paint);
        return myBitmap;
    }

    private String setWeatherIcon(int actualId, int hourOfDay, Context context) {
        int id = actualId / 100;
        String icon = "";
        if (actualId == 800) {
            if (hourOfDay >= 7 && hourOfDay < 20) {
                icon = context.getString(R.string.weather_sunny);
            } else {
                icon = context.getString(R.string.weather_clear_night);
            }
        } else {
            switch (id) {
                case 2:
                    icon = context.getString(R.string.weather_thunder);
                    break;
                case 3:
                    icon = context.getString(R.string.weather_drizzle);
                    break;
                case 7:
                    icon = context.getString(R.string.weather_foggy);
                    break;
                case 8:
                    icon = context.getString(R.string.weather_cloudy);
                    break;
                case 6:
                    icon = context.getString(R.string.weather_snowy);
                    break;
                case 5:
                    icon = context.getString(R.string.weather_rainy);
                    break;
            }
        }
        return icon;
    }

    protected Weather parseWidgetJson(String result, Context context) {
        try {
            MainActivity.initMappings();

            JSONObject reader = new JSONObject(result);
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);

            // Temperature
            float temperature = UnitConvertor.convertTemperature(Float.parseFloat(reader.optJSONObject("main").getString("temp").toString()), sp);
            if (sp.getBoolean("temperatureInteger", false)) {
                temperature = Math.round(temperature);
            }

            // Wind
            double wind;
            try {
                wind = Double.parseDouble(reader.optJSONObject("wind").getString("speed").toString());
            } catch (Exception e) {
                e.printStackTrace();
                wind = 0;
            }
            wind = UnitConvertor.convertWind(wind, sp);

            // Pressure
            double pressure = UnitConvertor.convertPressure((float) Double.parseDouble(reader.optJSONObject("main").getString("pressure").toString()), sp);

            long lastUpdateTimeInMillis = sp.getLong("lastUpdate", -1);
            String lastUpdate;
            if (lastUpdateTimeInMillis < 0) {
                // No time
                lastUpdate = "";
            } else {
                lastUpdate = context.getString(R.string.last_update_widget, MainActivity.formatTimeWithDayIfNotToday(context, lastUpdateTimeInMillis));
            }

            String description = reader.optJSONArray("weather").getJSONObject(0).getString("description");
            description = description.substring(0,1).toUpperCase() + description.substring(1);

            Weather widgetWeather = new Weather();
            widgetWeather.setCity(reader.getString("name"));
            widgetWeather.setCountry(reader.optJSONObject("sys").getString("country"));
            widgetWeather.setTemperature(Math.round(temperature) + localize(sp, context, "unit", "C"));
            widgetWeather.setDescription(description);
            widgetWeather.setWind(context.getString(R.string.wind) + ": " + new DecimalFormat("0.0").format(wind) + " " + localize(sp, context, "speedUnit", "m/s")
                    + (widgetWeather.isWindDirectionAvailable() ? " " + MainActivity.getWindDirectionString(sp, context, widgetWeather) : ""));
            widgetWeather.setPressure(context.getString(R.string.pressure) + ": " + new DecimalFormat("0.0").format(pressure) + " " + localize(sp, context, "pressureUnit", "hPa"));
            widgetWeather.setHumidity(reader.optJSONObject("main").getString("humidity"));
            widgetWeather.setSunrise(reader.optJSONObject("sys").getString("sunrise"));
            widgetWeather.setSunset(reader.optJSONObject("sys").getString("sunset"));
            widgetWeather.setIcon(setWeatherIcon(Integer.parseInt(reader.optJSONArray("weather").getJSONObject(0).getString("id")), Calendar.getInstance().get(Calendar.HOUR_OF_DAY), context));
            widgetWeather.setLastUpdated(lastUpdate);

            return widgetWeather;
        } catch (JSONException e) {
            Log.e("JSONException Data", result);
            e.printStackTrace();
            return new Weather();
        }
    }

    protected String localize(SharedPreferences sp, Context context, String preferenceKey,
                              String defaultValueKey) {
        return MainActivity.localize(sp, context, preferenceKey, defaultValueKey);
    }

    public static void updateWidgets(Context context) {
        updateWidgets(context, ExtensiveWidgetProvider.class);
        updateWidgets(context, TimeWidgetProvider.class);
        updateWidgets(context, SimpleWidgetProvider.class);
    }

    private static void updateWidgets(Context context, Class widgetClass) {
        Intent intent = new Intent(context.getApplicationContext(), widgetClass)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        int[] ids = AppWidgetManager.getInstance(context.getApplicationContext())
                .getAppWidgetIds(new ComponentName(context.getApplicationContext(), widgetClass));
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        context.getApplicationContext().sendBroadcast(intent);
    }

    protected void setTheme(Context context, RemoteViews remoteViews) {
        if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("transparentWidget", false)){
            remoteViews.setInt(R.id.widgetRoot, "setBackgroundResource", R.drawable.widget_card_transparent);
            return;
        }
        String theme = PreferenceManager.getDefaultSharedPreferences(context).getString("theme", "fresh");
        switch (theme) {
            case "dark":
            case "classicdark":
                remoteViews.setInt(R.id.widgetRoot, "setBackgroundResource", R.drawable.widget_card_dark);
                break;
            case "black":
            case "classicblack":
                remoteViews.setInt(R.id.widgetRoot, "setBackgroundResource", R.drawable.widget_card_black);
                break;
            case "classic":
                remoteViews.setInt(R.id.widgetRoot, "setBackgroundResource", R.drawable.widget_card_classic);
                break;
            default:
                remoteViews.setInt(R.id.widgetRoot, "setBackgroundResource", R.drawable.widget_card);
                break;
        }
    }

    protected void scheduleNextUpdate(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        long now = new Date().getTime();
        long nextUpdate = now + DURATION_MINUTE - now % DURATION_MINUTE;
        if (BuildConfig.DEBUG) {
            Log.v(this.getClass().getSimpleName(), "Next widget update: " +
                    android.text.format.DateFormat.getTimeFormat(context).format(new Date(nextUpdate)));
        }
        if (Build.VERSION.SDK_INT >= 19) {
            alarmManager.setExact(AlarmManager.RTC, nextUpdate, getTimeIntent(context));
        } else {
            alarmManager.set(AlarmManager.RTC, nextUpdate, getTimeIntent(context));
        }
    }

    protected void cancelUpdate(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(getTimeIntent(context));
    }

    protected PendingIntent getTimeIntent(Context context) {
        Intent intent = new Intent(context, this.getClass());
        intent.setAction(ACTION_UPDATE_TIME);
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
    }
}
