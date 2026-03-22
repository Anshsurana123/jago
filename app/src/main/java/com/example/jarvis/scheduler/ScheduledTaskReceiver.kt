package com.example.jarvis.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.jarvis.logic.ActionExecutor

class ScheduledTaskReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra("taskId", -1)
        if (taskId == -1L) {
             Log.e("ScheduledTaskReceiver", "Received alarm but no taskId found")
             return
        }

        Log.d("ScheduledTaskReceiver", "Alarm received for task: $taskId")
        val task = ScheduledTaskEngine.getTaskById(context, taskId)
        
        if (task != null) {
            Log.d("ScheduledTaskReceiver", "Executing command: ${task.command.type}")
            
            // Execute the command
            val executor = ActionExecutor(context)
            executor.execute(task.command)
            
            // Cleanup
            ScheduledTaskEngine.removeTask(context, taskId)
        } else {
            Log.w("ScheduledTaskReceiver", "Task not found for id: $taskId (maybe already cancelled?)")
        }
    }
}
