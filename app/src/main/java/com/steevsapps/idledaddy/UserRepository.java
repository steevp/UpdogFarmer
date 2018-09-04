package com.steevsapps.idledaddy;

import android.arch.lifecycle.LiveData;

import com.steevsapps.idledaddy.db.AppDatabase;
import com.steevsapps.idledaddy.db.entity.User;

import java.util.List;

public class UserRepository {
    private static UserRepository sInstance;

    private final AppDatabase db;

    private final AppExecutors executors;

    public static UserRepository getInstance(AppDatabase db, AppExecutors executors) {
        if (sInstance == null) {
            synchronized (UserRepository.class) {
                if (sInstance == null) {
                    sInstance = new UserRepository(db, executors);
                }
            }
        }
        return sInstance;
    }

    private UserRepository(AppDatabase db, AppExecutors executors) {
        this.db = db;
        this.executors = executors;
    }

    public void setCurrentUser(String name) {
        executors.diskIO().execute(new Runnable() {
            @Override
            public void run() {
                db.userSettingsDao().setCurrentUser(name);
            }
        });
    }

    public void insertUser(User user) {
        executors.diskIO().execute(new Runnable() {
            @Override
            public void run() {
                db.userDao().insertUser(user);
            }
        });
    }

    public void updateUser(User user) {
        executors.diskIO().execute(new Runnable() {
            @Override
            public void run() {
                db.userDao().updateUser(user);
            }
        });
    }

    public void deleteUser(User user) {
        executors.diskIO().execute(new Runnable() {
            @Override
            public void run() {
                db.userDao().deleteUser(user);
            }
        });
    }

    public LiveData<List<User>> getAll() {
        return db.userDao().getAll();
    }

    public LiveData<User> getCurrentUser() {
        return db.userDao().getCurrentUser();
    }

    public User getCurrentUserSync() {
        return db.userDao().getCurrentUserSync();
    }
}
