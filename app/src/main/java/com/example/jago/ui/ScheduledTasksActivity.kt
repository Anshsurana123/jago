package com.example.jago.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.jago.R
import com.example.jago.scheduler.ScheduledTaskEngine

class ScheduledTasksActivity : AppCompatActivity() {

    private lateinit var adapter: ScheduledTasksAdapter
    private lateinit var tvEmpty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scheduled_tasks)

        tvEmpty = findViewById(R.id.tvEmpty)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        
        adapter = ScheduledTasksAdapter { taskId ->
             ScheduledTaskEngine.removeTask(this, taskId)
             loadTasks()
        }
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        
        loadTasks()
    }

    private fun loadTasks() {
        val tasks = ScheduledTaskEngine.getAllTasks(this)
        if (tasks.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
        } else {
            tvEmpty.visibility = View.GONE
        }
        adapter.updateTasks(tasks)
    }
}
