package com.mainak.callguardai.screening

import android.media.AudioManager
import org.junit.Assert.assertEquals
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

    @Test fun vipRingVolumeUsesAudibleButNotMaximumDefault() {
        assertEquals(5, VipRingVolumePolicy.minimumAudibleVolume(10))
        assertEquals(1, VipRingVolumePolicy.minimumAudibleVolume(1))
        assertEquals(0, VipRingVolumePolicy.minimumAudibleVolume(0))
    }

    @Test fun restoresVolumeOnlyWhileTemporaryVolumeIsUnchanged() {
        assertTrue(VipRingVolumePolicy.shouldRestoreOriginalVolume(5, 5))
        assertFalse(VipRingVolumePolicy.shouldRestoreOriginalVolume(7, 5))
    }
}
