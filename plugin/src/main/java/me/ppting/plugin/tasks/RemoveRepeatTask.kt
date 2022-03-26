package me.ppting.plugin.tasks

import com.android.build.gradle.AppExtension
import com.android.build.gradle.internal.res.LinkApplicationAndroidResourcesTask
import me.ppting.plugin.utils.FileUtils
import me.ppting.plugin.utils.ZipUtils
import me.ppting.plugin.utils.setString
import me.ppting.plugin.utils.zip
import org.gradle.api.Project
import pink.madis.apk.arsc.ResourceFile
import pink.madis.apk.arsc.ResourceTableChunk
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Created by PPTing on 2022/3/25.
 * Description: 去除重复的资源文件
 */

class RemoveRepeatTask {

    companion object {
        private const val TAG = "RemoveRepeatTask"
    }

    fun call(project: Project) {


        project.afterEvaluate {
            val hasPlugin = project.plugins.hasPlugin("com.android.application")
            log("${TAG} hasPlugin $hasPlugin")
            if (hasPlugin) {
                val android = project.extensions.findByName("android")
                if (android is AppExtension) {
                    log("android is AppExtension")
//
                    log("applicationVariants is ${android.applicationVariants}")
                    android.applicationVariants.forEach {
                        log("single applicationVariant is ${it.name.capitalize()}")
                        val variantName = it.name.capitalize()
                        val processResource =
                            project.tasks.getByName("process${variantName}Resources")
                        log("processResource is ${processResource.name}")
                        processResource.doLast {
                            log("processResource doLast")
                            val resourcesTask = it as LinkApplicationAndroidResourcesTask
                            val resPackageOutputFolder = resourcesTask.resPackageOutputFolder
                            log("resPackageOutputFolder is ${resPackageOutputFolder.asFileTree.files}")


                            resPackageOutputFolder.asFileTree
                                .files
                                .filter { it.name.endsWith(".ap_") }
                                .firstOrNull()?.let {
                                    dealWithSuffixApFile(it)
                                }
                        }
                    }


                }
            }
        }
    }


    /**
     * 对 resources-${Variant}.ap_ 文件进行处理
     */
    private fun dealWithSuffixApFile(apFile: File) {
        //用来存储文件，key 是 目录名 + # + crc
        //value 是 key 相同的文件列表
        val repeatResCaches = hashMapOf<String, MutableList<ZipEntry>>()
        ZipFile(apFile).entries()
            .iterator()
            .forEach {
                val key = "${File(it.name).parent}#${it.crc}"
                val list = repeatResCaches.getOrDefault(key, mutableListOf())
                list.add(it)
                repeatResCaches.put(key, list)
            }

        //1. 将 *.ap_ 文件解压到当前目录下的同名目录中
        // processed_res/debug/out/resources-debug
        // processed_res/debug/out/resources-debug/AndroidManifest.xml
        // processed_res/debug/out/resources-debug/resources.arsc
        // processed_res/debug/out/resources-debug/res/xxx
        // processed_res/debug/out/resources-debug/res/...
        //                        /resources-debug.ap_
        //                        /output-metadata.json
        val unZipDirName = "Temp"//apFile.name.replace(".ap_", "")

        val unZipDirPath = "${File(apFile.parent).parent}${File.separator}${unZipDirName}"
        ZipUtils.unZipIt(apFile.absolutePath, unZipDirPath)
        //2. 获取到解压出来的 resources.arsc 文件
        val unZippedResourcesDotArscFile = File(unZipDirPath, "resources.arsc")

        //3. 将 resources.arsc 文件中的 chunks 拿到，并筛选出资源文件部分的 chunks
        val newResourceArscFileStream = FileInputStream(unZippedResourcesDotArscFile).use { fileInputStream ->
            val resourcesArscFileStream = ResourceFile.fromInputStream(fileInputStream)

            repeatResCaches
                .filter { it.value.size > 1 }
                .forEach { key, repeatResZipEntries ->
                    //获取到第一个，后序其他的都要重定向到这个资源文件上来
                    val firstZipEntry = repeatResZipEntries[0]
                    val otherResZipEntries =
                        repeatResZipEntries.subList(1, repeatResZipEntries.size)
                    otherResZipEntries.forEach { zipEntry ->
                        log("删除重复文件 : ${unZipDirPath}${File.separator}${zipEntry.name}")
                        //1. 删除重复的文件
                        File("${unZipDirPath}${File.separator}${zipEntry.name}").delete()
                        //2. 修改重定向

                        resourcesArscFileStream
                            .chunks
                            .asSequence()
                            .filterIsInstance<ResourceTableChunk>()
                            .forEach {
                                val index = it.stringPool.indexOf(zipEntry.name)
                                if (index != -1) {
                                    it.stringPool.setString(index, firstZipEntry.name)
                                }
                            }
                    }

                }

            return@use resourcesArscFileStream
        }

        //4. 删除旧的 resources.arsc
        unZippedResourcesDotArscFile.delete()
        //4. 生成新的 resources.arsc
        FileOutputStream(unZippedResourcesDotArscFile).use {
            it.write(newResourceArscFileStream.toByteArray())
        }
        //5. 删除旧的 resource-xx.ap_
        //5. 打包中间产物生成新的 .ap_
        log("将 $unZipDirPath 压缩为 ${apFile.absolutePath}")
        //ZipUtils.toZip(unZipDirPath, apFile.absolutePath,true)
        ZipOutputStream(apFile.outputStream()).use {
            it.zip(unZipDirPath, File(unZipDirPath))
        }
        log("新的 ${apFile.name}压缩完毕")
        //6. 删除中间产物
        val deleteResult = FileUtils.deleteFile(File(unZipDirPath))
        if (deleteResult) {
            log("成功删除中间产物")
        } else {
            log("删除中间产物出错了！！！")
        }

    }

    private fun log(message: String) {
        System.out.println("${TAG} $message")
    }
}