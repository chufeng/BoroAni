package com.xyoye.dandanplay.app

import android.content.Context
import androidx.multidex.MultiDex
import com.xyoye.common_component.base.app.BaseApplication
import com.xyoye.dandanplay.ui.main.TvDesign

/**
 * Created by xyoye on 2020/7/27.
 */

class IApplication : BaseApplication(){

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    override fun onLowMemory() {
        TvDesign.releaseImageMemory("system_low_memory")
        super.onLowMemory()
    }

    override fun onTrimMemory(level: Int) {
        if (level >= TRIM_MEMORY_RUNNING_LOW) TvDesign.releaseImageMemory("system_trim_$level")
        super.onTrimMemory(level)
    }
}
