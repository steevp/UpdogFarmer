package com.steevsapps.idledaddy.db.dao;

import android.arch.lifecycle.LiveData;
import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;

import com.steevsapps.idledaddy.db.entity.User;

import java.util.List;

@Dao
public interface UserDao {
    @Query("SELECT * FROM user ORDER BY username ASC")
    LiveData<List<User>> getAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertUser(User user);

    @Update
    void updateUser(User user);

    @Delete
    void deleteUser(User user);

    @Query("SELECT user.* FROM user " +
            "INNER JOIN user_settings " +
            "ON user.username = user_settings.current_user " +
            "LIMIT 1")
    LiveData<User> getCurrentUser();

    @Query("SELECT user.* FROM user " +
            "INNER JOIN user_settings " +
            "ON user.username = user_settings.current_user " +
            "LIMIT 1")
    User getCurrentUserSync();
}
