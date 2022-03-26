package me.ppting.plugin

import me.ppting.plugin.tasks.RemoveRepeatTask
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Created by PPTing on 2022/3/24.
 * Description:
 */
class ApkMonitorPlugin : Plugin<Project> {
    companion object {
        private const val TAG = "ApkMonitorPlugin"
    }

    override fun apply(project: Project) {
        RemoveRepeatTask().call(project)
    }


}