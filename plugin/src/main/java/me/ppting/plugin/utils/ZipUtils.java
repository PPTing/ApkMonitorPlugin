package me.ppting.plugin.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * @author baronzhang (baron[dot]zhanglei[at]gmail[dot]com)
 *         16/4/5
 */
public class ZipUtils {
    /**
     * 解压文件
     * @param zipFilePath 解压文件路径
     * @param outputFolder 输出解压文件路径
     */
    public static void unZipIt(String zipFilePath,String outputFolder){
        byte[] buffer = new byte[1024];

        File folder = new File(outputFolder);
        if (!folder.exists()){
            folder.mkdir();
        }
        try {
            //get the zip file content
            ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath));
            ZipEntry ze = zis.getNextEntry();
            while (ze != null){
                String fileName = ze.getName();
                File newFile = new File(outputFolder+File.separator+fileName);
                System.out.println("file unzip : "+ newFile.getAbsoluteFile());
                //create all non exists folders
                //else you will hit FileNotFoundException for compressed folder
                //大部分网络上的源码，这里没有判断子目录
                if (ze.isDirectory()){
                    newFile.mkdirs();
                }else{
                    new File(newFile.getParent()).mkdirs();
                    FileOutputStream fos = new FileOutputStream(newFile);
                    int len;
                    while ((len = zis.read(buffer))!=-1){
                        fos.write(buffer,0,len);
                    }
                    fos.close();
                }
                ze = zis.getNextEntry();
            }
            zis.closeEntry();
            zis.close();
            System.out.println("Done");
        }catch (IOException e){
            e.printStackTrace();
        }
    }



}