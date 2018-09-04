package com.steevsapps.idledaddy;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class AppExecutors {
    private final ExecutorService diskIO = Executors.newSingleThreadExecutor();

    private final ExecutorService networkIO = Executors.newCachedThreadPool();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(8);

    private final Executor mainThread = new MainThreadExecutor();

    public ExecutorService diskIO() {
        return diskIO;
    }

    public ExecutorService networkIO() {
        return networkIO;
    }

    public ScheduledExecutorService scheduler() {
        return scheduler;
    }

    public Executor mainThread() {
        return mainThread;
    }

    private static class MainThreadExecutor implements Executor {
        private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

        @Override
        public void execute(@NonNull Runnable command) {
            mainThreadHandler.post(command);
        }
    }
}
