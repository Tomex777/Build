package com.night.cortex

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.night.cortex.data.CortexRepository
import com.night.cortex.hosting.HostingProviderId
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CortexVisualEvidenceSetupTest {
    @Test
    fun configureOfflineAgentForVisualEvidence() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repo = CortexRepository(context)
        repo.rememberHostingIdentifier(HostingProviderId.AZURE, "https://127.0.0.1:9")
        repo.rememberHostingSecret(HostingProviderId.AZURE, "ci-visual-evidence-token")
    }
}
