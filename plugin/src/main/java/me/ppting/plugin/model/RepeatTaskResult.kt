package me.ppting.plugin.model

/**
 * Created by PPTing on 2022/5/5.
 * Description:
 */

data class RepeatTaskResult(
    val repeatSize : Map<String,Int>//key 为去重后的文件名(res/xx/xx.yy)，value 为重复的文件数量
)