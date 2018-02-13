package com.steevsapps.idledaddy;

import android.arch.lifecycle.LiveData;

import com.steevsapps.idledaddy.db.AppDatabase;
import com.steevsapps.idledaddy.db.entity.User;
import com.steevsapps.idledaddy.steam.wrapper.Game;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class UserRepository {
    private static UserRepository ourInstance;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private final AppDatabase db;
    private String currentUser;
    private boolean initialized = false;

    private UserRepository(AppDatabase db) {
        this.db = db;
    }

    public static UserRepository getInstance(AppDatabase db) {
        if (ourInstance == null) {
            synchronized (UserRepository.class) {
                if (ourInstance == null) {
                    ourInstance = new UserRepository(db);
                }
            }
        }
        return ourInstance;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void init(String name) {
        if (!name.isEmpty()) {
            currentUser = name;
            initialized = true;
        }
    }

    private void throwIfNotInitialized() {
        if (!isInitialized()) {
            throw new IllegalStateException(this.toString() + " is not initialized!");
        }
    }

    public LiveData<List<User>> getAll() {
        return db.userDao().getAll();
    }

    public LiveData<User> getUser() {
        throwIfNotInitialized();
        return db.userDao().findByName(currentUser);
    }

    public User getUserSync() {
        throwIfNotInitialized();
        return db.userDao().findByNameSync(currentUser);
    }

    public void updateLastSession(final ArrayList<Game> lastSession) {
        throwIfNotInitialized();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                db.userDao().updateLastSession(currentUser, lastSession);
            }
        });
    }

    public void updateLoginKey(final String loginKey) {
        throwIfNotInitialized();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                db.userDao().updateLoginKey(currentUser, loginKey);
            }
        });
    }

    public void updateSentryHash(final String sentryHash) {
        throwIfNotInitialized();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                db.userDao().updateSentryHash(currentUser, sentryHash);
            }
        });
    }

    public void updateApiKey(final String apiKey) {
        throwIfNotInitialized();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                db.userDao().updateApiKey(currentUser, apiKey);
            }
        });
    }

    public void updatePersonaNameAndAvatarHash(final String personaName, final String avatarHash) {
        throwIfNotInitialized();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                db.userDao().updatePersonaNameAndAvatarHash(currentUser, personaName, avatarHash);
            }
        });
    }

    public boolean hasUser(String name) {
        return db.userDao().hasUser(name);
    }

    public void addUser(final User user) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                db.userDao().addUser(user);
            }
        });
    }

    public void addUserSync(User user) {
        db.userDao().addUser(user);
    }

    public void removeUser(final String name) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                db.userDao().removeUser(name);
            }
        });
    }

    public void updateUser(final User user) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                db.userDao().updateUser(user);
            }
        });
    }
}
