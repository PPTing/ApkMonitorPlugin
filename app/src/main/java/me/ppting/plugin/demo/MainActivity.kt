package me.ppting.plugin.demo

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import me.ppting.plugin.R

class MainActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d("MainActivity"," id is ${R.drawable.ic_launcher_1}")
        Log.d("MainActivity"," id is ${R.drawable.ic_launcher_2}")

    }


}