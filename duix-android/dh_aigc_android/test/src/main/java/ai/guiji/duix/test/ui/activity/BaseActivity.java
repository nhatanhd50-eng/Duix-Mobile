package ai.guiji.duix.test.ui.activity;

import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
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

import ai.guiji.duix.sdk.client.DUIX;
import ai.guiji.duix.sdk.client.render.RenderSink;

public abstract class BaseActivity extends AppCompatActivity implements Handler.Callback {

    public final String TAG = getClass().getName();
    protected BaseActivity mContext;
    protected Handler mHandler;

    // --- BIẾN GIAO DIỆN CHAT (Để lớp con sử dụng) ---
    protected EditText inputText;
    protected Button btnSend;
    protected LinearLayout chatContainer; // Danh sách tin nhắn
    protected DUIX duix; // Biến DUIX dùng chung

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;
        HandlerThread mHandlerThread = new HandlerThread(TAG);
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper(), this);
    }

    /**
     * GỌI HÀM NÀY Ở CÁC LỚP CON (MainActivity, CallActivity)
     * NÓ SẼ TỰ ĐỘNG VẼ GIAO DIỆN CHAT
     */
    protected void setupChatUI() {
        // 1. Layout chính (Dọc)
        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setGravity(Gravity.BOTTOM);
        rootLayout.setBackgroundColor(Color.parseColor("#121212")); // Nền tối

        // 2. Danh sách tin nhắn (Chat History)
        // Sử dụng LinearLayout cuộn để hiển thị tin nhắn
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1.0f // Chiếm hết chỗ trống
        ));
        scrollView.setBackgroundColor(Color.TRANSPARENT);
        
        chatContainer = new LinearLayout(this);
        chatContainer.setOrientation(LinearLayout.VERTICAL);
        chatContainer.setPadding(20, 20, 20, 20);
        chatContainer.setGravity(Gravity.BOTTOM); // Chat hiển thị từ dưới lên
        
        scrollView.addView(chatContainer);
        rootLayout.addView(scrollView);

        // 3. Khung nhập liệu (Input Box)
        LinearLayout inputLayout = new LinearLayout(this);
        inputLayout.setOrientation(LinearLayout.HORIZONTAL);
        inputLayout.setGravity(Gravity.CENTER_VERTICAL);
        inputLayout.setPadding(20, 15, 20, 15);
        inputLayout.setBackgroundColor(Color.parseColor("#2C2C2C"));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rootLayout.addView(inputLayout, inputParams);

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

        // Hiển thị giao diện
        setContentView(rootLayout);
    }

    /**
     * HÀM THÊM TIN NHẮN VÀO KHUNG CHAT
     * @param isMine true = tin nhắn của bạn (bên phải), false = tin nhắn AI (bên trái)
     */
    protected void addChatMessage(String text, boolean isMine) {
        if (chatContainer == null) return;

        // Layout 1 tin nhắn
        LinearLayout msgWrapper = new LinearLayout(this);
        msgWrapper.setOrientation(LinearLayout.HORIZONTAL);
        msgWrapper.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        msgWrapper.setGravity(isMine ? Gravity.RIGHT : Gravity.LEFT); // Bạn phải, AI trái
        msgWrapper.setPadding(0, 0, 0, 10);

        // Bóng chat (Nền màu xanh hoặc xám)
        TextView msgBubble = new TextView(this);
        msgBubble.setText(text);
        msgBubble.setTextSize(15f);
        msgBubble.setTextColor(Color.WHITE);
        msgBubble.setPadding(20, 15, 20, 15);
        
        if (isMine) {
            msgBubble.setBackgroundColor(Color.parseColor("#00BCD4")); // Màu xanh dương cho bạn
            int rightMargin = 50; 
            msgWrapper.setPadding(rightMargin, 0, 0, 10); // Canh lề phải
        } else {
            msgBubble.setBackgroundColor(Color.parseColor("#3E3E3E")); // Màu xám cho AI
            int leftMargin = 50; 
            msgWrapper.setPadding(leftMargin, 0, 0, 10); // Canh lề trái
        }
        
        // Góc bo tròn (yêu cầu background drawable, ở đây đơn giản là vuông)
        
        msgWrapper.addView(msgBubble);
        chatContainer.addView(msgWrapper);
        
        // Tự động cuộn xuống dưới cùng khi có tin nhắn mới
        if (chatContainer.getParent() instanceof android.widget.ScrollView) {
            ((android.widget.ScrollView) chatContainer.getParent()).fullScroll(android.widget.ScrollView.FOCUS_DOWN);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy");
        if (mHandler != null && mHandler.getLooper() != null) {
            mHandler.getLooper().quit();
        }
    }

    @Override
    public boolean handleMessage(@NonNull Message msg) {
        onMessage(msg);
        return false;
    }

    // try abstract
    protected void onMessage(@NonNull Message msg) {

    }

    protected void keepScreenOn() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private String[] mRequestPermissions;
    private int mRequestPermissionCode;
    ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
            result -> {
                boolean hasDeny = false;
                for (String permission : mRequestPermissions) {
                    if (null == permission) {
                        continue;
                    }
                    if (ContextCompat.checkSelfPermission(mContext, permission) !=
                            PackageManager.PERMISSION_GRANTED) {
                        hasDeny = true;
                    }
                }
                if (hasDeny) {
                    permissionsGet(false, mRequestPermissionCode);
                } else {
                    permissionsGet(true, mRequestPermissionCode);
                }
            });

    //申请权限
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
            if (ContextCompat.checkSelfPermission(mContext, permission) !=
                    PackageManager.PERMISSION_GRANTED) {
                requestPermissions.add(permission);
            }
        }
        if (0 != requestPermissions.size()) {
            String[] permissionArray = new String[requestPermissions.size()];
            for (int i = 0; i < requestPermissions.size(); i++) {
                permissionArray[i] = requestPermissions.get(i);
            }
            permissionLauncher.launch(permissionArray);
        } else {
            permissionsGet(true, mRequestPermissionCode);
        }
    }

    //申请权限回调
    public void permissionsGet(boolean get, int code) {

    }
}
