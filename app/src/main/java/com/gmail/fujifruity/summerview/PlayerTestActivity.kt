package com.gmail.fujifruity.summerview

import android.os.Build
import android.os.Bundle
import android.os.Handler
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.gmail.fujifruity.summerview.databinding.ActivityPlayertestBinding

@RequiresApi(Build.VERSION_CODES.M)
class PlayerTestActivity : AppCompatActivity() {

    lateinit var binding: ActivityPlayertestBinding
    var player: PlayerWrapper? = null
    lateinit var handler: Handler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayertestBinding.inflate(layoutInflater)
        setContentView(binding.root)
        handler = Handler()
    }

    override fun onPause() {
        super.onPause()
        logd(TAG) { "Stop player." }
        player?.close()
    }

    companion object {
        private val TAG = PlayerTestActivity::class.java.simpleName
    }

}
