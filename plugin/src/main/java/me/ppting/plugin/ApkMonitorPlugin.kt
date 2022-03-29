package me.ppting.plugin

import com.android.build.gradle.AppExtension
import com.android.build.gradle.api.ApplicationVariant
import me.ppting.plugin.config.CONFIG_NAME
import me.ppting.plugin.config.Config
import me.ppting.plugin.config.REPEAT_CONFIG_NAME
import me.ppting.plugin.config.RepeatConfig
import me.ppting.plugin.tasks.ITask
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

    private val tasks by lazy {
        listOf<ITask>(RemoveRepeatTask())
    }

    override fun apply(project: Project) {
        project.extensions.create(CONFIG_NAME,Config::class.java)
        tasks.forEach { it.createConfig(project) }


        project.afterEvaluate {
            val configGet = project.extensions.getByName(CONFIG_NAME) as Config
            val isApplicationModule = project.plugins.hasPlugin("com.android.application")
            val android = project.extensions.findByName("android")
            val isAndroid = android is AppExtension
            //检查配置项
            if (!configGet.enable || !isAndroid || !isApplicationModule){
                return@afterEvaluate
            }
            //检查 debug 是否打开

            (android as AppExtension).applicationVariants.forEach {
                val variantName = it.name.capitalize()
                if (variantName.toLowerCase().contains("debug")){
                    //debug 版本
                    if (configGet.debugEnable){
                        execute(project,it)
                    }
                } else {
                    //非 debug 版本
                    execute(project, it)
                }
            }
        }
    }

    private fun execute(project: Project, applicationVariant: ApplicationVariant){
        tasks.forEach { it.call(project,applicationVariant) }
    }

}