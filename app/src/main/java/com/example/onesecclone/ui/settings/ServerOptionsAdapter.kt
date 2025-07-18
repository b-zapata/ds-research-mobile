package com.example.onesecclone.ui.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.onesecclone.R
import com.example.onesecclone.config.AppConfig

class ServerOptionsAdapter(
    private val environments: List<AppConfig.ServerEnvironment>,
    private var currentEnvironment: AppConfig.ServerEnvironment,
    private val onEnvironmentSelected: (AppConfig.ServerEnvironment) -> Unit
) : RecyclerView.Adapter<ServerOptionsAdapter.ServerOptionViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServerOptionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_server_option, parent, false)
        return ServerOptionViewHolder(view)
    }

    override fun onBindViewHolder(holder: ServerOptionViewHolder, position: Int) {
        holder.bind(environments[position])
    }

    override fun getItemCount(): Int = environments.size

    fun updateCurrentEnvironment(environment: AppConfig.ServerEnvironment) {
        currentEnvironment = environment
        notifyDataSetChanged()
    }

    inner class ServerOptionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val radioButton: RadioButton = itemView.findViewById(R.id.radioButton)
        private val environmentName: TextView = itemView.findViewById(R.id.environmentName)
        private val environmentUrl: TextView = itemView.findViewById(R.id.environmentUrl)

        fun bind(environment: AppConfig.ServerEnvironment) {
            environmentName.text = environment.displayName
            environmentUrl.text = if (environment.url.isNotEmpty()) environment.url else "Custom URL"

            radioButton.isChecked = environment == currentEnvironment

            itemView.setOnClickListener {
                onEnvironmentSelected(environment)
            }

            radioButton.setOnClickListener {
                onEnvironmentSelected(environment)
            }
        }
    }
}
