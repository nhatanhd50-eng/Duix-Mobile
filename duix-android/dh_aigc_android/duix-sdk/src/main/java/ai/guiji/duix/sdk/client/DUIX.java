package ai.guiji.duix.sdk.client;

import android.content.Context;
import android.util.Log;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

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
    private ExecutorService commonExecutor = Executors.newSingleThreadExecutor();
    private RenderThread mRenderThread;

    private boolean isReady;
    private float mVolume = 1.0F;
    private RenderThread.Reporter reporter;

    // --- BIẾN THÊM MỚI CHO TTS ---
    private TextToSpeech mTts;

    public DUIX(Context context, String modelName, RenderSink sink, Callback callback) {
        this.mContext = context;
        this.mCallback = callback;
        this.modelName = modelName;
        this.renderSink = sink;
    }

    /**
     * 模型读取
     */
    public void init() {
        // 先检查模型文件
        File duixDir = mContext.getExternalFilesDir("duix");

        File baseConfigDir = new File(duixDir + "/model/gj_dh_res");
        File baseConfigTag = new File(duixDir + "/model/tmp/gj_dh_res");
        if (!baseConfigDir.exists() || !baseConfigTag.exists()) {
            if (mCallback != null) {
                mCallback.onEvent(Constant.CALLBACK_EVENT_INIT_ERROR, "[gj_dh_res] does not exist", null);
            }
            return;
        }

        String dirName = "";
        if (modelName.startsWith("https://") || modelName.startsWith("http://")) {
            try {
                dirName = modelName.substring(modelName.lastIndexOf("/") + 1).replace(".zip", "");
            } catch (Exception ignore) {
            }
        } else {
            dirName = modelName;
        }
        File modelDir = new File(duixDir + "/model", dirName);
        File modelTag = new File(duixDir + "/model/tmp", dirName);
        if (!modelDir.exists() || !modelTag.exists()) {
            if (mCallback != null) {
                mCallback.onEvent(Constant.CALLBACK_EVENT_INIT_ERROR, "[" + dirName + "] does not exist", null);
            }
            return;
        }

        if (mRenderThread != null) {
            mRenderThread.stopPreview();
            mRenderThread = null;
        }
        mRenderThread = new RenderThread(mContext, modelDir, renderSink, mVolume, new RenderThread.RenderCallback() {
            @Override
            public void onInitResult(int code, int subCode, String message, ModelInfo modelInfo) {
                if (code == 0) {
                    isReady = true;
                    if (mCallback != null) {
                        mCallback.onEvent(Constant.CALLBACK_EVENT_INIT_READY, "init ok", modelInfo);
                    }
                } else {
                    if (mCallback != null) {
                        mCallback.onEvent(Constant.CALLBACK_EVENT_INIT_ERROR, code + ", " + subCode + ", " + message, null);
                    }
                }
            }

            @Override
            public void onPlayStart() {
                if (mCallback != null) {
                    mCallback.onEvent(Constant.CALLBACK_EVENT_AUDIO_PLAY_START, "play start", null);
                }
            }

            @Override
            public void onPlayEnd() {
                if (mCallback != null) {
                    mCallback.onEvent(Constant.CALLBACK_EVENT_AUDIO_PLAY_END, "play end", null);
                }
            }

            @Override
            public void onPlayError(int code, String msg) {
                if (mCallback != null) {
                    mCallback.onEvent(Constant.CALLBACK_EVENT_AUDIO_PLAY_ERROR, "audio play error code: " + code + " msg: " + msg, null);
                }
            }

            @Override
            public void onMotionPlayStart(String name) {
                if (mCallback != null) {
                    mCallback.onEvent(Constant.CALLBACK_EVENT_MOTION_START, "", null);
                }
            }

            @Override
            public void onMotionPlayComplete(String name) {
                if (mCallback != null) {
                    mCallback.onEvent(Constant.CALLBACK_EVENT_MOTION_END, "", null);
                }
            }
        }, reporter);
        mRenderThread.setName("DUIXRender-Thread");
        mRenderThread.start();
    }

    public boolean isReady() {
        return isReady;
    }

    public void setVolume(float volume) {
        if (volume >= 0.0F && volume <= 1.0F) {
            mVolume = volume;
            if (mRenderThread != null) {
                mRenderThread.setVolume(volume);
            }
        }
    }

    public void startPush() {
        if (mRenderThread != null) {
            mRenderThread.startPush();
        }
    }

    public void pushPcm(byte[] buffer) {
        if (mRenderThread != null) {
            mRenderThread.pushAudio(buffer.clone());
        }
    }

    public void stopPush() {
        if (mRenderThread != null) {
            mRenderThread.stopPush();
        }
    }

    /**
     * 播放音频文件
     * 这里演示了兼容旧的wav音频文件驱动
     *
     * @param wavPath 16k采样率单通道16位深的wav本地文件
     */
    public void playAudio(String wavPath) {
        File wavFile = new File(wavPath);
        if (isReady && mRenderThread != null && wavFile.exists() && wavFile.length() > 44) {
            // 默认wav的头是44bytes，并且采样率是16000、单通道、16bit深度
            byte[] data = new byte[(int) wavFile.length()];
            try (FileInputStream inputStream = new FileInputStream(wavFile)) {
                inputStream.read(data);
            } catch (Exception e) {
                Log.e(TAG, "Error reading wav file", e);
                return;
            }
            byte[] slice = java.util.Arrays.copyOfRange(data, 44, data.length);
            startPush();
            pushPcm(slice);
            stopPush();
        }
    }

    /**
     * 停止音频播放
     */
    public boolean stopAudio() {
        if (isReady && mRenderThread != null) {
            mRenderThread.stopPlayAudio();
            return true;
        } else {
            return false;
        }
    }

    /**
     * 播放一只指定动作区间
     */
    public void startMotion(String name, boolean now) {
        if (mRenderThread != null) {
            mRenderThread.requireMotion(name, now);
        }
    }

    /**
     * 随机播放一个动作区间
     */
    public void startRandomMotion(boolean now) {
        if (mRenderThread != null) {
            mRenderThread.requireRandomMotion(now);
        }
    }

    public void release() {
        isReady = false;
        if (commonExecutor != null) {
            commonExecutor.shutdown();
            commonExecutor = null;
        }
        if (mTts != null) {
            mTts.shutdown();
            mTts = null;
        }
        if (mRenderThread != null) {
            mRenderThread.stopPreview();
        }
    }

    public void setReporter(RenderThread.Reporter reporter) {
        this.reporter = reporter;
        if (mRenderThread != null) {
            mRenderThread.setReporter(reporter);
        }
    }

    // ==========================================
    // === INTEGRATED CEREBRAS & TTS (CODE MỚI) ===
    // ==========================================

    /**
     * HÀM CHÍNH: Gọi AI -> Đọc tiếng Việt -> Nhân vật nói
     */
    public void askAndSpeak(final String text) {
        if (!isReady()) {
            Log.w(TAG, "DUIX chưa sẵn sàng, bỏ qua yêu cầu.");
            return;
        }

        Log.i(TAG, "Gửi yêu cầu đến AI: " + text);

        // 1. Khởi tạo TTS (Tiếng Việt)
        initTts();

        // 2. Gọi API AI (Cerebras hoặc model khác)
        askCerebras(text, new CerebrasCallback() {
            @Override
            public void onSuccess(final String content) {
                Log.i(TAG, "AI Trả lời: " + content);

                // 3. Phát âm thanh qua TTS (trực tiếp không cần tạo file tạm)
                speakWithTts(content);
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Lỗi gọi AI: " + error);
            }
        });
    }

    // --- HÀM PHÁT QUA TTS (TRỰC TIẾP) ---
    private void speakWithTts(String content) {
        if (mTts == null) {
            Log.e(TAG, "TTS chưa được khởi tạo");
            return;
        }

        mTts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                Log.d(TAG, "TTS bắt đầu phát: " + utteranceId);
            }

            @Override
            public void onDone(String utteranceId) {
                Log.d(TAG, "TTS hoàn tất: " + utteranceId);
            }

            @Override
            public void onError(String utteranceId) {
                Log.e(TAG, "TTS lỗi: " + utteranceId);
            }
        });

        // Phát trực tiếp không cần file tạm
        int status = mTts.speak(content, TextToSpeech.QUEUE_FLUSH, null, "DUIX_TTS_UTTERANCE");
        if (status == TextToSpeech.ERROR) {
            Log.e(TAG, "Lỗi TTS khi phát âm");
        }
    }

    // --- HÀM HỖ TRỢ CEREBRAS API ---
    private void askCerebras(final String text, final CerebrasCallback callback) {
        commonExecutor.execute(() -> {
            try {
                // ✅ ĐÃ SỬA: URL không còn dấu cách thừa
                URL url = new URL("https://api.cerebras.ai/v1/chat/completions");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer csk-tmj9882rd95p388jedk9632nk4f99hdmkcjc5rrp3nptp8fe");
                conn.setDoOutput(true);

                // Model: zai-glm-4.7
                String jsonInputString = "{\"model\": \"zai-glm-4.7\", \"messages\": [{\"role\": \"user\", \"content\": \"" + text + "\"}], \"stream\": false}";

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonInputString.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    br.close();

                    String content = parseJsonContent(response.toString());
                    if (content != null) {
                        callback.onSuccess(content);
                    } else {
                        callback.onError("Không thể phân tích phản hồi từ API");
                    }
                } else {
                    callback.onError("API Error: " + responseCode + " - " + conn.getResponseMessage());
                }
                conn.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
                callback.onError("Network error: " + e.getMessage());
            }
        });
    }

    // --- HÀM HỖ TRỢ TTS (TIẾNG VIỆT) ---
    private void initTts() {
        if (mTts == null) {
            mTts = new TextToSpeech(mContext, new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    if (status == TextToSpeech.SUCCESS) {
                        int result = mTts.setLanguage(new Locale("vi", "VN"));
                        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            Log.w(TAG, "Không hỗ trợ tiếng Việt, dùng tiếng Anh");
                            mTts.setLanguage(Locale.US);
                        } else {
                            Log.i(TAG, "Đã cài đặt Tiếng Việt thành công!");
                        }
                    } else {
                        Log.e(TAG, "Khởi tạo TTS thất bại");
                    }
                }
            });
        }
    }

    // --- HÀM HỖ TRỢ JSON PARSER (DÙNG JSONOBJECT) ---
    private String parseJsonContent(String jsonResponse) {
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            // Truy cập: choices -> [0] -> message -> content
            return jsonObject.getJSONArray("choices")
                           .getJSONObject(0)
                           .getJSONObject("message")
                           .getString("content");
        } catch (Exception e) {
            Log.e(TAG, "Lỗi phân tích JSON: " + e.getMessage());
            return null;
        }
    }

    // --- INTERFACE TRỢ GIÚP ---
    interface CerebrasCallback {
        void onSuccess(String content);
        void onError(String error);
    }
}
