package me.ppting.plugin.tasks

import com.android.build.gradle.api.ApplicationVariant
import com.android.build.gradle.internal.res.LinkApplicationAndroidResourcesTask
import me.ppting.plugin.config.REPEAT_CONFIG_NAME
import me.ppting.plugin.config.REPEAT_MAPPING_TEXT_FILE_NAME
import me.ppting.plugin.config.RepeatConfig
import me.ppting.plugin.utils.FileUtils
import me.ppting.plugin.utils.ZipUtils
import me.ppting.plugin.utils.setString
import me.ppting.plugin.utils.zip
import org.gradle.api.Project
import pink.madis.apk.arsc.ResourceFile
import pink.madis.apk.arsc.ResourceTableChunk
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Created by PPTing on 2022/3/25.
 * Description: 去除重复的资源文件
 */

class RemoveRepeatTask : ITask{

    companion object {
        private const val TAG = "RemoveRepeatTask"
        private const val ARSC_FILE = "resources.arsc"
    }

    override fun call(project: Project, applicationVariant: ApplicationVariant) {
        val repeatConfig = project.extensions.getByName(REPEAT_CONFIG_NAME) as RepeatConfig

        log("single applicationVariant is ${applicationVariant.name.capitalize()}")
        val variantName = applicationVariant.name.capitalize()
        if (variantName.toLowerCase().contains("debug") && !repeatConfig.debugEnable){
            //检查配置不允许 debug 使用，则跳过
            return
        }
        val processResource = project.tasks.getByName("process${variantName}Resources")
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
                    dealWithSuffixApFile(it,repeatConfig,project)
                }
        }
    }


    /**
     * 对 resources-${Variant}.ap_ 文件进行处理
     * @param apFile 未解压的 resources-${Variant}.ap_ 文件
     * @param repeatConfig 配置文件
     */
    private fun dealWithSuffixApFile(apFile: File, repeatConfig: RepeatConfig, project: Project) {
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
        val unZipDirName = apFile.name.replace(".ap_", "")
        val apFileParent = File(apFile.parent)
        val unZipDirPath = "${apFileParent.parent}${File.separator}${unZipDirName}"
        ZipUtils.unZipIt(apFile.absolutePath, unZipDirPath)
        //2. 获取到解压出来的 resources.arsc 文件
        val unZippedResourcesDotArscFile = File(unZipDirPath, ARSC_FILE)

        //3. 将 resources.arsc 文件中的 chunks 拿到，并筛选出资源文件部分的 chunks
        val newResourceArscFileStream = FileInputStream(unZippedResourcesDotArscFile).use { fileInputStream ->
            val resourcesArscFileStream = ResourceFile.fromInputStream(fileInputStream)

            val fileWriter = if (repeatConfig.enableReportMapping){
                if (repeatConfig.mappingFilePath.isNullOrEmpty()){
                    FileWriter("${project.buildDir}${File.separator}${REPEAT_MAPPING_TEXT_FILE_NAME}")
                } else {
                    FileWriter("${repeatConfig.mappingFilePath}${File.separator}${REPEAT_MAPPING_TEXT_FILE_NAME}",true)
                }
            } else {
                null
            }
            repeatResCaches
                .filter { it.value.size > 1 }
                .forEach { key, repeatResZipEntries ->
                    //获取到第一个，后序其他的都要重定向到这个资源文件上来
                    //1. 过滤白名单
                    val repeatRes = repeatResZipEntries.filter {
                        val fileName = it.name.split("/").last()
                        !repeatConfig.ignoreList.contains(fileName)
                    }
                    if (repeatRes.size <= 1){
                        //如果过滤掉白名单后只剩下一个，则退出，进入下一个循环
                        return@forEach
                    }
                    val firstZipEntry = repeatRes[0]
                    val otherResZipEntries = repeatRes.subList(1, repeatRes.size)
                    fileWriter?.write("${firstZipEntry.name} -> ${firstZipEntry.name}\n")
                    otherResZipEntries.forEach { zipEntry ->
                        log("删除重复文件 : ${unZipDirPath}${File.separator}${zipEntry.name}")
                        log("删除重复文件 : name is ${zipEntry.name}")
                        //2. 记录日志
                        fileWriter?.write("${zipEntry.name} -> ${firstZipEntry.name}\n")
                        //3. 删除重复的文件
                        File("${unZipDirPath}${File.separator}${zipEntry.name}").delete()
                        //4. 修改重定向

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
            fileWriter?.close()
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