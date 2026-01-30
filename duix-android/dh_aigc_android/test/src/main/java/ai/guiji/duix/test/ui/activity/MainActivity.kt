package ai.guiji.duix.test.ui.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import ai.guiji.duix.sdk.client.BuildConfig
import ai.guiji.duix.sdk.client.DUIX
import ai.guiji.duix.sdk.client.VirtualModelUtil
import ai.guiji.duix.test.R
import ai.guiji.duix.test.databinding.ActivityMainBinding
import ai.guiji.duix.test.ui.dialog.LoadingDialog
import ai.guiji.duix.test.ui.dialog.ModelSelectorDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private var mLoadingDialog: LoadingDialog? = null
    private var mLastProgress = 0

    val models = arrayListOf(
        "https://github.com/duixcom/Duix-Mobile/releases/download/v1.0.0/bendi3_20240518.zip",
        "https://github.com/duixcom/Duix-Mobile/releases/download/v1.0.0/airuike_20240409.zip",
        "https://github.com/duixcom/Duix-Mobile/releases/download/v1.0.0/675429759852613_7f8d9388a4213080b1820b83dd057cfb_optim_m80.zip",
        "https://github.com/duixcom/Duix-Mobile/releases/download/v1.0.0/674402003804229_f6e86fb375c4f1f1b82b24f7ee4e7cb4_optim_m80.zip",
        "https://github.com/duixcom/Duix-Mobile/releases/download/v1.0.0/674400178376773_3925e756433c5a9caa9b9d54147ae4ab_optim_m80.zip",
        "https://github.com/duixcom/Duix-Mobile/releases/download/v1.0.0/674397294927941_6e297e18a4bdbe35c07a6ae48a1f021f_optim_m80.zip",
        "https://github.com/duixcom/Duix-Mobile/releases/download/v1.0.0/674393494597701_f49fcf68f5afdb241d516db8a7d88a7b_optim_m80.zip",
        "https://github.com/duixcom/Duix-Mobile/releases/download/v1.0.0/651705983152197_ccf3256b2449c76e77f94276dffcb293_optim_m80.zip",
        "https://github.com/duixcom/Duix-Mobile/releases/download/v1.0.0/627306542239813_1871244b5e6912efc636ba31ea4c5c6d_optim_m80.zip",
    )

    private var mBaseConfigUrl = ""
    private var mModelUrl = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvSdkVersion.text = "SDK Version: ${BuildConfig.VERSION_NAME}"

        // Gán sự kiện cho các nút
        binding.btnMoreModel.setOnClickListener {
            val modelSelectorDialog = ModelSelectorDialog(mContext, models) { url ->
                binding.etUrl.setText(url.trim())
            }
            modelSelectorDialog.show()
        }

        binding.btnPlay.setOnClickListener {
            play()
        }

        // Gọi setupChatUI từ BaseActivity (đã được cải tiến)
        setupChatUI()

        // Gán lại sự kiện gửi tin nhắn
        btnSend?.setOnClickListener {
            sendMessage()
        }
    }

    /**
     * GỬI TIN NHẮN ĐẾN LLM
     */
    private fun sendMessage() {
        val text = inputText?.text?.toString()?.trim() ?: ""

        if (text.isEmpty()) {
            Toast.makeText(mContext, "Vui lòng nhập tin nhắn", Toast.LENGTH_SHORT).show()
            return
        }

        // Hiển thị tin nhắn người dùng
        addChatMessage(text, true)

        // Gọi LLM API để xử lý
        callLLMAPI(text)

        // Xóa ô nhập
        inputText?.text?.clear()
    }

    /**
     * GỌI API LLM (Ví dụ: OpenAI GPT)
     * Bạn có thể thay đổi endpoint này tùy theo LLM bạn dùng
     */
    private fun callLLMAPI(userInput: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // TODO: Gọi API LLM ở đây (ví dụ OpenAI, Claude, v.v.)
                // Dưới đây là mô phỏng phản hồi từ AI
                val aiResponse = mockLLMResponse(userInput)

                withContext(Dispatchers.Main) {
                    // Hiển thị phản hồi từ AI
                    addChatMessage(aiResponse, false)

                    // Nếu đã chuyển sang CallActivity, gửi tin nhắn đến duix để phát âm
                    // duix?.askAndSpeak(aiResponse) // <-- Gọi ở CallActivity
                }
            } catch (e: Exception) {
                Log.e(TAG, "LLM API Error", e)
                withContext(Dispatchers.Main) {
                    addChatMessage("❌ Lỗi khi gọi AI: ${e.message}", false)
                }
            }
        }
    }

    /**
     * MÔ PHỎNG PHẢN HỒI TỪ AI
     * Thay thế hàm này bằng API call thực tế
     */
    private fun mockLLMResponse(input: String): String {
        return when {
            input.contains("chào", ignoreCase = true) -> "Xin chào! Rất vui được gặp bạn."
            input.contains("tên", ignoreCase = true) -> "Tên tôi là Avatar AI. Tôi có thể giúp gì cho bạn?"
            input.contains("giúp", ignoreCase = true) -> "Tôi có thể trò chuyện, trả lời câu hỏi, và thậm chí biểu diễn hành động!"
            else -> "Tôi đã nhận được câu hỏi: \"$input\". Đây là phản hồi từ AI."
        }
    }

    private fun play() {
        mBaseConfigUrl = binding.etBaseConfig.text.toString().trim()
        mModelUrl = binding.etUrl.text.toString().trim()

        if (TextUtils.isEmpty(mBaseConfigUrl)) {
            Toast.makeText(mContext, R.string.base_config_cannot_be_empty, Toast.LENGTH_SHORT).show()
            return
        }
        if (TextUtils.isEmpty(mModelUrl)) {
            Toast.makeText(mContext, R.string.model_url_cannot_be_empty, Toast.LENGTH_SHORT).show()
            return
        }

        checkBaseConfig()
    }

    private fun checkBaseConfig() {
        if (VirtualModelUtil.checkBaseConfig(mContext)) {
            checkModel()
        } else {
            baseConfigDownload()
        }
    }

    private fun checkModel() {
        if (VirtualModelUtil.checkModel(mContext, mModelUrl)) {
            jumpPlayPage()
        } else {
            modelDownload()
        }
    }

    private fun jumpPlayPage() {
        val intent = Intent(mContext, CallActivity::class.java)
        intent.putExtra("modelUrl", mModelUrl)
        val debug = binding.switchDebug.isChecked
        intent.putExtra("debug", debug)
        startActivity(intent)
    }

    private fun baseConfigDownload() {
        mLoadingDialog?.dismiss()
        mLoadingDialog = LoadingDialog(mContext, "Đang tải config...")
        mLoadingDialog?.show()

        VirtualModelUtil.baseConfigDownload(mContext, mBaseConfigUrl, object :
            VirtualModelUtil.ModelDownloadCallback {
            override fun onDownloadProgress(url: String?, current: Long, total: Long) {
                val progress = (current * 100 / total).toInt()
                if (progress != mLastProgress) {
                    mLastProgress = progress
                    runOnUiThread {
                        if (mLoadingDialog?.isShowing == true) {
                            mLoadingDialog?.setContent("Config tải (${progress}%)")
                        }
                    }
                }
            }

            override fun onUnzipProgress(url: String?, current: Long, total: Long) {
                val progress = (current * 100 / total).toInt()
                if (progress != mLastProgress) {
                    mLastProgress = progress
                    runOnUiThread {
                        if (mLoadingDialog?.isShowing == true) {
                            mLoadingDialog?.setContent("Config giải nén (${progress}%)")
                        }
                    }
                }
            }

            override fun onDownloadComplete(url: String?, dir: File?) {
                runOnUiThread {
                    mLoadingDialog?.dismiss()
                    checkModel()
                }
            }

            override fun onDownloadFail(url: String?, code: Int, msg: String?) {
                runOnUiThread {
                    mLoadingDialog?.dismiss()
                    Toast.makeText(mContext, "Lỗi tải config: $msg", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun modelDownload() {
        mLoadingDialog?.dismiss()
        mLoadingDialog = LoadingDialog(mContext, "Đang tải model...")
        mLoadingDialog?.show()

        VirtualModelUtil.modelDownload(mContext, mModelUrl, object :
            VirtualModelUtil.ModelDownloadCallback {
            override fun onDownloadProgress(url: String?, current: Long, total: Long) {
                val progress = (current * 100 / total).toInt()
                if (progress != mLastProgress) {
                    mLastProgress = progress
                    runOnUiThread {
                        if (mLoadingDialog?.isShowing == true) {
                            mLoadingDialog?.setContent("Model tải (${progress}%)")
                        }
                    }
                }
            }

            override fun onUnzipProgress(url: String?, current: Long, total: Long) {
                val progress = (current * 100 / total).toInt()
                if (progress != mLastProgress) {
                    mLastProgress = progress
                    runOnUiThread {
                        if (mLoadingDialog?.isShowing == true) {
                            mLoadingDialog?.setContent("Model giải nén (${progress}%)")
                        }
                    }
                }
            }

            override fun onDownloadComplete(url: String?, dir: File?) {
                runOnUiThread {
                    mLoadingDialog?.dismiss()
                    jumpPlayPage()
                }
            }

            override fun onDownloadFail(url: String?, code: Int, msg: String?) {
                runOnUiThread {
                    mLoadingDialog?.dismiss()
                    Toast.makeText(mContext, "Lỗi tải model: $msg", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }
}
