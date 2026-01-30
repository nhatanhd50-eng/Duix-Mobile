package ai.guiji.duix.test.ui.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.TextUtils
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
import java.io.File

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private var mLoadingDialog: LoadingDialog? = null
    private var mLastProgress = 0

    // --- BIẾN CỦA GIAO DIỆN CHAT ---
    private var etChat: EditText? = null
    private var btnSend: Button? = null
    private var chatContainer: LinearLayout? = null
    
    // Lưu ý: Biến duix cần được gán từ CallActivity hoặc tạo ở đây để có thể nói chuyện
    // Ở MainActivity này duix = null, nên nhân vật sẽ chưa nói được
    private var duix: DUIX? = null 

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

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvSdkVersion.text = "SDK Version: ${BuildConfig.VERSION_NAME}"

        // --- GÁN LOGIC CŨ (DOWNLOAD & PLAY) ---
        binding.btnMoreModel.setOnClickListener {
            val modelSelectorDialog = ModelSelectorDialog(mContext, models, object : ModelSelectorDialog.Listener{
                override fun onSelect(url: String) {
                    binding.etUrl.setText(url)
                }
            })
            modelSelectorDialog.show()
        }
        binding.btnPlay.setOnClickListener {
            play()
        }

        // --- THÊM LOGIC GIAO DIỆN CHAT MỚI ---
        setupChatUI()
    }

    /**
     * HÀM TẠO GIAO DIỆN CHAT PROGRAMMATICALLY
     * Thêm vào dưới cùng của màn hình hiện tại
     */
    private fun setupChatUI() {
        // Kiểm tra xem binding.root có phải là LinearLayout không để thêm view được
        if (binding.root !is ViewGroup) return

        val rootGroup = binding.root as ViewGroup
        
        // 1. Tạo khung Chat (ScrollView chứa danh sách tin nhắn)
        val scrollView = ScrollView(mContext)
        scrollView.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f
        )
        scrollView.backgroundColor = Color.TRANSPARENT
        
        chatContainer = LinearLayout(mContext)
        chatContainer!!.orientation = LinearLayout.VERTICAL
        chatContainer!!.setPadding(20, 20, 20, 20)
        chatContainer!!.gravity = Gravity.BOTTOM
        
        scrollView.addView(chatContainer)
        rootGroup.addView(scrollView, rootGroup.childCount) // Thêm vào vị trí cuối cùng

        // 2. Tạo khung Input (Ô nhập + Nút gửi)
        val inputLayout = LinearLayout(mContext)
        inputLayout.orientation = LinearLayout.HORIZONTAL
        inputLayout.gravity = Gravity.CENTER_VERTICAL
        inputLayout.setBackgroundColor(Color.parseColor("#2C2C2C"))
        inputLayout.setPadding(15, 15, 15, 15)
        
        val inputParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        rootGroup.addView(inputLayout, rootGroup.childCount) // Thêm vào cuối cùng

        // Ô nhập liệu
        etChat = EditText(mContext)
        etChat!!.hint = "Nhập tin nhắn..."
        etChat!!.backgroundColor = Color.TRANSPARENT
        etChat!!.setTextColor(Color.WHITE)
        etChat!!.setHintTextColor(Color.GRAY)
        etChat!!.setPadding(10, 10, 10, 10)
        
        val editParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
        editParams.setMargins(0, 0, 10, 0)
        inputLayout.addView(etChat, editParams)

        // Nút gửi
        btnSend = Button(mContext)
        btnSend!!.text = "Gửi"
        btnSend!!.setBackgroundColor(Color.parseColor("#00BCD4"))
        btnSend!!.setTextColor(Color.WHITE)
        btnSend!!.setPadding(20, 10, 20, 10)
        
        val btnParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        inputLayout.addView(btnSend, btnParams)

        // 3. Xử lý sự kiện bấm nút gửi
        btnSend!!.setOnClickListener {
            sendMessage()
        }
    }

    /**
     * HÀM GỬI TIN NHẮN
     */
    private fun sendMessage() {
        val text = etChat?.text.toString().trim()
        
        if (text.isNullOrEmpty()) {
            Toast.makeText(mContext, "Vui lòng nhập tin nhắn", Toast.LENGTH_SHORT).show()
            return
        }

        // 1. Hiển thị tin nhắn của User
        addChatMessage(text, true)

        // 2. Gọi AI (Nếu duix đã được khởi tạo ở đâu đó)
        duix?.askAndSpeak(text)

        // 3. Hiển thị tin nhắn của AI (Mô phỏng - Bạn cần thêm callback vào DUIX.java để gọi dòng này)
        // addChatMessage(aiResponse, false)

        // Xóa ô nhập
        etChat?.text?.clear()
    }

    /**
     * HÀM THÊM TIN NHẮN VÀO LIST
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
        msgBubble.setTextSize(15f)
        msgBubble.setTextColor(Color.WHITE)
        msgBubble.setPadding(20, 15, 20, 15)
        
        if (isMine) {
            msgBubble.setBackgroundColor(Color.parseColor("#00BCD4"))
            val params = msgWrapper.layoutParams as LinearLayout.LayoutParams
            params.rightMargin = 50
            msgWrapper.layoutParams = params
        } else {
            msgBubble.setBackgroundColor(Color.parseColor("#3E3E3E"))
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

    private fun play(){
        mBaseConfigUrl = binding.etBaseConfig.text.toString()
        mModelUrl = binding.etUrl.text.toString()
        if (TextUtils.isEmpty(mBaseConfigUrl)){
            Toast.makeText(mContext, R.string.base_config_cannot_be_empty, Toast.LENGTH_SHORT).show()
            return
        }
        if (TextUtils.isEmpty(mModelUrl)){
            Toast.makeText(mContext, R.string.model_url_cannot_be_empty, Toast.LENGTH_SHORT).show()
            return
        }
        checkBaseConfig()
    }

    private fun checkBaseConfig(){
        if (VirtualModelUtil.checkBaseConfig(mContext)){
            checkModel()
        } else {
            baseConfigDownload()
        }
    }

    private fun checkModel(){
        if (VirtualModelUtil.checkModel(mContext, mModelUrl)){
            jumpPlayPage()
        } else {
            modelDownload()
        }
    }

    private fun jumpPlayPage(){
        val intent = Intent(mContext, CallActivity::class.java)
        intent.putExtra("modelUrl", mModelUrl)
        val debug = binding.switchDebug.isChecked
        intent.putExtra("debug", debug)
        startActivity(intent)
    }

    private fun baseConfigDownload(){
        mLoadingDialog?.dismiss()
        mLoadingDialog = LoadingDialog(mContext, "Start downloading")
        mLoadingDialog?.show()
        VirtualModelUtil.baseConfigDownload(mContext, mBaseConfigUrl, object :
            VirtualModelUtil.ModelDownloadCallback {
            override fun onDownloadProgress(url: String?, current: Long, total: Long) {
                val progress = (current * 100 / total).toInt()
                if (progress != mLastProgress){
                    mLastProgress = progress
                    runOnUiThread {
                        if (mLoadingDialog?.isShowing == true){
                            mLoadingDialog?.setContent("Config download(${progress}%)")
                        }
                    }
                }
            }

            override fun onUnzipProgress(url: String?, current: Long, total: Long) {
                val progress = (current * 100 / total).toInt()
                if (progress != mLastProgress){
                    mLastProgress = progress
                    runOnUiThread {
                        if (mLoadingDialog?.isShowing == true){
                            mLoadingDialog?.setContent("Config unzip(${progress}%)")
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
                    Toast.makeText(mContext, "BaseConfig download error: $msg", Toast.LENGTH_SHORT).show()
                }
            }

        })
    }

    private fun modelDownload(){
        mLoadingDialog?.dismiss()
        mLoadingDialog = LoadingDialog(mContext, "Start downloading")
        mLoadingDialog?.show()
        VirtualModelUtil.modelDownload(mContext, mModelUrl, object : VirtualModelUtil.ModelDownloadCallback{
            override fun onDownloadProgress(
                url: String?,
                current: Long,
                total: Long,
            ) {
                val progress = (current * 100 / total).toInt()
                if (progress != mLastProgress){
                    mLastProgress = progress
                    runOnUiThread {
                        if (mLoadingDialog?.isShowing == true){
                            mLoadingDialog?.setContent("Model download(${progress}%)")
                        }
                    }
                }
            }

            override fun onUnzipProgress(
                url: String?,
                current: Long,
                total: Long,
            ) {
                val progress = (current * 100 / total).toInt()
                if (progress != mLastProgress){
                    mLastProgress = progress
                    runOnUiThread {
                        if (mLoadingDialog?.isShowing == true){
                            mLoadingDialog?.setContent("Model unzip(${progress}%)")
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

            override fun onDownloadFail(
                url: String?,
                code: Int,
                msg: String?,
            ) {
                runOnUiThread {
                    mLoadingDialog?.dismiss()
                    Toast.makeText(mContext, "Model download error: $msg", Toast.LENGTH_SHORT).show()
                }
            }

        })
    }
}
