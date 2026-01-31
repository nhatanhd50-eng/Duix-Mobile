package ai.guiji.duix.sdk.client;

import android.content.Context;
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
    
    private ExecutorService commonExecutor = Executors.newSingleThreadExecutor();
    private RenderThread mRenderThread;

    private boolean isReady;
    private float mVolume = 1.0F;
    private RenderThread.Reporter reporter;

    private TextToSpeech mTts;
    private final String TTS_UTTERANCE_ID = "DUIX_TTS_ID";

    public DUIX(Context context, String modelName, RenderSink sink, Callback callback) {
        this.mContext = context;
        this.mCallback = callback;
        this.modelName = modelName;
        this.renderSink = sink;
    }

    public void init() {
        try {
            File duixDir = mContext.getExternalFilesDir("duix");
            File baseConfigDir = new File(duixDir + "/model/gj_dh_res");
            
            // --- FIX CRASH 1: Kiểm tra thư mục tài nguyên chung ---
            if (!baseConfigDir.exists()) {
                Log.e(TAG, "CRITICAL: Base Config Missing at " + baseConfigDir.getAbsolutePath());
                if (mCallback != null) mCallback.onEvent(Constant.CALLBACK_EVENT_INIT_ERROR, "Base Resource missing", null);
                return;
            }

            // --- FIX CRASH 2: Logic tìm đường dẫn Model thông minh hơn ---
            String dirName = modelName;
            if (modelName.startsWith("http")) {
                 try { dirName = modelName.substring(modelName.lastIndexOf("/") + 1).replace(".zip", ""); } catch (Exception e) {}
            }
            
            File modelDir = new File(duixDir + "/model", dirName);
            
            // Tự động tìm file config.json nếu bị lồng thư mục (Nested Folder Fix)
            if (modelDir.exists() && !new File(modelDir, "config.json").exists()) {
                File[] subFiles = modelDir.listFiles(File::isDirectory);
                if (subFiles != null && subFiles.length > 0) {
                    Log.w(TAG, "Model directory seems nested. Switching to: " + subFiles[0].getAbsolutePath());
                    modelDir = subFiles[0];
                }
            }
            
            if (!new File(modelDir, "config.json").exists()) {
                Log.e(TAG, "CRITICAL: config.json NOT FOUND in " + modelDir.getAbsolutePath());
                if (mCallback != null) mCallback.onEvent(Constant.CALLBACK_EVENT_INIT_ERROR, "Invalid Model Path", null);
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
                        if (mCallback != null) mCallback.onEvent(Constant.CALLBACK_EVENT_INIT_READY, "init ok", modelInfo);
                        initTts(); 
                    } else {
                        if (mCallback != null) mCallback.onEvent(Constant.CALLBACK_EVENT_INIT_ERROR, message, null);
                    }
                }
                @Override public void onPlayStart() { if (mCallback != null) mCallback.onEvent(Constant.CALLBACK_EVENT_AUDIO_PLAY_START, "play start", null); }
                @Override public void onPlayEnd() { if (mCallback != null) mCallback.onEvent(Constant.CALLBACK_EVENT_AUDIO_PLAY_END, "play end", null); }
                @Override public void onPlayError(int code, String msg) { if (mCallback != null) mCallback.onEvent(Constant.CALLBACK_EVENT_AUDIO_PLAY_ERROR, msg, null); }
                @Override public void onMotionPlayStart(String name) { if (mCallback != null) mCallback.onEvent(Constant.CALLBACK_EVENT_MOTION_START, "", null); }
                @Override public void onMotionPlayComplete(String name) { if (mCallback != null) mCallback.onEvent(Constant.CALLBACK_EVENT_MOTION_END, "", null); }
            }, reporter);
            
            mRenderThread.setName("DUIXRender-Thread");
            mRenderThread.start();
            
        } catch (Exception e) {
            Log.e(TAG, "CRASH PREVENTED in DUIX.init(): " + e.getMessage());
            e.printStackTrace();
        }
    }

    public boolean isReady() { return isReady; }
    public void setVolume(float volume) { 
        mVolume = volume; 
        if (mRenderThread != null) mRenderThread.setVolume(volume); 
    }

    public void startPush() { if (mRenderThread != null) mRenderThread.startPush(); }
    public void stopPush() { if (mRenderThread != null) mRenderThread.stopPush(); }
    public void pushPcm(byte[] buffer) { if (mRenderThread != null) mRenderThread.pushAudio(buffer.clone()); }

    public void startMotion(String name, boolean now) {
        if (mRenderThread != null) mRenderThread.requireMotion(name, now);
    }

    public void startRandomMotion(boolean now) {
        if (mRenderThread != null) mRenderThread.requireRandomMotion(now);
    }

    public void stopAudio() {
        if (mRenderThread != null) mRenderThread.stopPlayAudio();
        if (mTts != null) mTts.stop();
    }
    
    public void playAudio(String wavPath) {}

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

    public void askAndSpeak(final String text) {
        if (!isReady()) return;
        commonExecutor.execute(() -> {
            String aiResponse = callCerebrasApi(text);
            if (aiResponse != null && !aiResponse.isEmpty()) {
                speakWithLipSync(aiResponse);
            }
        });
    }

    private void speakWithLipSync(String text) {
        if (mTts == null) return;
        File tempFile = new File(mContext.getCacheDir(), "tts_temp.wav");
        if (tempFile.exists()) tempFile.delete();
        mTts.synthesizeToFile(text, null, tempFile, TTS_UTTERANCE_ID);
    }

    private String callCerebrasApi(String prompt) {
        try {
            URL url = new URL("https://api.cerebras.ai/v1/chat/completions");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer csk-tmj9882rd95p388jedk9632nk4f99hdmkcjc5rrp3nptp8fe");
            conn.setDoOutput(true);
            String jsonInputString = "{\"model\": \"zai-glm-4.7\", \"messages\": [{\"role\": \"user\", \"content\": \"" + prompt + "\"}], \"stream\": false}";
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                os.write(input, 0, input.length);
            }
            if (conn.getResponseCode() == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) response.append(line.trim());
                JSONObject json = new JSONObject(response.toString());
                return json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
            }
        } catch (Exception e) {
            Log.e(TAG, "AI Error: " + e.getMessage());
        }
        return null;
    }

    private void initTts() {
        if (mTts == null) {
            mTts = new TextToSpeech(mContext, status -> {
                if (status == TextToSpeech.SUCCESS) {
                    mTts.setLanguage(new Locale("vi", "VN"));
                    mTts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override public void onStart(String utteranceId) {}
                        @Override public void onDone(String utteranceId) {
                            if (TTS_UTTERANCE_ID.equals(utteranceId)) playGeneratedAudio();
                        }
                        @Override public void onError(String utteranceId) {}
                    });
                }
            });
        }
    }

    private void playGeneratedAudio() {
        File tempFile = new File(mContext.getCacheDir(), "tts_temp.wav");
        if (tempFile.exists() && tempFile.length() > 44) {
            try (FileInputStream fis = new FileInputStream(tempFile)) {
                byte[] data = new byte[(int) tempFile.length()];
                fis.read(data);
                startPush();
                pushPcm(java.util.Arrays.copyOfRange(data, 44, data.length));
                stopPush();
            } catch (Exception e) {}
        }
    }
}
