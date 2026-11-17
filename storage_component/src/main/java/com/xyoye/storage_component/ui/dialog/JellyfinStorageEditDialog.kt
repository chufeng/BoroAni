package com.xyoye.storage_component.ui.dialog

import com.xyoye.common_component.extension.setTextColorRes
import com.xyoye.common_component.weight.ToastCenter
import com.xyoye.data_component.entity.MediaLibraryEntity
import com.xyoye.data_component.enums.MediaType
import com.xyoye.storage_component.R
import com.xyoye.storage_component.databinding.DialogJellyfinLoginBinding
import com.xyoye.storage_component.ui.activities.storage_plus.StoragePlusActivity

/**
 * Jellyfin 媒体库编辑弹窗
 */

class JellyfinStorageEditDialog(
    private val activity: StoragePlusActivity,
    private val library: MediaLibraryEntity?
) : StorageEditDialog<DialogJellyfinLoginBinding>(activity) {

    private lateinit var binding: DialogJellyfinLoginBinding

    override fun getChildLayoutId() = R.layout.dialog_jellyfin_login

    override fun initView(binding: DialogJellyfinLoginBinding) {
        this.binding = binding
        val isEditMode = library != null

        setTitle(if (isEditMode) "编辑Jellyfin媒体库" else "添加Jellyfin媒体库")

        val editLibrary = library ?: MediaLibraryEntity(
            0,
            "",
            "",
            MediaType.JELLYFIN_SERVER
        )
        binding.library = editLibrary

        binding.serverTestConnectTv.setOnClickListener {
            if (checkParams(editLibrary)) {
                activity.testStorage(editLibrary)
            }
        }

        setPositiveListener {
            if (checkParams(editLibrary)) {
                if (editLibrary.displayName.isEmpty()) {
                    editLibrary.displayName = "Jellyfin媒体库"
                }
                editLibrary.describe = editLibrary.url
                activity.addStorage(editLibrary)
            }
        }

        setNegativeListener {
            activity.finish()
        }
    }

    override fun onTestResult(result: Boolean) {
        if (result) {
            binding.serverStatusTv.text = "连接成功"
            binding.serverStatusTv.setTextColorRes(R.color.text_blue)
        } else {
            binding.serverStatusTv.text = "连接失败"
            binding.serverStatusTv.setTextColorRes(R.color.text_red)
        }
    }

    private fun checkParams(serverData: MediaLibraryEntity): Boolean {
        if (serverData.url.isEmpty()) {
            ToastCenter.showWarning("请填写服务器地址")
            return false
        }

        val serverUrl = serverData.url.trim()
        if (!serverUrl.startsWith("http://") && !serverUrl.startsWith("https://")) {
            ToastCenter.showWarning("请填写服务器协议：http或https")
            return false
        }

        // 统一去掉结尾斜杠，避免同一地址重复添加
        serverData.url = serverUrl.trimEnd('/')

        if (serverData.password.isNullOrEmpty()) {
            ToastCenter.showWarning("请填写API Key")
            return false
        }

        return true
    }
}
