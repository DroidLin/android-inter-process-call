package com.lza.android.inter.process.call

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.lza.android.inter.process.library.ProcessCenter
import kotlinx.coroutines.launch

class LibraryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_library)

        val btnRemoteCall: View = findViewById(R.id.btn_remote_call)
        btnRemoteCall.setOnClickListener { v ->
            this.lifecycleScope.launch {
                val startTimeStamp = SystemClock.elapsedRealtimeNanos()
                val processService = ProcessCenter.getService(ProcessConst.KEY_MAIN_PROCESS, ProcessService::class.java)
                val remoteProcessName = processService.suspendTestFunction("libraryActivity", 500)
                val count = with(processService) { remoteProcessName.calculateStringCount() }
                Log.i("LibraryActivity", "remoteProcessName: $remoteProcessName, count: $count")
                Log.i(
                    "LibraryActivity",
                    "remote call cost: ${(SystemClock.elapsedRealtimeNanos() - startTimeStamp) / 1_000_000L}ms"
                )
            }
        }
    }
}