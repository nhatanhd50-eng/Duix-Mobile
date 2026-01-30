package ai.guiji.duix.sdk.client;

import android.content.Context;
import android.util.Log;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ai.guiji.duix.sdk.client.loader.ModelInfo;
import ai.guiji.duix.sdk.client.render.RenderSink;
import ai.guiji.duix.sdk.client.thread.RenderThread;

public class DUIX {

    private final Context mContext;
    private final Callback mCallback;
    private final String modelName;
    private final RenderSink renderSink;
    private ExecutorService commonExecutor = Executors.newSingleThreadExecutor();
    private RenderThread mRenderThread;

    private boolean isReady;            // 准备完成的标记
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
        if (!baseConfigDir.exists() || !baseConfigTag.exists()){
            if (mCallback != null){
                mCallback.onEvent(Constant.CALLBACK_EVENT_INIT_ERROR, "[gj_dh_res] does not exist", null);
            }
            return;
        }

        String dirName = "";
        if (modelName.startsWith("https://") || modelName.startsWith("http://")){
            try {
                dirName = modelName.substring(modelName.lastIndexOf("/") + 1).replace(".zip", "");
            }catch (Exception ignore){
            }
        } else {
            dirName = modelName;
        }
        File modelDir = new File(duixDir + "/model", dirName);
        File modelTag = new File(duixDir + "/model/tmp", dirName);
        if (!modelDir.exists() || !modelTag.exists()){
            if (mCallback != null){
                mCallback.onEvent(Constant.CALLBACK_EVENT_INIT_ERROR,  "[" + dirName + "] does not exist", null);
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
                if (code == 0){
                    isReady = true;
                    if (mCallback != null){
                        mCallback.onEvent(Constant.CALLBACK_EVENT_INIT_READY, "init ok", modelInfo);
                    }
                } else {
                    if (mCallback != null){
                        mCallback.onEvent(Constant.CALLBACK_EVENT_INIT_ERROR, code + ", " + subCode + ", " + message, null);
                    }
                }
            }

            @Override
            public void onPlayStart() {
                if (mCallback != null){
                    mCallback.onEvent(Constant.CALLBACK_EVENT_AUDIO_PLAY_START, "play start", null);
                }
            }

            @Override
            public void onPlayEnd() {
                if (mCallback != null){
                    mCallback.onEvent(Constant.CALLBACK_EVENT_AUDIO_PLAY_END, "play end", null);
                }
            }

            @Override
            public void onPlayError(int code, String msg) {
                if (mCallback != null){
                    mCallback.onEvent(Constant.CALLBACK_EVENT_AUDIO_PLAY_ERROR, "audio play error code: " + code + " msg: " + msg, null);
                }
            }

            @Override
            public void onMotionPlayStart(String name) {
                if (mCallback != null){
                    mCallback.onEvent(Constant.CALLBACK_EVENT_MOTION_START, "", null);
                }
            }

            @Override
            public void onMotionPlayComplete(String name) {
                if (mCallback != null){
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

    public void setVolume(float volume){
        if (volume >= 0.0F && volume <= 1.0F){
            mVolume = volume;
            if (mRenderThread != null){
                mRenderThread.setVolume(volume);
            }
        }
    }

    public void startPush(){
        if (mRenderThread != null){
            mRenderThread.startPush();
        }
    }

    public void pushPcm(byte[] buffer){
        if (mRenderThread != null){
            mRenderThread.pushAudio(buffer.clone());
        }
    }

    public void stopPush(){
        if (mRenderThread != null){
            mRenderThread.stopPush();
        }
    }


    /**
     * 播放音频文件
     * 这里演示了兼容旧的wav音频文件驱动
     * @param wavPath 16k采样率单通道16位深的wav本地文件
     */
    public void playAudio(String wavPath) {
        File wavFile = new File(wavPath);
        if (isReady && mRenderThread != null && wavFile.exists() && wavFile.length() > 44) {
//            mRenderThread.prepareAudio(wavPath);
            // 这里默认wav的头是44bytes，并且采样率是16000、单通道、16bit深度
            byte[] data = new byte[(int) wavFile.length()];
            try (FileInputStream inputStream = new FileInputStream(wavFile)) {
                inputStream.read(data);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            byte[] slice = Arrays.copyOfRange(data, 44, data.length);
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
        if (mRenderThread != null) {
            mRenderThread.stopPreview();
        }
    }

    public void setReporter(RenderThread.Reporter reporter){
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
        // 1. Khởi tạo TTS (Tiếng Việt)
        initTts();

        // 2. Gọi API Cerebras
        askCerebras(text, new CerebrasCallback() {
            @Override
            public void onSuccess(final String content) {
                Log.i("DuixCerebras", "AI Trả lời: " + content);
                
                // Tạo file âm thanh tạm
                final String tempFileName = mContext.getExternalFilesDir(null) + "/duix_temp_audio_" + System.currentTimeMillis() + ".wav";

                // 3. Chuyển Text thành File Wav (Synthesize)
                // Dùng UtteranceProgressListener để biết khi nào xong
                mTts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) {}

                    @Override
                    public void onDone(String utteranceId) {
                        // 4. Khi xong file wav -> Gửi cho Duix đọc
                        Log.i("DuixTTS", "Đã tạo xong file âm thanh: " + tempFileName);
                        playAudio(tempFileName);
                        
                        // Gợi ý: Xóa file tạm sau khi phát xong
                        // new File(tempFileName).delete();
                    }

                    @Override
                    public void onError(String utteranceId) {
                        Log.e("DuixTTS", "Lỗi khi tạo file âm thanh");
                    }
                });

                // Thực hiện chuyển văn bản thành file
                int status = mTts.synthesizeToFile(content, null, new File(tempFileName), "temp_utterance_id");
                if (status == TextToSpeech.ERROR) {
                    Log.e("DuixTTS", "Lỗi TTS: Text không hợp lệ");
                }
            }

            @Override
            public void onError(String error) {
                Log.e("DuixCerebras", "Lỗi gọi AI: " + error);
            }
        });
    }

    // --- HÀM HỖ TRỢ CEREBRAS API ---
    private void askCerebras(final String text, final CerebrasCallback callback) {
        new Thread(() -> {
            try {
                URL url = new URL("https://api.cerebras.ai/v1/chat/completions");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                
                // --- KEY VÀ MODEL ĐÃ CÀI ĐẶT ---
                conn.setRequestProperty("Authorization", "Bearer csk-tmj9882rd95p388jedk9632nk4f99hdmkcjc5rrp3nptp8fe");
                
                conn.setDoOutput(true);

                // Model: zai-glm-4.7
                String jsonInputString = "{\"model\": \"zai-glm-4.7\", \"messages\": [{\"role\": \"user\", \"content\": \"" + text + "\"}], \"stream\": false}";

                try(OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonInputString.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    
                    String content = parseJsonContent(response.toString());
                    callback.onSuccess(content);
                } else {
                    callback.onError("API Error: " + responseCode);
                }
            } catch (Exception e) {
                e.printStackTrace();
                callback.onError(e.getMessage());
            }
        }).start();
    }

    // --- HÀM HỖ TRỢ TTS (TẮM TIẾNG VIỆT) ---
    private void initTts() {
        if (mTts == null) {
            mTts = new TextToSpeech(mContext, new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    if (status != TextToSpeech.ERROR) {
                        int result = mTts.setLanguage(new Locale("vi", "VN"));
                        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            Log.e("DuixTTS", "Không hỗ trợ tiếng Việt, dùng tiếng Anh");
                            mTts.setLanguage(Locale.US);
                        } else {
                            Log.i("DuixTTS", "Đã cài đặt Tiếng Việt thành công!");
                        }
                    }
                }
            });
        }
    }

    // --- HÀM HỖ TRỢ JSON PARSER ĐƠN GIẢN ---
    private String parseJsonContent(String jsonResponse) {
        try {
            int start = jsonResponse.indexOf("\"content\": \"");
            if (start == -1) return "Không hiểu";
            start += "\"content\": \"".length();
            int end = jsonResponse.indexOf("\"", start);
            return jsonResponse.substring(start, end);
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    // --- INTERFACE TRỢ GIÚP ---
    interface CerebrasCallback {
        void onSuccess(String content);
        void onError(String error);
    }
}
