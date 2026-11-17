package com.xyoye.storage_component.ui.dialog

import android.net.Uri
import com.xyoye.common_component.extension.setTextColorRes
import com.xyoye.common_component.weight.ToastCenter
import com.xyoye.data_component.entity.MediaLibraryEntity
import com.xyoye.data_component.enums.MediaType
import com.xyoye.storage_component.R
import com.xyoye.storage_component.databinding.DialogCmsSourceBinding
import com.xyoye.storage_component.ui.activities.storage_plus.StoragePlusActivity

/**
 * 在线番剧源（苹果CMS 采集接口）编辑弹窗
 */

class CmsSourceEditDialog(
    private val activity: StoragePlusActivity,
    private val library: MediaLibraryEntity?
) : StorageEditDialog<DialogCmsSourceBinding>(activity) {

    private lateinit var binding: DialogCmsSourceBinding

    override fun getChildLayoutId() = R.layout.dialog_cms_source

    override fun initView(binding: DialogCmsSourceBinding) {
        this.binding = binding
        val isEditMode = library != null

        setTitle(if (isEditMode) "编辑 CMS 在线源" else "添加 CMS 在线源")

        val editLibrary = library ?: MediaLibraryEntity(
            0,
            "",
            "",
            MediaType.CMS_SOURCE
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
                    editLibrary.displayName = Uri.parse(editLibrary.url).host ?: "在线番剧源"
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
            binding.serverStatusTv.text = "连接失败或接口无数据"
            binding.serverStatusTv.setTextColorRes(R.color.text_red)
        }
    }

    private fun checkParams(serverData: MediaLibraryEntity): Boolean {
        val apiUrl = serverData.url.trim()
        if (apiUrl.isEmpty()) {
            ToastCenter.showWarning("请填写采集接口地址")
            return false
        }
        if (!apiUrl.startsWith("http://") && !apiUrl.startsWith("https://")) {
            ToastCenter.showWarning("请填写协议：http或https")
            return false
        }
        serverData.url = apiUrl

        // 空字符串会让请求带上空白请求头，这里统一转成 null
        serverData.account = serverData.account?.trim()?.takeIf { it.isNotEmpty() }
        serverData.password = serverData.password?.trim()?.takeIf { it.isNotEmpty() }

        return true
    }
}
