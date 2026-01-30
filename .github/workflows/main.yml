package ai.guiji.duix.test.ui.activity

import ai.guiji.duix.sdk.client.Constant
import ai.guiji.duix.sdk.client.DUIX
import ai.guiji.duix.sdk.client.loader.ModelInfo
import ai.guiji.duix.sdk.client.render.DUIXRenderer
import ai.guiji.duix.sdk.client.thread.RenderThread
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
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.widget.CompoundButton
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

    // --- CÁC BIẾN GIAO DIỆN CHAT MỚI ---
    // Đổi thành 'protected' để BaseActivity truy cập được
    // Tạo biến UI ở đây thay vì BaseActivity có thể private
    protected var etChat: EditText? = null
    protected var btnSend: Button? = null
    protected var chatContainer: LinearLayout? = null
    
    // Duix object được tạo trong onCreate sẽ lưu vào đây
    private var duix: DUIX? = null
    private var mDUIXRender: DUIXRenderer? = null
    private var mModelInfo: ModelInfo? = null

    @SuppressLint("SetTextI18n")
    private fun applyMessage(msg: String){
        if (debug){
            runOnUiThread {
                binding.tvDebug.visibility = View.VISIBLE
                if (mMessage.length >10000){
                    mMessage = ""
                }
                mMessage = "${StringUtils.dateToStringMS4()} $msg\n$mMessage"
                binding.tvDebug.text = mMessage
            }
        }
    }

    private lateinit var binding: ActivityCallBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        keepScreenOn()
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        modelUrl = intent.getStringExtra("modelUrl") ?: ""
        debug = intent.getBooleanExtra("debug", false)

        Glide.with(mContext).load("file:///android_asset/bg1.png").into(binding.ivBg)

        binding.glTextureView.setEGLContextClientVersion(GL_CONTEXT_VERSION)
        binding.glTextureView.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        binding.glTextureView.isOpaque = false
        binding.glTextureView.renderMode =
            GLSurfaceView.RENDERMODE_WHEN_DIRTY

        binding.switchMute.setOnCheckedChangeListener(object : CompoundButton.OnCheckedChangeListener {
            override fun onCheckedChanged(
                buttonView: CompoundButton?,
                isChecked: Boolean,
            ) {
                if (isChecked) {
                    duix?.setVolume(0.0F)
                } else {
                    duix?.setVolume(1.0F)
                }
            }
        })

        binding.btnRecord.setOnClickListener {
            requestPermission(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }

        binding.btnPlayPCM.setOnClickListener {
            applyMessage("start play pcm")
            playPCMStream()
        }

        binding.btnPlayWAV.setOnClickListener {
            applyMessage("start play wav")
            playWAVFile()
        }

        binding.btnRandomMotion.setOnClickListener {
            applyMessage("start random motion")
            duix?.startRandomMotion(true)
        }
        binding.btnStopPlay.setOnClickListener {
            duix?.stopAudio()
        }
        
        // GÁN LOGIC CHAT MỚI
        setupChatUI()

        // Khởi tạo Duix và Render
        mDUIXRender =
            DUIXRenderer(
                mContext,
                binding.glTextureView
            )
        binding.glTextureView.setRenderer(mDUIXRender)

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
                        Toast.makeText(mContext, "Initialization exception: $msg", Toast.LENGTH_SHORT).show()
                    }
                }

                Constant.CALLBACK_EVENT_AUDIO_PLAY_START -> {
                    applyMessage("callback audio play start")
                    Log.i(TAG, "CALLBACK_EVENT_AUDIO_PLAY_START")
                }

                Constant.CALLBACK_EVENT_AUDIO_PLAY_END -> {
                    applyMessage("callback audio play end")
                    Log.i(TAG, "CALLBACK_EVENT_AUDIO_PLAY_END")
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
                    Log.i(TAG, "CallACK_EVENT_MOTION_END")
                }
            }
        }
        applyMessage("start init")
        duix?.init()
    }

    /**
     * HÀM TẠO GIAO DIỆN CHAT (VÀO TRÊN MÀN HÌNH CALL ACTIVITY)
     */
    private fun setupChatUI() {
        // Kiểm tra xem binding.root là LinearLayout để thêm view được
        if (binding.root !is ViewGroup) return

        val rootGroup = binding.root as ViewGroup
        
        // 1. Tạo ScrollView cho tin nhắn
        val scrollView = ScrollView(mContext)
        scrollView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f
        )
        scrollView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        rootGroup.addView(scrollView, 0) // Chèn vào vị trí 0 (Dưới cùng)

        chatContainer = LinearLayout(mContext)
        chatContainer!!.orientation = LinearLayout.VERTICAL
        chatContainer!!.setPadding(20, 20, 20, 20)
        chatContainer!!.gravity = Gravity.BOTTOM
        
        scrollView.addView(chatContainer)

        // 2. Tạo Layout nhập liệu (Input Box)
        val inputLayout = LinearLayout(mContext)
        inputLayout.orientation = LinearLayout.HORIZONTAL
        inputLayout.gravity = Gravity.CENTER_VERTICAL
        inputLayout.setBackgroundColor(android.graphics.Color.parseColor("#2C2C2C"))
        inputLayout.setPadding(15, 15, 15, 15)
        
        val inputParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        rootGroup.addView(inputLayout, rootGroup.childCount) // Thêm vào cuối cùng

        // Ô nhập liệu
        etChat = EditText(mContext)
        etChat!!.hint = "Nhập tin nhắn..."
        etChat!!.backgroundColor = android.graphics.Color.TRANSPARENT
        etChat!!.setTextColor(android.graphics.Color.WHITE)
        etChat!!.setHintTextColor(android.graphics.Color.GRAY)
        etChat!!.setPadding(10, 10, 10, 10)
        etChat!!.textSize = 14f
        
        val editParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
        editParams.setMargins(0, 0, 10, 0)
        inputLayout.addView(etChat, editParams)

        // Nút gửi
        btnSend = Button(mContext)
        btnSend!!.text = "Gửi"
        btnSend!!.setBackgroundColor(android.graphics.Color.parseColor("#4CAF50"))
        btnSend!!.setTextColor(android.graphics.Color.WHITE)
        btnSend!!.setPadding(20, 10, 20, 10)
        
        val btnParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        inputLayout.addView(btnSend, btnParams)

        // 3. Xử lý sự kiện bấm nút gửi
        btnSend!!.setOnClickListener {
            sendChatMessage()
        }
    }

    /**
     * HÀM GỬI TIN NHẮN -> GỌI AI -> TTS -> NHÂN VẬT NÓI
     */
    private fun sendChatMessage() {
        val text = etChat?.text.toString().trim() ?: ""
        
        if (text.isEmpty()) {
            Toast.makeText(mContext, "Vui lòng nhập tin nhắn", Toast.LENGTH_SHORT).show()
            return
        }

        // 1. Hiển thị tin nhắn của User
        addChatMessage(text, true)

        // 2. Gọi AI (Nếu duix đã được khởi tạo ở đâu đó)
        // Hàm askAndSpeak đã được tích hợp trong DUIX.java
        duix?.askAndSpeak(text)

        // 3. Xóa ô nhập
        etChat?.text?.clear()
    }

    /**
     * HÀM HIỆN THỊ TIN NHẮN
     */
    private fun addChatMessage(text: String, isMine: Boolean) {
        if (chatContainer == null) return

        val msgWrapper = LinearLayout(mContext)
        msgWrapper.orientation = LinearLayout.HORIZONTAL
        msgWrapper.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        msgWrapper.gravity = if (isMine) Gravity.RIGHT else Gravity.LEFT
        msgWrapper.setPadding(0, 0, 0, 10)

        val msgBubble = TextView(mContext)
        msgBubble.text = text
        msgBubble.textSize = 15f
        msgBubble.setTextColor(android.graphics.Color.WHITE)
        msgBubble.setPadding(20, 15, 20, 15)
        
        if (isMine) {
            msgBubble.setBackgroundColor(android.graphics.Color.parseColor("#00BCD4"))
            val params = msgWrapper.layoutParams as LinearLayout.LayoutParams
            params.rightMargin = 50
            msgWrapper.layoutParams = params
        } else {
            msgBubble.setBackgroundColor(android.graphics.Color.parseColor("#3E3E3E"))
            val params = msgWrapper.layoutParams as LinearLayout.LayoutParams
            params.leftMargin = 50
            msgWrapper.layoutParams = params
        }
        
        msgWrapper.addView(msgBubble)
        chatContainer!!.addView(msgWrapper)
        
        // Tự động cuộn xuống
        if (chatContainer!!.parent is ScrollView) {
            (chatContainer!!.parent as ScrollView).fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    private fun initOk() {
        Log.i(TAG, "init ok")
        applyMessage("init ok")
        runOnUiThread {
            binding.btnRecord.isEnabled = true
            binding.btnPlayPCM.isEnabled = true
            binding.btnPlayWAV.isEnabled = true
            binding.switchMute.isEnabled = true
            binding.btnStopPlay.isEnabled = true

            mModelInfo?.let { modelInfo ->
                if (modelInfo.motionRegions.isNotEmpty()) {
                    val names = ArrayList<String>()
                    for (motion in modelInfo.motionRegions){
                        if (!TextUtils.isEmpty(motion.name) && "unknown" != motion.name){
                            names.add(motion.name)
                        }
                    }
                    // Named action regions
                    if (names.isNotEmpty()){
                        val motionAdapter = MotionAdapter(names, object : MotionAdapter.Callback{
                            override fun onClick(name: String, now: Boolean) {
                                applyMessage("start [${name}] motion")
                                duix?.startMotion(name, now)
                            }
                        })
                        binding.rvMotion.adapter = motionAdapter
                    }
                    binding.btnRandomMotion.visibility = View.VISIBLE
                    binding.tvMotionTips.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun playPCMStream(){
        val thread = Thread {
            duix?.startPush()
            val inputStream = assets.open("pcm/2.pcm")
            val buffer = ByteArray(320)
            var length = 0
            while (inputStream.read(buffer).also { length = it } > 0){
                val data = buffer.copyOfRange(0, length)
                duix?.pushPcm(data)
            }
            duix?.stopPush()
            inputStream.close()
        }
        thread.start()
    }

    private fun playWAVFile(){
        val thread = Thread {
            val wavName = "1.wav"
            val wavFile = File(mContext.externalCacheDir, wavName)
            if (!wavFile.exists()){
                // copy assets -> sd card
                val inputStream = assets.open("wav/$wavName")
                if (!mContext.externalCacheDir!!.exists()){
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
        if (get){
            showRecordDialog()
        } else {
            Toast.makeText(mContext, R.string.need_permission_continue, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRecordDialog(){
        val audioRecordDialog = AudioRecordDialog(mContext, object : AudioRecordDialog.Listener{
            override fun onFinish(path: String) {
                val thread = Thread {
                    duix?.startPush()
                    val inputStream = FileInputStream(path)
                    val buffer = ByteArray(320)
                    var length = 0
                    while (inputStream.read(buffer).also { length = it } > 0){
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
