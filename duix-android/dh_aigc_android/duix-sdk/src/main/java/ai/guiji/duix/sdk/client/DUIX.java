package ai.guiji.duix.sdk.client;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ai.guiji.duix.sdk.client.loader.ModelInfo;
import ai.guiji.duix.sdk.client.render.RenderSink;
import ai.guiji.duix.sdk.client.thread.RenderThread;

public class DUIX {

    private static final String TAG = "DUIX";

    private final Context mContext;
    private final Callback mCallback;
    private final String modelName;
    private final RenderSink renderSink;
    
    // Executor đơn luồng để xử lý network và file I/O tránh lag UI
    private ExecutorService commonExecutor = Executors.newSingleThreadExecutor();
    private RenderThread mRenderThread;

    private boolean isReady;
    private float mVolume = 1.0F;
    private RenderThread.Reporter reporter;

    // --- BIẾN CHO TTS SYSTEM (CÓ SẴN TRONG ANDROID) ---
    private TextToSpeech mTts;
    private final String TTS_UTTERANCE_ID = "DUIX_TTS_ID";

    public DUIX(Context context, String modelName, RenderSink sink, Callback callback) {
        this.mContext = context;
        this.mCallback = callback;
        this.modelName = modelName;
        this.renderSink = sink;
    }

    /**
     * Khởi tạo Model 3D Avatar
     */
    public void init() {
        File duixDir = mContext.getExternalFilesDir("duix");
        File baseConfigDir = new File(duixDir + "/model/gj_dh_res");
        File baseConfigTag = new File(duixDir + "/model/tmp/gj_dh_res");
        
        if (!baseConfigDir.exists() || !baseConfigTag.exists()) {
            if (mCallback != null) mCallback.onEvent(Constant.CALLBACK_EVENT_INIT_ERROR, "Resource missing", null);
            return;
        }

        String dirName = modelName;
        if (modelName.startsWith("http")) {
             try { dirName = modelName.substring(modelName.lastIndexOf("/") + 1).replace(".zip", ""); } catch (Exception e) {}
        }
        
        File modelDir = new File(duixDir + "/model", dirName);
        
        if (mRenderThread != null) {
            mRenderThread.stopPreview();
            mRenderThread = null;
        }
        
        mRenderThread = new RenderThread(mContext, modelDir, renderSink, mVolume, new RenderThread.RenderCallback() {
            @Override
            public void onInitResult(int code, int subCode, String message, ModelInfo modelInfo) {
                if (code == 0) {
                    isReady = true;
                    if (mCallback != null) mCallback.onEvent(Constant.CALLBACK_EVENT_INIT_READY, "init ok", modelInfo);
                    // Khởi tạo sẵn TTS khi Avatar đã load xong
                    initTts(); 
                } else {
                    if (mCallback != null) mCallback.onEvent(Constant.CALLBACK_EVENT_INIT_ERROR, message, null);
                }
            }
            @Override public void onPlayStart() { if (mCallback != null) mCallback.onEvent(Constant.CALLBACK_EVENT_AUDIO_PLAY_START, "play start", null); }
            @Override public void onPlayEnd() { if (mCallback != null) mCallback.onEvent(Constant.CALLBACK_EVENT_AUDIO_PLAY_END, "play end", null); }
            @Override public void onPlayError(int code, String msg) { if (mCallback != null) mCallback.onEvent(Constant.CALLBACK_EVENT_AUDIO_PLAY_ERROR, msg, null); }
            @Override public void onMotionPlayStart(String name) {}
            @Override public void onMotionPlayComplete(String name) {}
        }, reporter);
        
        mRenderThread.setName("DUIXRender-Thread");
        mRenderThread.start();
    }

    public boolean isReady() { return isReady; }
    public void setVolume(float volume) { 
        mVolume = volume; 
        if (mRenderThread != null) mRenderThread.setVolume(volume); 
    }

    // --- CÁC HÀM ĐIỀU KHIỂN RENDER ---
    public void startPush() { if (mRenderThread != null) mRenderThread.startPush(); }
    public void stopPush() { if (mRenderThread != null) mRenderThread.stopPush(); }
    public void pushPcm(byte[] buffer) { if (mRenderThread != null) mRenderThread.pushAudio(buffer.clone()); }

    public void release() {
        isReady = false;
        if (commonExecutor != null) {
            commonExecutor.shutdown();
            commonExecutor = null;
        }
        if (mTts != null) {
            mTts.stop();
            mTts.shutdown();
            mTts = null;
        }
        if (mRenderThread != null) {
            mRenderThread.stopPreview();
        }
    }

    public void setReporter(RenderThread.Reporter reporter) {
        this.reporter = reporter;
        if (mRenderThread != null) mRenderThread.setReporter(reporter);
    }

    // ==========================================
    // === PHẦN LOGIC MỚI: AI & SYSTEM TTS    ===
    // ==========================================

    /**
     * Gọi API Cerebras -> Nhận text -> Chuyển thành file âm thanh -> Đẩy vào Avatar để nhép miệng
     */
    public void askAndSpeak(final String text) {
        if (!isReady()) {
            Log.w(TAG, "Avatar chưa sẵn sàng!");
            return;
        }

        // Gọi AI trong thread riêng
        commonExecutor.execute(() -> {
            Log.i(TAG, "Đang hỏi AI: " + text);
            String aiResponse = callCerebrasApi(text);
            
            if (aiResponse != null && !aiResponse.isEmpty()) {
                Log.i(TAG, "AI trả lời: " + aiResponse);
                // Sau khi có text, gọi hàm tạo âm thanh + lip-sync
                speakWithLipSync(aiResponse);
            } else {
                Log.e(TAG, "Không nhận được phản hồi từ AI");
            }
        });
    }

    /**
     * Hàm quan trọng: Dùng TTS tạo file WAV, sau đó đọc file WAV đẩy vào Avatar
     */
    private void speakWithLipSync(String text) {
        if (mTts == null) {
            Log.e(TAG, "TTS chưa khởi tạo");
            return;
        }

        // Tạo file tạm để lưu âm thanh
        File tempFile = new File(mContext.getCacheDir(), "tts_temp.wav");
        if (tempFile.exists()) tempFile.delete();

        // Yêu cầu TTS ghi âm thanh ra file (thay vì phát loa ngay)
        // Bundle params = new Bundle(); // Cho Android mới nếu cần
        int result = mTts.synthesizeToFile(text, null, tempFile, TTS_UTTERANCE_ID);
        
        if (result == TextToSpeech.SUCCESS) {
            Log.i(TAG, "Đang tạo file âm thanh từ text...");
            // Việc đọc file sẽ được xử lý trong callback onDone của UtteranceProgressListener
        } else {
            Log.e(TAG, "Lỗi tạo file âm thanh TTS");
        }
    }

    /**
     * Gọi API Cerebras (HTTP thuần, siêu nhẹ)
     */
    private String callCerebrasApi(String prompt) {
        try {
            URL url = new URL("https://api.cerebras.ai/v1/chat/completions");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer csk-tmj9882rd95p388jedk9632nk4f99hdmkcjc5rrp3nptp8fe"); // Key của bạn
            conn.setDoOutput(true);

            // JSON Body
            String jsonInputString = "{\"model\": \"zai-glm-4.7\", \"messages\": [{\"role\": \"user\", \"content\": \"" + prompt + "\"}], \"stream\": false}";

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int code = conn.getResponseCode();
            if (code == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) response.append(line.trim());
                
                // Parse JSON thủ công để lấy nội dung trả lời
                JSONObject json = new JSONObject(response.toString());
                return json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
            } else {
                Log.e(TAG, "API Error Code: " + code);
            }
        } catch (Exception e) {
            Log.e(TAG, "Network Error: " + e.getMessage());
        }
        return null;
    }

    private void initTts() {
        if (mTts == null) {
            mTts = new TextToSpeech(mContext, status -> {
                if (status == TextToSpeech.SUCCESS) {
                    // Cấu hình ngôn ngữ Tiếng Việt
                    int result = mTts.setLanguage(new Locale("vi", "VN"));
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.w(TAG, "Tiếng Việt không khả dụng, chuyển sang US");
                        mTts.setLanguage(Locale.US);
                    }
                    
                    // Cài đặt Listener để biết khi nào tạo file âm thanh xong
                    mTts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override
                        public void onStart(String utteranceId) { Log.d(TAG, "TTS Start: " + utteranceId); }

                        @Override
                        public void onDone(String utteranceId) {
                            if (TTS_UTTERANCE_ID.equals(utteranceId)) {
                                Log.i(TAG, "TTS tạo file xong, bắt đầu đẩy vào Avatar...");
                                playGeneratedAudio();
                            }
                        }

                        @Override
                        public void onError(String utteranceId) { Log.e(TAG, "TTS Error: " + utteranceId); }
                    });
                }
            });
        }
    }

    /**
     * Đọc file âm thanh đã tạo và đẩy vào engine Avatar
     */
    private void playGeneratedAudio() {
        File tempFile = new File(mContext.getCacheDir(), "tts_temp.wav");
        if (tempFile.exists() && tempFile.length() > 44) { // Header WAV thường là 44 byte
            byte[] data = new byte[(int) tempFile.length()];
            try (FileInputStream fis = new FileInputStream(tempFile)) {
                fis.read(data);
                
                // Bỏ 44 byte đầu (header) để lấy dữ liệu PCM thô
                byte[] pcmData = java.util.Arrays.copyOfRange(data, 44, data.length);
                
                // Đẩy vào Avatar
                startPush();
                pushPcm(pcmData);
                stopPush();
                
            } catch (Exception e) {
                Log.e(TAG, "Lỗi đọc file audio: " + e.getMessage());
            }
        }
    }
}
