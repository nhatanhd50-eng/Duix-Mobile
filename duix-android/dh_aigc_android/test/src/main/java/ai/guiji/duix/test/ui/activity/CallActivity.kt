package ai.guiji.duix.test.ui.activity

import ai.guiji.duix.sdk.client.Constant
import ai.guiji.duix.sdk.client.DUIX
import ai.guiji.duix.sdk.client.loader.ModelInfo
import ai.guiji.duix.sdk.client.render.DUIXRenderer
import ai.guiji.duix.test.databinding.ActivityCallBinding
import android.Manifest
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import com.bumptech.glide.Glide

class CallActivity : BaseActivity() {

    private lateinit var binding: ActivityCallBinding
    private var modelUrl = ""
    private var mDUIXRender: DUIXRenderer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        modelUrl = intent.getStringExtra("modelUrl") ?: ""
        Glide.with(mContext).load("file:///android_asset/bg1.png").into(binding.ivBg)

        // 1. Setup Renderer
        binding.glTextureView.apply {
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            isOpaque = false
            renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        }
        mDUIXRender = DUIXRenderer(mContext, binding.glTextureView)
        binding.glTextureView.setRenderer(mDUIXRender)

        // 2. Setup Chat UI
        setupChatUI()

        // 3. Init DUIX
        duix = DUIX(mContext, modelUrl, mDUIXRender) { event, msg, info ->
            when (event) {
                Constant.CALLBACK_EVENT_INIT_READY -> runOnUiThread { 
                    binding.btnRecord.isEnabled = true
                    Toast.makeText(mContext, "Avatar Ready!", Toast.LENGTH_SHORT).show()
                }
                Constant.CALLBACK_EVENT_INIT_ERROR -> runOnUiThread {
                    Toast.makeText(mContext, "Lỗi: $msg", Toast.LENGTH_LONG).show()
                }
            }
        }
        duix?.init()
        
        // 4. Buttons
        binding.btnRecord.setOnClickListener { requestPermission(arrayOf(Manifest.permission.RECORD_AUDIO), 1) }
        binding.btnStopPlay.setOnClickListener { duix?.stopPush() }
    }

    override fun onDestroy() {
        super.onDestroy()
        duix?.release()
    }
    
    override fun permissionsGet(get: Boolean, code: Int) {
        if (get) Toast.makeText(mContext, "Đã cấp quyền", Toast.LENGTH_SHORT).show()
    }
}
