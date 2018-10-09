package com.steevsapps.idledaddy;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Global executor pools for the application
 */
public class AppExecutors {
    private ExecutorService diskIO;

    private ExecutorService networkIO;

    private ScheduledExecutorService scheduler;

    private final Executor mainThread;

    public AppExecutors() {
        diskIO = Executors.newSingleThreadExecutor();
        networkIO = Executors.newCachedThreadPool();
        scheduler = Executors.newScheduledThreadPool(8);
        mainThread = new MainThreadExecutor();
    }

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

    public void shutdownNow() {
        networkIO.shutdownNow();
        scheduler.shutdownNow();
        // Recreate for when the app is relaunched
        networkIO = Executors.newCachedThreadPool();
        scheduler = Executors.newScheduledThreadPool(8);
    }

    private static class MainThreadExecutor implements Executor {
        private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

        @Override
        public void execute(@NonNull Runnable command) {
            mainThreadHandler.post(command);
        }
    }
}
