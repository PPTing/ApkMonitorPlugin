package me.ppting.plugin.tasks

import com.android.build.gradle.api.ApplicationVariant
import com.android.build.gradle.internal.res.LinkApplicationAndroidResourcesTask
import me.ppting.plugin.config.*
import me.ppting.plugin.model.RepeatTaskResult
import me.ppting.plugin.utils.FileUtils
import me.ppting.plugin.utils.ZipUtils
import me.ppting.plugin.utils.setString
import me.ppting.plugin.utils.zip
import org.gradle.api.Project
import pink.madis.apk.arsc.ResourceFile
import pink.madis.apk.arsc.ResourceTableChunk
import java.io.*
import java.text.CharacterIterator
import java.text.StringCharacterIterator
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Created by PPTing on 2022/3/25.
 * Description: 去除重复的资源文件
 */

class RemoveRepeatTask : ITask {

    companion object {
        private const val TAG = "RemoveRepeatTask"
        private const val ARSC_FILE = "resources.arsc"
    }

    override fun createConfig(project: Project) {
        project.extensions.create(REPEAT_CONFIG_NAME, RepeatConfig::class.java)
        project.extensions.create(COMPRESS_CONFIG_NAME, CompressConfig::class.java)
    }

    override fun call(project: Project, applicationVariant: ApplicationVariant) {
        val repeatConfig = project.extensions.getByName(REPEAT_CONFIG_NAME) as RepeatConfig
        val compressConfig = project.extensions.getByName(COMPRESS_CONFIG_NAME) as CompressConfig

        log("single applicationVariant is ${applicationVariant.name.capitalize()}")
        val variantName = applicationVariant.name.capitalize()
        if (variantName.toLowerCase().contains("debug") && !repeatConfig.debugEnable) {
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
                .firstOrNull()?.let { _apFile ->
                    dealWithSuffixApFile(_apFile, repeatConfig, compressConfig, project)
                }
        }
    }


    /**
     * 对 resources-${Variant}.ap_ 文件进行处理
     * @param apFile 未解压的 resources-${Variant}.ap_ 文件
     * @param repeatConfig 配置文件
     */
    private fun dealWithSuffixApFile(apFile: File, repeatConfig: RepeatConfig, compressConfig: CompressConfig, project: Project) {

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


            //region 去重
            val repeatResCaches = deleteRepeatRes(project, apFile, repeatConfig, unZipDirPath, resourcesArscFileStream)
            //去重后，将文件压缩
            compressPng(project, File(unZipDirPath), compressConfig, resourcesArscFileStream, repeatResCaches)

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
            it.zip(unZipDirPath, File(unZipDirPath), setOf(ARSC_FILE))
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

    private fun deleteRepeatRes(
        project: Project,
        apFile: File,
        repeatConfig: RepeatConfig,
        unZipDirPath: String,
        resourcesArscFileStream: ResourceFile
    ): RepeatTaskResult {
        //罗列出重复的文件
        //用来存储文件，key 是 目录名 + # + crc
        //value 是 key 相同的文件列表
        val repeatResCaches = hashMapOf<String, MutableList<ZipEntry>>()
        ZipFile(apFile).entries()
            .iterator()
            .forEach {
                val file = File(it.name)
                val key = "${file.parent}#${it.crc}"
                System.out.println("插入缓存的 key ${it.name} value ${it.crc}")
                val list = repeatResCaches.getOrDefault(key, mutableListOf())
                list.add(it)
                repeatResCaches.put(key, list)
            }
        val fileWriter = if (repeatConfig.enableReportMapping) {
            if (repeatConfig.mappingFilePath.isNullOrEmpty()) {
                FileWriter("${project.buildDir}${File.separator}${REPEAT_MAPPING_TEXT_FILE_NAME}")
            } else {
                FileWriter("${repeatConfig.mappingFilePath}${File.separator}${REPEAT_MAPPING_TEXT_FILE_NAME}", true)
            }
        } else {
            null
        }
        var deleteRepeatNumbers = 0L//记录删除的重复文件数量
        var deleteRepeatFileSize = 0L//记录删除的重复文件的大小
        val repeatResNeedToSolve = repeatResCaches
            .filter { it.value.size > 1 }

        val repeatSizeMap = hashMapOf<String, Int>()
        repeatResNeedToSolve.forEach { _, repeatResZipEntries ->
            //1. 过滤白名单
            val repeatRes = repeatResZipEntries.filter {
                val fileName = it.name.split("/").last()
                !repeatConfig.ignoreList.contains(fileName)
            }
            if (repeatRes.size <= 1) {
                //如果过滤掉白名单后只剩下一个，则退出，进入下一个循环
                return@forEach
            }

            //获取到第一个，后序其他的都要重定向到这个资源文件上来
            val firstZipEntry = repeatRes[0]

            val otherResZipEntries = repeatRes.subList(1, repeatRes.size)
            log("repeatSizeMap.put firstZipEntry.name：${firstZipEntry.name}")
            log("repeatSizeMap.put size ：${repeatRes.size}")
            repeatSizeMap.put(firstZipEntry.name, repeatRes.size)
            fileWriter?.write("${firstZipEntry.name} <--- ${firstZipEntry.name}\n ")
            deleteRepeatNumbers += otherResZipEntries.size
            otherResZipEntries.forEach { zipEntry ->
                log("删除重复文件 : ${unZipDirPath}${File.separator}${zipEntry.name}")
                log("删除重复文件 : name is ${zipEntry.name}")
                //2. 记录日志
                fileWriter?.write("${getSpaceByLength(firstZipEntry.name.length)}<--- ${zipEntry.name}\n")
                //3. 删除重复的文件
                File("${unZipDirPath}${File.separator}${zipEntry.name}").delete()
                //4. 修改重定向
                deleteRepeatFileSize += zipEntry.size
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
            fileWriter?.write("----------------------------------------------\n")
        }
        //endregion
        fileWriter?.write("删除的文件数量为:${deleteRepeatNumbers}\n")
        fileWriter?.write("删除的文件大小为:${humanReadableByteCountBin(deleteRepeatFileSize)}\n")
        fileWriter?.close()
        return RepeatTaskResult(repeatSizeMap)
    }

    private val spaceCacheMap by lazy { mutableMapOf<Int, String>() }

    private fun getSpaceByLength(length: Int): String {
        return spaceCacheMap.getOrPut(length) {
            (" ").repeat(length)
        }
    }

    private fun log(message: String) {
        System.out.println("${TAG} $message")
    }

    private fun humanReadableByteCountBin(bytes: Long): String? {
        val absB = if (bytes == Long.MIN_VALUE) Long.MAX_VALUE else Math.abs(bytes)
        if (absB < 1024) {
            return "$bytes B"
        }
        var value = absB
        val ci: CharacterIterator = StringCharacterIterator("KMGTPE")
        var i = 40
        while (i >= 0 && absB > 0xfffccccccccccccL shr i) {
            value = value shr 10
            ci.next()
            i -= 10
        }
        value *= java.lang.Long.signum(bytes).toLong()
        return String.format("%.1f %ciB", value / 1024.0, ci.current())
    }

    /**
     * 去重后对 png 进行压缩处理
     * 并且需要修改 arsc 中对应的资源文件名
     */
    private fun compressPng(
        project: Project,
        rawFileDir: File,
        compressConfig: CompressConfig,
        resourcesArscFileStream: ResourceFile,
        repeatTaskResult: RepeatTaskResult
    ) {
        System.out.println("开始执行 coverToWebPTask")
        System.out.println("rawFileDir path is ${rawFileDir.absolutePath}")
        val files = arrayListOf<File>()
        val cachePath = mutableSetOf<String>()
        addImageFiles(rawFileDir, files, cachePath)


        var reduceSize = 0L//减少的体积
        val pngFile = files.filter {
            it.name.endsWith(".png") && !it.name.endsWith(".9.png") && !it.path.contains("mipmap")
        }
        val fileWriter = if (compressConfig.enableReportMapping) {
            if (compressConfig.mappingFilePath.isNullOrEmpty()) {
                FileWriter("${project.buildDir}${File.separator}${COMPRESS_MAPPING_TEXT_FILE_NAME}")
            } else {
                FileWriter("${compressConfig.mappingFilePath}${File.separator}${COMPRESS_MAPPING_TEXT_FILE_NAME}", true)
            }
        } else {
            null
        }
        pngFile.forEach { originFile ->
            val webPFile = File(originFile.parent, originFile.name.replace(".png", ".webp"))
            val cmd = "cwebp -q 80 -m 6 -lossless ${originFile.absolutePath} -o ${webPFile.absolutePath}"

            System.out.println("cmd ${cmd}")
            System.out.println("需要被压缩的 png 文件路径为 ${originFile.absolutePath}")
            executeCmd(cmd)
            if (webPFile.exists() && webPFile.length() < originFile.length()) {
                System.out.println("转 webp ${webPFile.absolutePath}")

                //压缩前的文件名
                val originFileName = "${originFile.parentFile.parentFile.name}${File.separator}${originFile.parentFile.name}${File.separator}${originFile.name}"

                //压缩后的文件名
                val compressFileName = webPFile.absolutePath.replace("${rawFileDir.absolutePath}${File.separator}", "")

                fileWriter?.write("${originFileName} -> ${compressFileName}\n")
                //region 修改 arsc 中的映射
                resourcesArscFileStream
                    .chunks
                    .asSequence()
                    .filterIsInstance<ResourceTableChunk>()
                    .forEach { resourceTableChunk ->
                        //如果被压缩的图片包含去重的列表中，则还要修改其他的图片
                        val index = resourceTableChunk.stringPool.indexOf(originFileName)
                        if (index != -1) {
                            resourceTableChunk.stringPool.setString(index, compressFileName)
                        }
                        val repeatFileSize = repeatTaskResult.repeatSize.get(originFileName)?:0
                        for (i in 0 until repeatFileSize){
                            //因为前面已经替换过一次了，这里减一，只需要替换那些重复的资源(不包括第一张)
                            val otherIndex = resourceTableChunk.stringPool.indexOf(originFileName)
                            if (otherIndex != -1) {
                                resourceTableChunk.stringPool.setString(otherIndex, compressFileName)
                            }
                        }

                    }
                //endregion
                reduceSize += (originFile.length() - webPFile.length())
                originFile.delete()
            } else {
                webPFile.delete()
                System.out.println("删除文件 ${webPFile.absolutePath}")
            }
        }
        fileWriter?.write("压缩减小的体积为 ${humanReadableByteCountBin(reduceSize)}\n")
        fileWriter?.close()

    }

    private fun addImageFiles(dir: File, files: ArrayList<File>, cachePath: MutableSet<String>) {
        if (cachePath.contains(dir.absolutePath)) {
            return
        }
        cachePath.add(dir.absolutePath)
        if (dir.isDirectory) {
            dir.listFiles()?.forEach { addImageFiles(it, files, cachePath) }
        } else {
            files.add(dir)
        }
    }

    private fun executeCmd(cmd: String): String? {
        val process = Runtime.getRuntime().exec(cmd)
        process.waitFor()
        val bufferReader = BufferedReader(InputStreamReader(process.inputStream))
        return try {
            bufferReader.readLine()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}