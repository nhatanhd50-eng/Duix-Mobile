package ai.guiji.duix.test.ui.activity

import ai.guiji.duix.sdk.client.Constant
import ai.guiji.duix.sdk.client.DUIX
import ai.guiji.duix.sdk.client.loader.ModelInfo
import ai.guiji.duix.sdk.client.render.DUIXRenderer
import ai.guiji.duix.test.R
import ai.guiji.duix.test.databinding.ActivityCallBinding
import ai.guiji.duix.test.ui.adapter.MotionAdapter
import ai.guiji.duix.test.ui.dialog.AudioRecordDialog
import ai.guiji.duix.test.util.StringUtils
import android.Manifest
import android.annotation.SuppressLint
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.Toast
import com.bumptech.glide.Glide
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.ArrayList

class CallActivity : BaseActivity() {

    companion object {
        const val GL_CONTEXT_VERSION = 2
    }

    private var modelUrl = ""
    private var debug = false
    private var mMessage = ""

    private lateinit var binding: ActivityCallBinding

    // Renderer cho Avatar
    private var mDUIXRender: DUIXRenderer? = null
    private var mModelInfo: ModelInfo? = null

    @SuppressLint("SetTextI18n")
    private fun applyMessage(msg: String) {
        if (debug) {
            runOnUiThread {
                binding.tvDebug.visibility = View.VISIBLE
                if (mMessage.length > 5000) {
                    mMessage = ""
                }
                mMessage = "${StringUtils.dateToStringMS4()} $msg\n$mMessage"
                binding.tvDebug.text = mMessage
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        keepScreenOn() // Giữ màn hình luôn sáng
        
        // Setup ViewBinding cho layout chính (chứa GLSurfaceView)
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Lấy dữ liệu từ Intent
        modelUrl = intent.getStringExtra("modelUrl") ?: ""
        debug = intent.getBooleanExtra("debug", false)

        // Load hình nền
        Glide.with(mContext).load("file:///android_asset/bg1.png").into(binding.ivBg)

        // 1. Setup Avatar Renderer (OpenGL)
        setupAvatarRenderer()

        // 2. Setup Chat UI (Overlay khung chat lên trên Avatar)
        // Hàm này nằm ở BaseActivity
        setupChatUI()

        // 3. Setup các nút bấm chức năng (Record, Play WAV...)
        setupEventListeners()

        // 4. Khởi tạo DUIX Engine (AI + TTS + Avatar)
        initializeDUIX()
    }

    private fun setupAvatarRenderer() {
        binding.glTextureView.apply {
            setEGLContextClientVersion(GL_CONTEXT_VERSION)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            isOpaque = false // Để nền trong suốt nhìn thấy background
            renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        }

        mDUIXRender = DUIXRenderer(mContext, binding.glTextureView)
        binding.glTextureView.setRenderer(mDUIXRender)
    }

    /**
     * Override lại setupChatUI của BaseActivity để gán sự kiện click cho nút Gửi
     */
    override fun setupChatUI() {
        super.setupChatUI() // Gọi base để vẽ giao diện

        // Gán sự kiện click cho nút Send (btnSend được khai báo trong BaseActivity)
        btnSend?.setOnClickListener {
            val text = inputText?.text?.toString()?.trim() ?: ""
            if (text.isNotEmpty()) {
                // 1. Hiện tin nhắn của người dùng lên khung chat
                addChatMessage("Bạn: $text", true)
                
                // 2. Gửi text cho DUIX xử lý (AI -> TTS -> LipSync)
                duix?.askAndSpeak(text)
                
                // 3. Xóa ô nhập liệu
                inputText?.text?.clear()
            }
        }
    }

    private fun setupEventListeners() {
        binding.apply {
            // Nút Mute âm thanh
            switchMute.setOnCheckedChangeListener { _, isChecked ->
                duix?.setVolume(if (isChecked) 0.0F else 1.0F)
            }

            // Nút Ghi âm (Cần quyền)
            btnRecord.setOnClickListener {
                requestPermission(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            }

            // Các nút test âm thanh (nếu cần debug)
            btnPlayPCM.setOnClickListener {
                applyMessage("start play pcm")
                playPCMStream()
            }

            btnPlayWAV.setOnClickListener {
                applyMessage("start play wav")
                playWAVFile()
            }

            // Nút chuyển động ngẫu nhiên
            btnRandomMotion.setOnClickListener {
                applyMessage("start random motion")
                duix?.startRandomMotion(true)
            }

            // Nút dừng nói
            btnStopPlay.setOnClickListener {
                duix?.stopPush() // Dừng đẩy PCM
            }
        }
    }

    private fun initializeDUIX() {
        // Tạo đối tượng DUIX. Lưu ý: Interface Callback từ Java sang Kotlin
        duix = DUIX(mContext, modelUrl, mDUIXRender) { event, msg, info ->
            when (event) {
                Constant.CALLBACK_EVENT_INIT_READY -> {
                    mModelInfo = info as? ModelInfo
                    Log.i(TAG, "DUIX INIT READY")
                    initOk()
                }

                Constant.CALLBACK_EVENT_INIT_ERROR -> {
                    runOnUiThread {
                        applyMessage("Init Error: $msg")
                        Toast.makeText(mContext, "Lỗi khởi tạo: $msg", Toast.LENGTH_LONG).show()
                    }
                }

                Constant.CALLBACK_EVENT_AUDIO_PLAY_START -> {
                    Log.i(TAG, "Audio Start")
                    // Có thể thêm hiệu ứng UI khi bắt đầu nói
                }

                Constant.CALLBACK_EVENT_AUDIO_PLAY_END -> {
                    Log.i(TAG, "Audio End")
                }
                
                // Các event khác nếu cần...
            }
        }
        
        applyMessage("Start Init DUIX...")
        duix?.init()
    }

    private fun initOk() {
        applyMessage("DUIX Ready!")
        runOnUiThread {
            // Mở khóa các nút chức năng
            binding.apply {
                btnRecord.isEnabled = true
                btnPlayPCM.isEnabled = true
                btnPlayWAV.isEnabled = true
                switchMute.isEnabled = true
                btnStopPlay.isEnabled = true

                // Load danh sách chuyển động (Motion) nếu có
                mModelInfo?.let { info ->
                    if (info.motionRegions.isNotEmpty()) {
                        val names = ArrayList<String>()
                        for (motion in info.motionRegions) {
                            if (!TextUtils.isEmpty(motion.name) && "unknown" != motion.name) {
                                names.add(motion.name)
                            }
                        }
                        if (names.isNotEmpty()) {
                            val adapter = MotionAdapter(names, object : MotionAdapter.Callback {
                                override fun onClick(name: String, now: Boolean) {
                                    duix?.startMotion(name, now)
                                }
                            })
                            rvMotion.adapter = adapter
                            btnRandomMotion.visibility = View.VISIBLE
                            tvMotionTips.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
    }

    // --- CÁC HÀM TEST ÂM THANH (GIỮ NGUYÊN TỪ CODE CŨ) ---

    private fun playPCMStream() {
        Thread {
            try {
                duix?.startPush()
                val inputStream = assets.open("pcm/2.pcm")
                val buffer = ByteArray(320)
                var length: Int
                while (inputStream.read(buffer).also { length = it } > 0) {
                    val data = buffer.copyOfRange(0, length)
                    duix?.pushPcm(data)
                }
                duix?.stopPush()
                inputStream.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun playWAVFile() {
        Thread {
            try {
                val wavName = "1.wav"
                val wavFile = File(mContext.externalCacheDir, wavName)
                if (!wavFile.exists()) {
                    val inputStream = assets.open("wav/$wavName")
                    if (!mContext.externalCacheDir!!.exists()) {
                        mContext.externalCacheDir!!.mkdirs()
                    }
                    val out = FileOutputStream(wavFile)
                    val buffer = ByteArray(1024)
                    var length: Int
                    while ((inputStream.read(buffer).also { length = it }) > 0) {
                        out.write(buffer, 0, length)
                    }
                    out.close()
                    inputStream.close()
                }
                // Sử dụng hàm playAudio cũ để test
                // Lưu ý: Logic chính bây giờ nằm ở askAndSpeak
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        duix?.release()
    }

    // Xử lý quyền Ghi âm
    override fun permissionsGet(get: Boolean, code: Int) {
        super.permissionsGet(get, code)
        if (get) {
            showRecordDialog()
        } else {
            Toast.makeText(mContext, "Cần quyền ghi âm để sử dụng tính năng này", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRecordDialog() {
        AudioRecordDialog(mContext, object : AudioRecordDialog.Listener {
            override fun onFinish(path: String) {
                Thread {
                    try {
                        duix?.startPush()
                        val inputStream = FileInputStream(path)
                        val buffer = ByteArray(320)
                        var length: Int
                        while (inputStream.read(buffer).also { length = it } > 0) {
                            val data = buffer.copyOfRange(0, length)
                            duix?.pushPcm(data)
                        }
                        duix?.stopPush()
                        inputStream.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }.start()
            }
        }).show()
    }
}
