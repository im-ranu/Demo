package com.artivatic.demo

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.artivatic.classifier_offline.ClassifierCameraActivity
import kotlinx.android.synthetic.main.activity_main.*
import android.app.Activity
import android.util.Log
import android.view.View
import org.json.JSONObject




class MainActivity : AppCompatActivity() {
    var TAG = "MainActivity"
    var isStarted = false;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        button.setOnClickListener {
            val intent = Intent(this@MainActivity, ClassifierCameraActivity::class.java)
// To pass any data to next activity
// start your next activity
            startActivityForResult(intent,1)

        }



    }


    override fun onResume() {
        super.onResume()
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {

        if (requestCode == 1) {
            if (resultCode == Activity.RESULT_OK) {
                val jsonObj = JSONObject(data!!.getStringExtra("result"))
                Log.e(TAG,"Result" + jsonObj.toString())

            }
            if (resultCode == Activity.RESULT_CANCELED) {
                //Write your code if there's no result
            }
        }
    }
}
