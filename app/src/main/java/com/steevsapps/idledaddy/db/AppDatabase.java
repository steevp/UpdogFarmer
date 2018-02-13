package com.steevsapps.idledaddy.db;

import android.arch.persistence.room.Database;
import android.arch.persistence.room.Room;
import android.arch.persistence.room.RoomDatabase;
import android.arch.persistence.room.TypeConverters;
import android.content.Context;

import com.steevsapps.idledaddy.db.converter.GamesTypeConverter;
import com.steevsapps.idledaddy.db.converter.StringArrayTypeConverter;
import com.steevsapps.idledaddy.db.dao.UserDao;
import com.steevsapps.idledaddy.db.entity.User;

@Database(entities = {User.class}, version = 1)
@TypeConverters({GamesTypeConverter.class, StringArrayTypeConverter.class})
public abstract class AppDatabase extends RoomDatabase {
    private static AppDatabase ourInstance;

    public abstract UserDao userDao();

    public static AppDatabase getInstance(Context context) {
        if (ourInstance == null) {
            synchronized (AppDatabase.class) {
                if (ourInstance == null) {
                    ourInstance = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class,
                            "user-database")
                            .build();
                }
            }
        }
        return ourInstance;
    }
}
