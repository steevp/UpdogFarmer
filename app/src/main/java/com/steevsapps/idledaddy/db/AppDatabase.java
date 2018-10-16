package com.steevsapps.idledaddy.db;

import android.arch.persistence.db.SupportSQLiteDatabase;
import android.arch.persistence.room.Database;
import android.arch.persistence.room.Room;
import android.arch.persistence.room.RoomDatabase;
import android.arch.persistence.room.TypeConverters;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.steevsapps.idledaddy.AppExecutors;
import com.steevsapps.idledaddy.db.converter.GamesConverter;
import com.steevsapps.idledaddy.db.dao.UserDao;
import com.steevsapps.idledaddy.db.dao.UserSettingsDao;
import com.steevsapps.idledaddy.db.entity.UserSettings;
import com.steevsapps.idledaddy.db.entity.User;

@Database(entities = {User.class, UserSettings.class}, version = 1)
@TypeConverters({GamesConverter.class})
public abstract class AppDatabase extends RoomDatabase {
    private static AppDatabase sInstance;

    private final static String DATABASE_NAME = "users";

    public static AppDatabase getInstance(Context context, AppExecutors executors) {
        if (sInstance == null) {
            synchronized (AppDatabase.class) {
                if (sInstance == null) {
                    sInstance = Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, DATABASE_NAME)
                            .addCallback(new Callback() {
                                @Override
                                public void onCreate(@NonNull SupportSQLiteDatabase db) {
                                    super.onCreate(db);
                                    AppDatabase.populateDatabase(context.getApplicationContext(), sInstance, executors);
                                }
                            })
                            .build();
                }
            }
        }
        return sInstance;
    }

    public abstract UserDao userDao();

    public abstract UserSettingsDao userSettingsDao();

    private static void populateDatabase(Context context, AppDatabase db, AppExecutors executors) {
        executors.diskIO().execute(() -> {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

            final String username = prefs.getString("username", null);

            if (!TextUtils.isEmpty(username)) {
                // Migrate user info from SharedPreferences
                final User userEntity = new User(username);
                userEntity.setPassword(prefs.getString("password", ""));
                userEntity.setLoginKey(prefs.getString("login_key", ""));
                userEntity.setSentryHash(prefs.getString("sentry_hash", ""));
                userEntity.setParentalPin(prefs.getString("parental_pin", ""));
                userEntity.setPersonaName(prefs.getString("persona_name", ""));
                userEntity.setAvatarHash(prefs.getString("avatar_hash", ""));
                userEntity.setApiKey(prefs.getString("api_key", ""));
                db.userDao().insertUser(userEntity);
            }

            // Set default user
            db.userSettingsDao().insert(new UserSettings(username));
        });
    }
}
