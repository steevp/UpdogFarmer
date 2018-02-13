package com.steevsapps.idledaddy;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;

import com.steevsapps.idledaddy.preferences.PrefsManager;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class LocaleManager {
    private final static String TAG = LocaleManager.class.getSimpleName();

    /**
     * Set the locale from SharedPreferences.
     * Called from attachBaseContext and onConfigurationChanged of the Application class and
     * attachBaseContext of Activities and Services
     */
    public static Context setLocale(Context context) {
        final String language;
        if (!PrefsManager.getLanguage().isEmpty()) {
            language = PrefsManager.getLanguage();
        } else {
            language = setInitialValue(context.getResources());
        }
        return updateResources(context, language);
    }

    private static String setInitialValue(Resources res) {
        final List<String> tags = Arrays.asList(res.getStringArray(R.array.language_option_values));
        final Locale locale = getLocale(res);
        final String languageTag = toLanguageTag(locale);
        final String language;
        if (tags.contains(languageTag)) {
            language = languageTag;
        } else if (tags.contains(locale.getLanguage())) {
            language = locale.getLanguage();
        } else {
            language = "en";
        }
        PrefsManager.writeLanguage(language);
        return language;
    }

    private static Context updateResources(Context context, String language) {
        final Locale locale = forLanguageTag(language);
        Locale.setDefault(locale);

        final Resources res = context.getResources();
        final Configuration config = new Configuration(res.getConfiguration());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLocale(locale);
            context = context.createConfigurationContext(config);
        } else {
            config.locale = locale;
            res.updateConfiguration(config, res.getDisplayMetrics());
        }
        return context;
    }

    private static Locale forLanguageTag(String languageTag) {
        final String[] locale = languageTag.split("-");
        return locale.length == 2 ? new Locale(locale[0], locale[1]) : new Locale(languageTag);
    }

    private static String toLanguageTag(Locale locale) {
        if (!locale.getCountry().isEmpty()) {
            return String.format("%s-%s", locale.getLanguage(), locale.getCountry());
        }
        return locale.getLanguage();
    }

    private static Locale getLocale(Resources res) {
        final Configuration config = res.getConfiguration();
        return  Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ? config.getLocales().get(0) : config.locale;
    }
}
