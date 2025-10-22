// File: kotlin-lib/src/main/java/com/example/hc/HcPermissionActivity.kt
package com.example.hc

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.health.connect.client.PermissionController

/** A translucent activity that launches Health Connect permission UI and immediately returns a result. */
class HcPermissionActivity : ComponentActivity() {

    private val launcher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted: Set<String> ->
        if (granted.containsAll(HealthCore.defaultPermissions())) {
            setResult(Activity.RESULT_OK)
        } else {
            setResult(Activity.RESULT_CANCELED)
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!HealthCore.isAvailable(this)) {
            setResult(Activity.RESULT_CANCELED)
            finish(); return
        }
        launcher.launch(HealthCore.defaultPermissions())
    }
}
