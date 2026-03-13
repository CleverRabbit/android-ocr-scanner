package com.contract.scanner

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.contract.scanner.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var preferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferences = AppPreferences(this)

        // Load current settings
        binding.urlTemplateEdit.setText(preferences.urlTemplate)
        binding.autoOpenSwitch.isChecked = preferences.autoOpen

        // Save settings
        binding.saveButton.setOnClickListener {
            saveSettings()
        }
    }

    private fun saveSettings() {
        val urlTemplate = binding.urlTemplateEdit.text.toString().trim()
        
        if (urlTemplate.isEmpty()) {
            Toast.makeText(this, "Шаблон ссылки не может быть пустым", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (!urlTemplate.contains("{NUMBER}")) {
            Toast.makeText(this, "Шаблон должен содержать {NUMBER} для подстановки номера договора", Toast.LENGTH_LONG).show()
            return
        }

        preferences.urlTemplate = urlTemplate
        preferences.autoOpen = binding.autoOpenSwitch.isChecked

        Toast.makeText(this, "Настройки сохранены", Toast.LENGTH_SHORT).show()
        finish()
    }
}
