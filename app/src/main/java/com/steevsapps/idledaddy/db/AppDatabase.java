package com.steevsapps.idledaddy.db;

import android.arch.persistence.db.SupportSQLiteDatabase;
import android.arch.persistence.room.Database;
import android.arch.persistence.room.Room;
import android.arch.persistence.room.RoomDatabase;
import android.arch.persistence.room.TypeConverters;
import android.content.Context;
import android.support.annotation.NonNull;

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
                                    AppDatabase.populateDatabase(sInstance, executors);
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

    private static void populateDatabase(AppDatabase db, AppExecutors executors) {
        executors.diskIO().execute(() -> {
            // Populate the user_settings table
            db.userSettingsDao().insert(new UserSettings(null));
        });
    }
}
