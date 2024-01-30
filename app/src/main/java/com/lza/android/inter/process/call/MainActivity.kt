package com.lza.android.inter.process.call

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.lza.android.inter.process.library.ProcessCenter
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnSendAction: View = findViewById(R.id.btn_send_action)
        btnSendAction.setOnClickListener { v ->
            val intent = Intent(v.context, LibraryActivity::class.java)
            v.context.startActivity(intent)
        }

        val btnRemoteCall: View = findViewById(R.id.btn_remote_call)
        btnRemoteCall.setOnClickListener { v ->
            this.lifecycleScope.launch {
                val startTimeStamp = SystemClock.elapsedRealtimeNanos()
                val processService = ProcessCenter.getService(ProcessConst.KEY_LIBRARY_PROCESS, ProcessService::class.java, LibraryProcessServiceImpl)
                val remoteResult = processService.processName
                Log.i("MainActivity", "remoteProcessName: ${remoteResult}")
                Log.i("MainActivity", "remote call cost: ${(SystemClock.elapsedRealtimeNanos() - startTimeStamp) / 1_000_000L}ms")
            }
        }

        val btnCheck: View = findViewById(R.id.btn_check)
        btnCheck.setOnClickListener {
//            val kClass = ProcessService::class.java.kotlin
//            kClass.members.forEach { kCallable ->
//                val stringBuilder = StringBuilder()
//                    .append("memberName: ").append(kCallable.name).append("\n")
//                    .append("isSuspendFunction: ").append(kCallable.isSuspend).append("\n")
//                    .append("returnType isNullable: ").append(kCallable.returnType.isMarkedNullable).append("\n")
//
//                Log.d("MainActivity", stringBuilder.toString())
//            }
        }
    }
}