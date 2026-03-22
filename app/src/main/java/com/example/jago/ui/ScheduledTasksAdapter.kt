package com.example.jago.ui

import android.content.Context
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.jago.R
import com.example.jago.logic.CommandType
import com.example.jago.scheduler.ScheduledTask
import java.util.Date

class ScheduledTasksAdapter(
    private val onDeleteClick: (Long) -> Unit
) : RecyclerView.Adapter<ScheduledTasksAdapter.ViewHolder>() {

    private var tasks: List<ScheduledTask> = emptyList()

    fun updateTasks(newTasks: List<ScheduledTask>) {
        tasks = newTasks
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvType: TextView = view.findViewById(R.id.tvTaskType)
        val tvDetails: TextView = view.findViewById(R.id.tvTaskDetails)
        val tvTime: TextView = view.findViewById(R.id.tvTriggerTime)
        val btnCancel: ImageButton = view.findViewById(R.id.btnCancel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_scheduled_task, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val task = tasks[position]
        val cmd = task.command

        holder.tvType.text = cmd.type.name.replace("_", " ")

        val details = StringBuilder()
        when (cmd.type) {
            CommandType.SEND_WHATSAPP_MESSAGE -> {
                details.append("To: ${cmd.contactName}\n")
                details.append("Msg: ${cmd.messageBody}")
            }
            CommandType.SET_REMINDER -> {
                details.append("Msg: ${cmd.messageBody}")
            }
            else -> {
                details.append("Waiting execution")
            }
        }
        holder.tvDetails.text = details.toString()

        val date = Date(task.triggerAtMillis)
        val dateStr = DateFormat.getTimeFormat(holder.itemView.context).format(date)
        holder.tvTime.text = "At $dateStr"

        holder.btnCancel.setOnClickListener {
            onDeleteClick(task.id)
        }
    }

    override fun getItemCount() = tasks.size
}
