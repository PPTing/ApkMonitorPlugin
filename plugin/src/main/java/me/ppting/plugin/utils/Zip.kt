package me.ppting.plugin.utils

import java.io.File
import java.io.FileInputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Created by PPTing on 2022/3/26.
 * Description:
 */

fun ZipOutputStream.zip(srcRootDir: String, file: File) {

    //如果是文件，则直接压缩该文件
    if (file.isFile) {
        //获取文件相对于压缩文件夹根目录的子路径
        var subPath = file.absolutePath
        val index = subPath.indexOf(srcRootDir)
        if (index != -1) {
            subPath = subPath.substring(srcRootDir.length + File.separator.length)
        }
        val entry = ZipEntry(subPath).apply {
            //method = ZipEntry.STORED
            setLevel(ZipOutputStream.STORED)
            setMethod(ZipEntry.STORED);
            setCompressedSize(file.length());
            setSize(file.length());
            val crc = CRC32();
            //file.by
            crc.update(file.readBytes())//getFileBytes(srcFile));
            setCrc(crc.getValue());
        }
        putNextEntry(entry)

        FileInputStream(file).use {
            it.copyTo(this)
        }
        closeEntry()
    } else {
        //压缩目录中的文件或子目录
        val childFileList = file.listFiles()
        for (n in childFileList.indices) {
            childFileList[n].absolutePath.indexOf(file.absolutePath)
            zip(srcRootDir, childFileList[n])
        }
    }
    //如果是目录，则压缩整个目录

}