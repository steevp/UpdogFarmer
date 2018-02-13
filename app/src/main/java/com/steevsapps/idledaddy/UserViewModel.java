package com.steevsapps.idledaddy;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MediatorLiveData;
import android.arch.lifecycle.Observer;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.steevsapps.idledaddy.db.entity.User;
import com.steevsapps.idledaddy.preferences.PrefsManager;
import com.steevsapps.idledaddy.steam.wrapper.Game;

import java.util.ArrayList;
import java.util.List;

public class UserViewModel extends AndroidViewModel {
    private final UserRepository userRepo;
    private final MediatorLiveData<User> currentUser = new MediatorLiveData<>();

    public UserViewModel(@NonNull Application application) {
        super(application);
        userRepo = ((IdleDaddy) application).getRepository();
        // Listen for database changes and update the current user
        currentUser.addSource(userRepo.getAll(), new Observer<List<User>>() {
            @Override
            public void onChanged(@Nullable List<User> users) {
                if (users != null) {
                    for (User user : users) {
                        if (PrefsManager.getCurrentUser().equals(user.getUsername())) {
                            currentUser.setValue(user);
                            break;
                        }
                    }
                }
            }
        });
    }

    public LiveData<User> getUser() {
        return currentUser;
    }

    public void updateLastSession(ArrayList<Game> lastSession) {
        userRepo.updateLastSession(lastSession);
    }
}
