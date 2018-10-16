package com.steevsapps.idledaddy;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Global executor pools for the application
 */
public class AppExecutors {
    private ExecutorService diskIO = Executors.newSingleThreadExecutor();

    private Executor mainThread = new MainThreadExecutor();

    private ExecutorService networkIO = Executors.newCachedThreadPool();

    public ExecutorService diskIO() {
        return diskIO;
    }

    public ExecutorService networkIO() {
        return networkIO;
    }

    public Executor mainThread() {
        return mainThread;
    }

    public void shutdownNow() {
        networkIO.shutdownNow();
        // Recreate for when the app is relaunched
        networkIO = Executors.newCachedThreadPool();
    }

    private static class MainThreadExecutor implements Executor {
        private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

        @Override
        public void execute(@NonNull Runnable command) {
            mainThreadHandler.post(command);
        }
    }
}
