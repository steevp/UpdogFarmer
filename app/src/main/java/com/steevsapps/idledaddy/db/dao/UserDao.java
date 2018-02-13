package com.steevsapps.idledaddy.db.dao;


import android.arch.lifecycle.LiveData;
import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Transaction;
import android.arch.persistence.room.Update;

import com.steevsapps.idledaddy.db.entity.User;
import com.steevsapps.idledaddy.steam.wrapper.Game;

import java.util.ArrayList;
import java.util.List;

import static android.arch.persistence.room.OnConflictStrategy.REPLACE;

@Dao
public abstract class UserDao {
    @Query("SELECT * FROM users")
    public abstract LiveData<List<User>> getAll();

    @Query("SELECT * FROM users")
    public abstract List<User> getAllSync();

    @Query("SELECT * FROM users WHERE username = :name")
    public abstract LiveData<User> findByName(String name);

    @Query("SELECT * FROM users WHERE username = :name")
    public abstract User findByNameSync(String name);

    @Query("SELECT COUNT(*) FROM users")
    public abstract int getCount();

    @Update
    public abstract void updateUser(User user);

    @Insert(onConflict = REPLACE)
    public abstract void addUser(User user);

    @Delete
    public abstract void removeUser(User user);

    @Query("DELETE FROM users WHERE username = :name")
    public abstract void removeUser(String name);

    @Transaction
    public void updatePersonaNameAndAvatarHash(String name, String personaName, String avatarHash) {
        updatePersonaName(name, personaName);
        updateAvatarHash(name, avatarHash);
    }

    @Query("UPDATE users SET persona_name = :personaName WHERE username = :name")
    public abstract void updatePersonaName(String name, String personaName);

    @Query("UPDATE users SET avatar_hash = :avatarHash WHERE username = :name")
    public abstract void updateAvatarHash(String name, String avatarHash);

    @Query("UPDATE users SET login_key = :loginKey WHERE username = :name")
    public abstract void updateLoginKey(String name, String loginKey);

    @Query("UPDATE users SET sentry_hash = :sentryHash WHERE username = :name")
    public abstract void updateSentryHash(String name, String sentryHash);

    @Query("UPDATE users SET last_session = :lastSession WHERE username = :name")
    public abstract void updateLastSession(String name, ArrayList<Game> lastSession);

    @Query("UPDATE users SET api_key = :apiKey WHERE username = :name")
    public abstract void updateApiKey(String name, String apiKey);

    @Query("SELECT EXISTS(SELECT 1 FROM users WHERE username = :name)")
    public abstract boolean hasUser(String name);
}
