package me.ppting.plugin.config

import org.gradle.api.Action
import javax.inject.Inject

/**
 * Created by PPTing on 2022/3/26.
 * Description:
 */

const val CONFIG_NAME = "apkMonitorConfig"
const val REPEAT_CONFIG_NAME = "repeatConfig"

open class Config : BaseConfig() {

}

open class RepeatConfig : BaseConfig() {

}

open class BaseConfig {
    var enable: Boolean = true
    var debugEnable: Boolean = true
    var ignoreList: List<String> = emptyList()
}