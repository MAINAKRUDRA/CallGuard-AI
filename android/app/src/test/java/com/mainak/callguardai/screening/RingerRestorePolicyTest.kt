package com.mainak.callguardai.screening

import android.media.AudioManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RingerRestorePolicyTest {
    @Test fun restoresOriginalModeWhenTemporaryRingModeIsStillActive() {
        assertTrue(RingerRestorePolicy.shouldRestoreOriginalMode(AudioManager.RINGER_MODE_NORMAL))
    }

    @Test fun preservesUserChangedSilentMode() {
        assertFalse(RingerRestorePolicy.shouldRestoreOriginalMode(AudioManager.RINGER_MODE_SILENT))
    }

    @Test fun preservesUserChangedVibrateMode() {
        assertFalse(RingerRestorePolicy.shouldRestoreOriginalMode(AudioManager.RINGER_MODE_VIBRATE))
    }
}
