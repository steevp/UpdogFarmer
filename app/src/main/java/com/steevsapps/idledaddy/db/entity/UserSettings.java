package com.steevsapps.idledaddy.db.entity;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.ForeignKey;
import android.arch.persistence.room.Index;
import android.arch.persistence.room.PrimaryKey;
import android.support.annotation.Nullable;

@Entity(tableName = "user_settings", indices = {@Index("current_user")},
        foreignKeys = {@ForeignKey(entity = User.class,
                parentColumns = {"username"},
                childColumns = {"current_user"},
                onDelete = ForeignKey.SET_NULL)})
public class UserSettings {
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private int id;

    @ColumnInfo(name = "current_user")
    private String currentUser;

    public UserSettings(@Nullable String currentUser) {
        this.currentUser = currentUser;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    @Nullable
    public String getCurrentUser() {
        return currentUser;
    }
}
