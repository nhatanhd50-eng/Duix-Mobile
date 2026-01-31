package ai.guiji.duix.test;

import android.app.Application;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;

public class App extends Application {

    public static App mApp;
    private static OkHttpClient mOkHttpClient;

    @Override
    public void onCreate() {
        super.onCreate();
        mApp = this;
    }

    public static OkHttpClient getOkHttpClient() {
        if (mOkHttpClient == null) {
            mOkHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS) // Tăng lên 30s
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build();
        }
        return mOkHttpClient;
    }
}
