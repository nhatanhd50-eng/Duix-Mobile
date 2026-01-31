package ai.guiji.duix.test.ui.activity;

import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

import ai.guiji.duix.sdk.client.DUIX;

public abstract class BaseActivity extends AppCompatActivity implements Handler.Callback {

    public final String TAG = getClass().getName();
    protected BaseActivity mContext;
    protected Handler mHandler;

    protected EditText inputText;
    protected Button btnSend;
    protected LinearLayout chatContainer;
    protected ScrollView scrollView;
    protected DUIX duix; 

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;
        HandlerThread mHandlerThread = new HandlerThread(TAG);
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper(), this);
        keepScreenOn();
    }

    protected void setupChatUI() {
        LinearLayout overlayLayout = new LinearLayout(this);
        overlayLayout.setOrientation(LinearLayout.VERTICAL);
        overlayLayout.setBackgroundColor(Color.TRANSPARENT);
        overlayLayout.setGravity(Gravity.BOTTOM);

        scrollView = new ScrollView(this);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f 
        );
        scrollParams.setMargins(20, 100, 20, 20);
        scrollView.setLayoutParams(scrollParams);
        chatContainer = new LinearLayout(this);
        chatContainer.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(chatContainer);

        LinearLayout inputLayout = new LinearLayout(this);
        inputLayout.setOrientation(LinearLayout.HORIZONTAL);
        inputLayout.setPadding(20, 20, 20, 20);
        inputLayout.setBackgroundColor(Color.parseColor("#80000000"));

        inputText = new EditText(this);
        inputText.setHint("Hỏi gì đó...");
        inputText.setTextColor(Color.WHITE);
        inputText.setHintTextColor(Color.LTGRAY);
        inputText.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        inputLayout.addView(inputText, textParams);

        btnSend = new Button(this);
        btnSend.setText("Gửi");
        btnSend.setBackgroundColor(Color.BLUE);
        btnSend.setTextColor(Color.WHITE);
        inputLayout.addView(btnSend);

        overlayLayout.addView(scrollView);
        overlayLayout.addView(inputLayout);
        addContentView(overlayLayout, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        btnSend.setOnClickListener(v -> {
            String text = inputText.getText().toString().trim();
            if (!text.isEmpty()) {
                addChatMessage("Bạn: " + text, true);
                inputText.setText("");
                if (duix != null) duix.askAndSpeak(text);
            }
        });
    }

    protected void addChatMessage(String msg, boolean isUser) {
        runOnUiThread(() -> {
            TextView tv = new TextView(this);
            tv.setText(msg);
            tv.setTextColor(Color.WHITE);
            tv.setPadding(20, 10, 20, 10);
            tv.setTextSize(16f);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 5, 0, 5);
            if (isUser) {
                tv.setBackgroundColor(Color.parseColor("#007AFF"));
                params.gravity = Gravity.END;
            } else {
                tv.setBackgroundColor(Color.parseColor("#333333"));
                params.gravity = Gravity.START;
            }
            tv.setLayoutParams(params);
            chatContainer.addView(tv);
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mHandler != null) mHandler.getLooper().quitSafely();
    }
    @Override public boolean handleMessage(@NonNull Message msg) { onMessage(msg); return false; }
    protected void onMessage(@NonNull Message msg) {}
    protected void keepScreenOn() { getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); }

    private String[] mRequestPermissions;
    private int mRequestPermissionCode;
    ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            boolean hasDeny = false;
            for (String permission : mRequestPermissions) {
                if (permission != null && ContextCompat.checkSelfPermission(mContext, permission) != PackageManager.PERMISSION_GRANTED) hasDeny = true;
            }
            permissionsGet(!hasDeny, mRequestPermissionCode);
        });
    public void requestPermission(String[] permissions, int code) {
        if (permissions == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) { permissionsGet(true, code); return; }
        mRequestPermissions = permissions; mRequestPermissionCode = code;
        List<String> list = new ArrayList<>();
        for (String p : permissions) { if (ContextCompat.checkSelfPermission(mContext, p) != PackageManager.PERMISSION_GRANTED) list.add(p); }
        if (!list.isEmpty()) permissionLauncher.launch(list.toArray(new String[0])); else permissionsGet(true, mRequestPermissionCode);
    }
    public void permissionsGet(boolean get, int code) {}
}
