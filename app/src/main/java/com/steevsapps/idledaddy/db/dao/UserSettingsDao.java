package com.steevsapps.idledaddy.db.dao;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;

import com.steevsapps.idledaddy.db.entity.UserSettings;

@Dao
public interface UserSettingsDao {
    @Insert
    void insert(UserSettings settings);

    @Query("UPDATE user_settings SET current_user = :name")
    void setCurrentUser(String name);
}
