package me.ppting.plugin.config

/**
 * Created by PPTing on 2022/3/26.
 * Description:
 */

const val CONFIG_NAME = "apkMonitorConfig"
const val REPEAT_CONFIG_NAME = "repeatConfig"
const val COMPRESS_CONFIG_NAME = "compressConfig"
const val REPEAT_MAPPING_TEXT_FILE_NAME = "repeatMapping.txt"
const val COMPRESS_MAPPING_TEXT_FILE_NAME = "compressMapping.txt"

open class Config : BaseConfig() {

}

open class RepeatConfig : BaseConfig() {
    var enableReportMapping : Boolean = true
    var mappingFilePath : String = ""
}

open class CompressConfig : BaseConfig() {
    var enableReportMapping : Boolean = true
    var mappingFilePath : String = ""
}

open class BaseConfig {
    var enable: Boolean = true
    var debugEnable: Boolean = true
    var ignoreList: List<String> = emptyList()
}