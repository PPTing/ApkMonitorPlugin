package me.ppting.plugin.demo

import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import me.ppting.plugin.R

class MainActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d("MainActivity"," id is ${R.drawable.ic_launcher_1}")
        Log.d("MainActivity"," id is ${R.drawable.ic_launcher_2}")
        findViewById<ImageView>(R.id.iv1).setImageResource(R.drawable.ic_launcher_1)
        findViewById<ImageView>(R.id.iv2).setImageResource(R.drawable.ic_launcher_2)
    }


}