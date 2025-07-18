package com.example.onesecclone.ui.settings

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.onesecclone.R
import com.example.onesecclone.config.AppConfig
import com.example.onesecclone.network.NetworkClient

/**
 * Fragment for managing server configuration settings
 */
class ServerSettingsFragment : Fragment() {

    private lateinit var networkClient: NetworkClient
    private lateinit var serverOptionsRecyclerView: RecyclerView
    private lateinit var serverOptionsAdapter: ServerOptionsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_server_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        networkClient = NetworkClient.getInstance(requireContext())

        setupRecyclerView(view)
        setupCurrentUrlDisplay(view)
    }

    private fun setupRecyclerView(view: View) {
        serverOptionsRecyclerView = view.findViewById(R.id.serverOptionsRecyclerView)
        serverOptionsAdapter = ServerOptionsAdapter(
            environments = AppConfig.ServerEnvironment.values().toList(),
            currentEnvironment = networkClient.getCurrentServerEnvironment(),
            onEnvironmentSelected = { environment ->
                handleEnvironmentSelection(environment)
            }
        )

        serverOptionsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        serverOptionsRecyclerView.adapter = serverOptionsAdapter
    }

    private fun setupCurrentUrlDisplay(view: View) {
        val currentUrlText = view.findViewById<androidx.appcompat.widget.AppCompatTextView>(R.id.currentUrlText)
        currentUrlText.text = "Current URL: ${networkClient.getBaseUrl()}"
    }

    private fun handleEnvironmentSelection(environment: AppConfig.ServerEnvironment) {
        when (environment) {
            AppConfig.ServerEnvironment.CUSTOM -> {
                showCustomUrlDialog()
            }
            else -> {
                networkClient.setServerEnvironment(environment)
                updateCurrentUrlDisplay()
                Toast.makeText(requireContext(), "Server changed to ${environment.displayName}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showCustomUrlDialog() {
        val editText = EditText(requireContext()).apply {
            hint = "Enter server URL (e.g., https://your-server.com:8080/)"
            setText(networkClient.getBaseUrl())
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Custom Server URL")
            .setMessage("Enter the complete URL including protocol and port")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                val url = editText.text.toString().trim()
                if (isValidUrl(url)) {
                    networkClient.setBaseUrl(url)
                    updateCurrentUrlDisplay()
                    Toast.makeText(requireContext(), "Custom URL saved", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Invalid URL format", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun isValidUrl(url: String): Boolean {
        return try {
            val urlPattern = "^(https?://)([^/]+)(:[0-9]+)?(/.*)?$".toRegex()
            urlPattern.matches(url)
        } catch (e: Exception) {
            false
        }
    }

    private fun updateCurrentUrlDisplay() {
        view?.findViewById<androidx.appcompat.widget.AppCompatTextView>(R.id.currentUrlText)?.text =
            "Current URL: ${networkClient.getBaseUrl()}"
        serverOptionsAdapter.updateCurrentEnvironment(networkClient.getCurrentServerEnvironment())
    }
}
