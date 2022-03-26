package me.ppting.plugin.utils

import pink.madis.apk.arsc.StringPoolChunk

/**
 * Created by PPTing on 2022/3/26.
 * Description:
 */

fun StringPoolChunk.setString(index:Int, value:String){
    try{
        val field = javaClass.getDeclaredField("strings")
        field.setAccessible(true)
        val list = field.get(this) as MutableList<String>
        list.set(index,value)
    }catch (e:Exception){
        e.printStackTrace()
    }

}