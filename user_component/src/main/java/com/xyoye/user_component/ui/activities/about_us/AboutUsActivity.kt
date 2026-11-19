package com.xyoye.user_component.ui.activities.about_us

import android.annotation.SuppressLint
import com.alibaba.android.arouter.facade.annotation.Route
import com.xyoye.common_component.base.BaseActivity
import com.xyoye.common_component.config.RouteTable
import com.xyoye.common_component.extension.startUrlActivity
import com.xyoye.common_component.utils.AppUtils
import com.xyoye.user_component.BR
import com.xyoye.user_component.BuildConfig
import com.xyoye.user_component.R
import com.xyoye.user_component.databinding.ActivityAboutUsBinding

@Route(path = RouteTable.User.AboutUs)
class AboutUsActivity : BaseActivity<AboutUsViewModel, ActivityAboutUsBinding>() {

    override fun initViewModel() =
        ViewModelInit(
            BR.viewModel,
            AboutUsViewModel::class.java
        )

    override fun getLayoutId() = R.layout.activity_about_us

    @SuppressLint("SetTextI18n")
    override fun initView() {

        title = ""

        val describe = "《老爷机追番》是一款为旧设备保留的轻量追番应用，支持番剧发现、来源搜索、选集播放与本地媒体播放。" +
                "\n\nBoroAni 是一个免费、开源 Android 项目。项目仅用于个人学习、研究，不参与任何商业行为。"

        dataBinding.appDescribeTv.text = describe

        dataBinding.versionTv.text = "v${AppUtils.getVersionName()}  ${BuildConfig.BUILD_COMMIT}"

        dataBinding.officialAddressTv.setOnClickListener {
            startUrlActivity("https://github.com/chufeng/BoroAni")
        }

        dataBinding.sourceAddressTv.setOnClickListener {
            startUrlActivity("https://github.com/chufeng/BoroAni")
        }
    }
}
