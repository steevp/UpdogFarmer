package com.steevsapps.idledaddy;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.support.annotation.NonNull;

import com.steevsapps.idledaddy.db.entity.User;

public class UserViewModel extends AndroidViewModel {
    private UserRepository userRepo;

    public UserViewModel(@NonNull Application application) {
        super(application);
        userRepo = ((IdleDaddy) application).getUserRepository();
    }

    void selectUser(String name) {
        userRepo.setCurrentUser(name);
    }

    LiveData<User> getCurrentUser() {
        return userRepo.getCurrentUser();
    }
}
