package me.ppting.plugin.tasks

import com.android.build.gradle.api.ApplicationVariant
import org.gradle.api.Project

/**
 * Created by PPTing on 2022/3/27.
 * Description:
 */

interface ITask {

    fun createConfig(project: Project)

    fun call(project: Project, applicationVariant: ApplicationVariant)
}