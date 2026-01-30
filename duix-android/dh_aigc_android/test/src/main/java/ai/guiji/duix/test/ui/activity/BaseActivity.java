package ai.guiji.duix.test.ui.activity;

import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Color;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ai.guiji.duix.sdk.client.DUIX;
import ai.guiji.duix.sdk.client.render.RenderSink;

public abstract class BaseActivity extends AppCompatActivity implements Handler.Callback {

    public final String TAG = getClass().getName();
    protected BaseActivity mContext;
    protected Handler mHandler;

    // --- BIẾN GIAO DIỆN CHAT ---
    protected EditText inputText;
    protected Button btnSend;
    protected LinearLayout chatContainer; // Danh sách tin nhắn
    protected ScrollView scrollView; // ScrollView để cuộn
    protected DUIX duix; // Biến DUIX dùng chung

    // --- TTS ENGINE ---
    protected TextToSpeech ttsEngine;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;
        HandlerThread mHandlerThread = new HandlerThread(TAG);
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper(), this);

        // Khởi tạo TTS Engine
        ttsEngine = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = ttsEngine.setLanguage(Locale.US);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "TTS Language not supported");
                } else {
                    Log.d(TAG, "TTS Initialized successfully");
                }
            } else {
                Log.e(TAG, "TTS Initialization failed");
            }
        });
    }

    /**
     * GỌI HÀM NÀY Ở CÁC LỚP CON (MainActivity, CallActivity)
     * NÓ SẼ TỰ ĐỘNG VẼ GIAO DIỆN CHAT
     */
    protected void setupChatUI() {
        // 1. Layout chính (Dọc)
        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(Color.parseColor("#121212")); // Nền tối

        // 2. Danh sách tin nhắn (Chat History)
        scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1.0f // Chiếm phần lớn màn hình
        ));
        scrollView.setBackgroundColor(Color.TRANSPARENT);

        chatContainer = new LinearLayout(this);
        chatContainer.setOrientation(LinearLayout.VERTICAL);
        chatContainer.setPadding(20, 20, 20, 20);
        chatContainer.setGravity(Gravity.BOTTOM);

        scrollView.addView(chatContainer);
        rootLayout.addView(scrollView);

        // 3. Khung nhập liệu (Input Box)
        LinearLayout inputLayout = new LinearLayout(this);
        inputLayout.setOrientation(LinearLayout.HORIZONTAL);
        inputLayout.setGravity(Gravity.CENTER_VERTICAL);
        inputLayout.setPadding(20, 15, 20, 15);
        inputLayout.setBackgroundColor(Color.parseColor("#2C2C2C"));

        // Ô nhập Text
        inputText = new EditText(this);
        inputText.setHint("Nhập tin nhắn...");
        inputText.setBackgroundColor(Color.TRANSPARENT);
        inputText.setTextColor(Color.WHITE);
        inputText.setHintTextColor(Color.GRAY);
        inputText.setPadding(15, 15, 15, 15);
        inputText.setTextSize(14f);
        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1.0f
        );
        editParams.setMargins(0, 0, 10, 0);
        inputLayout.addView(inputText, editParams);

        // Nút Gửi
        btnSend = new Button(this);
        btnSend.setText("Gửi");
        btnSend.setBackgroundColor(Color.parseColor("#00BCD4"));
        btnSend.setTextColor(Color.WHITE);
        btnSend.setPadding(20, 10, 20, 10);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        inputLayout.addView(btnSend, btnParams);

        // Thêm input layout vào root
        rootLayout.addView(inputLayout);

        // Hiển thị giao diện
        setContentView(rootLayout);

        // Sự kiện nhấn nút gửi
        btnSend.setOnClickListener(v -> {
            String userText = inputText.getText().toString().trim();
            if (!userText.isEmpty()) {
                addChatMessage(userText, true); // Tin nhắn người dùng
                inputText.setText(""); // Xóa ô input
                processUserInput(userText); // Gửi tới LLM
            }
        });
    }

    /**
     * GỬI INPUT CHO LLM VÀ NHẬN KẾT QUẢ
     * @param userInput Văn bản người dùng nhập
     */
    protected void processUserInput(String userInput) {
        // TODO: Gọi API LLM ở đây (OpenAI, Claude, v.v.)
        // Ví dụ giả lập trả lời sau 1 giây
        mHandler.postDelayed(() -> {
            String aiResponse = "Tôi đã nhận được câu hỏi: \"" + userInput + "\". Đây là phản hồi từ AI.";
            addChatMessage(aiResponse, false); // Tin nhắn AI
            speakText(aiResponse); // Phát âm thanh qua TTS
        }, 1000);
    }

    /**
     * PHÁT ÂM THANH QUA TTS ENGINE
     * @param text Văn bản cần phát
     */
    protected void speakText(String text) {
        if (ttsEngine != null) {
            ttsEngine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "TTS_UTTERANCE_ID");
        }
    }

    /**
     * HÀM THÊM TIN NHẮN VÀO KHUNG CHAT
     * @param text Nội dung tin nhắn
     * @param isMine true = tin nhắn của bạn (bên phải), false = tin nhắn AI (bên trái)
     */
    protected void addChatMessage(String text, boolean isMine) {
        runOnUiThread(() -> { // Đảm bảo chạy trên UI thread
            if (chatContainer == null) return;

            // Layout 1 tin nhắn
            LinearLayout msgWrapper = new LinearLayout(this);
            msgWrapper.setOrientation(LinearLayout.HORIZONTAL);
            msgWrapper.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            msgWrapper.setGravity(isMine ? Gravity.END : Gravity.START); // END = phải, START = trái
            msgWrapper.setPadding(0, 0, 0, 10);

            // Bong bóng chat
            TextView msgBubble = new TextView(this);
            msgBubble.setText(text);
            msgBubble.setTextSize(15f);
            msgBubble.setTextColor(Color.WHITE);
            msgBubble.setPadding(20, 15, 20, 15);

            if (isMine) {
                msgBubble.setBackgroundColor(Color.parseColor("#00BCD4")); // Xanh dương
                msgBubble.setGravity(Gravity.END);
            } else {
                msgBubble.setBackgroundColor(Color.parseColor("#3E3E3E")); // Xám
                msgBubble.setGravity(Gravity.START);
            }

            msgWrapper.addView(msgBubble);
            chatContainer.addView(msgBubble);

            // Tự động cuộn xuống cuối
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy");

        // Giải phóng TTS
        if (ttsEngine != null) {
            ttsEngine.stop();
            ttsEngine.shutdown();
        }

        if (mHandler != null && mHandler.getLooper() != null) {
            mHandler.getLooper().quitSafely();
        }
    }

    @Override
    public boolean handleMessage(@NonNull Message msg) {
        onMessage(msg);
        return false;
    }

    // try abstract
    protected void onMessage(@NonNull Message msg) {}

    protected void keepScreenOn() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    // --- PHẦN XỬ LÝ PERMISSION ---
    private String[] mRequestPermissions;
    private int mRequestPermissionCode;
    ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
        new ActivityResultContracts.RequestMultiplePermissions(),
        result -> {
            boolean hasDeny = false;
            for (String permission : mRequestPermissions) {
                if (null == permission) continue;
                if (ContextCompat.checkSelfPermission(mContext, permission) != PackageManager.PERMISSION_GRANTED) {
                    hasDeny = true;
                }
            }
            if (hasDeny) {
                permissionsGet(false, mRequestPermissionCode);
            } else {
                permissionsGet(true, mRequestPermissionCode);
            }
        });

    public void requestPermission(String[] permissions, int code) {
        if (null == permissions) {
            permissionsGet(true, code);
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            permissionsGet(true, code);
            return;
        }
        mRequestPermissions = permissions;
        mRequestPermissionCode = code;
        List<String> requestPermissions = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(mContext, permission) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions.add(permission);
            }
        }
        if (!requestPermissions.isEmpty()) {
            permissionLauncher.launch(requestPermissions.toArray(new String[0]));
        } else {
            permissionsGet(true, mRequestPermissionCode);
        }
    }

    public void permissionsGet(boolean get, int code) {}
}
