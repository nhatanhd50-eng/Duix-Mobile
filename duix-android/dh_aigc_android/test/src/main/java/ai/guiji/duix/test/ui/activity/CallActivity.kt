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
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.bumptech.glide.Glide
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class CallActivity : BaseActivity() {

    companion object {
        const val GL_CONTEXT_VERSION = 2
    }

    private var modelUrl = ""
    private var debug = false
    private var mMessage = ""

    private lateinit var binding: ActivityCallBinding

    // Duix object được tạo trong onCreate sẽ lưu vào đây
    private var duix: DUIX? = null
    private var mDUIXRender: DUIXRenderer? = null
    private var mModelInfo: ModelInfo? = null

    @SuppressLint("SetTextI18n")
    private fun applyMessage(msg: String) {
        if (debug) {
            runOnUiThread {
                binding.tvDebug.visibility = View.VISIBLE
                if (mMessage.length > 10000) {
                    mMessage = ""
                }
                mMessage = "${StringUtils.dateToStringMS4()} $msg\n$mMessage"
                binding.tvDebug.text = mMessage
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        keepScreenOn()
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        modelUrl = intent.getStringExtra("modelUrl") ?: ""
        debug = intent.getBooleanExtra("debug", false)

        Glide.with(mContext).load("file:///android_asset/bg1.png").into(binding.ivBg)

        setupAvatarRenderer()
        setupChatUI()
        setupEventListeners()
        initializeDUIX()
    }

    /**
     * CÀI ĐẶT RENDERER CHO AVATAR
     */
    private fun setupAvatarRenderer() {
        binding.glTextureView.apply {
            setEGLContextClientVersion(GL_CONTEXT_VERSION)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            isOpaque = false
            renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        }

        mDUIXRender = DUIXRenderer(mContext, binding.glTextureView)
        binding.glTextureView.setRenderer(mDUIXRender)
    }

    /**
     * CÀI ĐẶT GIAO DIỆN CHAT (ĐƯỢC GỌI TỪ BASE CLASS)
     */
    override fun setupChatUI() {
        // Gọi từ base class để setup giao diện chat chuẩn
        super.setupChatUI()

        // Bổ sung sự kiện gửi tin nhắn
        btnSend?.setOnClickListener {
            sendChatMessage()
        }
    }

    /**
     * GỬI TIN NHẮN ĐẾN DUIX SDK
     */
    private fun sendChatMessage() {
        val text = inputText?.text?.toString()?.trim() ?: ""

        if (text.isEmpty()) {
            Toast.makeText(mContext, "Vui lòng nhập tin nhắn", Toast.LENGTH_SHORT).show()
            return
        }

        // Hiển thị tin nhắn người dùng
        addChatMessage(text, true)

        // Gửi đến AI thông qua DUIX
        duix?.askAndSpeak(text)

        // Xóa ô nhập
        inputText?.text?.clear()
    }

    /**
     * CÀI ĐẶT CÁC SỰ KIỆN NHƯ RECORD, PCM, WAV...
     */
    private fun setupEventListeners() {
        binding.apply {
            switchMute.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    duix?.setVolume(0.0F)
                } else {
                    duix?.setVolume(1.0F)
                }
            }

            btnRecord.setOnClickListener {
                requestPermission(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            }

            btnPlayPCM.setOnClickListener {
                applyMessage("start play pcm")
                playPCMStream()
            }

            btnPlayWAV.setOnClickListener {
                applyMessage("start play wav")
                playWAVFile()
            }

            btnRandomMotion.setOnClickListener {
                applyMessage("start random motion")
                duix?.startRandomMotion(true)
            }

            btnStopPlay.setOnClickListener {
                duix?.stopAudio()
            }
        }
    }

    /**
     * KHỞI TẠO DUIX SDK
     */
    private fun initializeDUIX() {
        duix = DUIX(mContext, modelUrl, mDUIXRender) { event, msg, info ->
            when (event) {
                Constant.CALLBACK_EVENT_INIT_READY -> {
                    mModelInfo = info as ModelInfo
                    Log.i(TAG, "CALLBACK_EVENT_INIT_READY: $mModelInfo")
                    initOk()
                }

                Constant.CALLBACK_EVENT_INIT_ERROR -> {
                    runOnUiThread {
                        applyMessage("init error: $msg")
                        Log.e(TAG, "CALLBACK_EVENT_INIT_ERROR: $msg")
                        Toast.makeText(
                            mContext,
                            "Initialization exception: $msg",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                Constant.CALLBACK_EVENT_AUDIO_PLAY_START -> {
                    applyMessage("callback audio play start")
                    Log.i(TAG, "CALLBACK_EVENT_AUDIO_PLAY_START")
                }

                Constant.CALLBACK_EVENT_AUDIO_PLAY_END -> {
                    applyMessage("callback audio play end")
                    Log.i(TAG, "CALLBACK_EVENT_AUDIO_PLAY_END")
                    // Sau khi audio kết thúc, có thể thêm hiệu ứng gì đó
                }

                Constant.CALLBACK_EVENT_AUDIO_PLAY_ERROR -> {
                    applyMessage("callback audio play error: $msg")
                    Log.e(TAG, "CALLBACK_EVENT_AUDIO_PLAY_ERROR: $msg")
                }

                Constant.CALLBACK_EVENT_MOTION_START -> {
                    applyMessage("callback motion play start")
                    Log.e(TAG, "CALLBACK_EVENT_MOTION_START")
                }

                Constant.CALLBACK_EVENT_MOTION_END -> {
                    applyMessage("callback motion play end")
                    Log.i(TAG, "CALLBACK_EVENT_MOTION_END")
                }

                // Thêm callback nhận text từ AI response để hiển thị chat
                Constant.CALLBACK_EVENT_AI_RESPONSE -> {
                    runOnUiThread {
                        addChatMessage(msg, false) // Hiển thị phản hồi từ AI
                    }
                }
            }
        }
        applyMessage("start init")
        duix?.init()
    }

    private fun initOk() {
        Log.i(TAG, "init ok")
        applyMessage("init ok")
        runOnUiThread {
            binding.apply {
                btnRecord.isEnabled = true
                btnPlayPCM.isEnabled = true
                btnPlayWAV.isEnabled = true
                switchMute.isEnabled = true
                btnStopPlay.isEnabled = true

                mModelInfo?.let { modelInfo ->
                    if (modelInfo.motionRegions.isNotEmpty()) {
                        val names = ArrayList<String>()
                        for (motion in modelInfo.motionRegions) {
                            if (!TextUtils.isEmpty(motion.name) && "unknown" != motion.name) {
                                names.add(motion.name)
                            }
                        }
                        if (names.isNotEmpty()) {
                            val motionAdapter = MotionAdapter(names, object : MotionAdapter.Callback {
                                override fun onClick(name: String, now: Boolean) {
                                    applyMessage("start [$name] motion")
                                    duix?.startMotion(name, now)
                                }
                            })
                            rvMotion.adapter = motionAdapter
                        }
                        btnRandomMotion.visibility = View.VISIBLE
                        tvMotionTips.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun playPCMStream() {
        val thread = Thread {
            duix?.startPush()
            val inputStream = assets.open("pcm/2.pcm")
            val buffer = ByteArray(320)
            var length = 0
            while (inputStream.read(buffer).also { length = it } > 0) {
                val data = buffer.copyOfRange(0, length)
                duix?.pushPcm(data)
            }
            duix?.stopPush()
            inputStream.close()
        }
        thread.start()
    }

    private fun playWAVFile() {
        val thread = Thread {
            val wavName = "1.wav"
            val wavFile = File(mContext.externalCacheDir, wavName)
            if (!wavFile.exists()) {
                val inputStream = assets.open("wav/$wavName")
                if (!mContext.externalCacheDir!!.exists()) {
                    mContext.externalCacheDir!!.mkdirs()
                }
                val out = FileOutputStream(wavFile)
                val buffer = ByteArray(1024)
                var length = 0
                while ((inputStream.read(buffer).also { length = it }) > 0) {
                    out.write(buffer, 0, length)
                }
                out.close()
                inputStream.close()
            }
            duix?.playAudio(wavFile.absolutePath)
        }
        thread.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        duix?.release()
    }

    override fun permissionsGet(get: Boolean, code: Int) {
        super.permissionsGet(get, code)
        if (get) {
            showRecordDialog()
        } else {
            Toast.makeText(mContext, R.string.need_permission_continue, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRecordDialog() {
        val audioRecordDialog = AudioRecordDialog(mContext, object : AudioRecordDialog.Listener {
            override fun onFinish(path: String) {
                val thread = Thread {
                    duix?.startPush()
                    val inputStream = FileInputStream(path)
                    val buffer = ByteArray(320)
                    var length = 0
                    while (inputStream.read(buffer).also { length = it } > 0) {
                        val data = buffer.copyOfRange(0, length)
                        duix?.pushPcm(data)
                    }
                    duix?.stopPush()
                    inputStream.close()
                }
                thread.start()
            }
        })
        audioRecordDialog.show()
    }
}
