package ai.guiji.duix.test.ui.activity

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.widget.Toast
import ai.guiji.duix.sdk.client.BuildConfig
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

    val models = arrayListOf(
        "https://github.com/duixcom/Duix-Mobile/releases/download/v1.0.0/bendi3_20240518.zip",
        "https://github.com/duixcom/Duix-Mobile/releases/download/v1.0.0/airuike_20240409.zip"
    )

    private var mBaseConfigUrl = ""
    private var mModelUrl = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvSdkVersion.text = "SDK Version: ${BuildConfig.VERSION_NAME}"

        binding.btnMoreModel.setOnClickListener {
            val modelSelectorDialog = ModelSelectorDialog(mContext, models, object : ModelSelectorDialog.Listener {
                override fun onSelect(url: String) { // Tên hàm đã sửa đúng
                    binding.etUrl.setText(url.trim())
                }
            })
            modelSelectorDialog.show()
        }

        binding.btnPlay.setOnClickListener { play() }
    }

    private fun play() {
        mBaseConfigUrl = binding.etBaseConfig.text.toString().trim()
        mModelUrl = binding.etUrl.text.toString().trim()
        if (TextUtils.isEmpty(mBaseConfigUrl) || TextUtils.isEmpty(mModelUrl)) {
            Toast.makeText(mContext, "Nhập URL model", Toast.LENGTH_SHORT).show()
            return
        }
        checkBaseConfig()
    }

    private fun checkBaseConfig() {
        if (VirtualModelUtil.checkBaseConfig(mContext)) checkModel() else baseConfigDownload()
    }

    private fun checkModel() {
        if (VirtualModelUtil.checkModel(mContext, mModelUrl)) jumpPlayPage() else modelDownload()
    }

    private fun jumpPlayPage() {
        val intent = Intent(mContext, CallActivity::class.java)
        intent.putExtra("modelUrl", mModelUrl)
        intent.putExtra("debug", binding.switchDebug.isChecked)
        startActivity(intent)
    }

    private fun baseConfigDownload() {
        mLoadingDialog = LoadingDialog(mContext, "Tải Config...").apply { show() }
        VirtualModelUtil.baseConfigDownload(mContext, mBaseConfigUrl, object : VirtualModelUtil.ModelDownloadCallback {
            override fun onDownloadProgress(url: String?, current: Long, total: Long) {
                runOnUiThread { if (mLoadingDialog?.isShowing == true) mLoadingDialog?.setContent("Config ${current * 100 / total}%") }
            }
            override fun onUnzipProgress(url: String?, current: Long, total: Long) {}
            override fun onDownloadComplete(url: String?, dir: File?) {
                runOnUiThread { mLoadingDialog?.dismiss(); checkModel() }
            }
            override fun onDownloadFail(url: String?, code: Int, msg: String?) {
                runOnUiThread { mLoadingDialog?.dismiss(); Toast.makeText(mContext, "Lỗi: $msg", Toast.LENGTH_SHORT).show() }
            }
        })
    }

    private fun modelDownload() {
        mLoadingDialog = LoadingDialog(mContext, "Tải Model...").apply { show() }
        VirtualModelUtil.modelDownload(mContext, mModelUrl, object : VirtualModelUtil.ModelDownloadCallback {
            override fun onDownloadProgress(url: String?, current: Long, total: Long) {
                runOnUiThread { if (mLoadingDialog?.isShowing == true) mLoadingDialog?.setContent("Model ${current * 100 / total}%") }
            }
            override fun onUnzipProgress(url: String?, current: Long, total: Long) {}
            override fun onDownloadComplete(url: String?, dir: File?) {
                runOnUiThread { mLoadingDialog?.dismiss(); jumpPlayPage() }
            }
            override fun onDownloadFail(url: String?, code: Int, msg: String?) {
                runOnUiThread { mLoadingDialog?.dismiss(); Toast.makeText(mContext, "Lỗi: $msg", Toast.LENGTH_SHORT).show() }
            }
        })
    }
}
