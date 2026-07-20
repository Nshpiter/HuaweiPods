package moe.chenxy.huaweipods.debugcapture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureStoreTest {
    @Test
    fun normalizeMetadata_keepsIssueOptionalAndMasksAddress() {
        val normalized = CaptureStore.normalizeMetadata(
            CaptureSessionMetadata(
                issueId = "  ",
                headsetModel = "  HUAWEI FreeBuds Pro 4  ",
                officialAppPackage = "  com.huawei.smarthome  ",
                headsetAddress = "AA:BB:CC:DD:EE:FF",
                headsetNameSource = " ",
                featureCatalogVersion = " ",
            ),
        )

        assertNull(normalized.issueId)
        assertEquals("HUAWEI FreeBuds Pro 4", normalized.headsetModel)
        assertEquals("com.huawei.smarthome", normalized.officialAppPackage)
        assertEquals("**:**:**:**:EE:FF", normalized.headsetAddress)
        assertEquals(DEFAULT_HEADSET_NAME_SOURCE, normalized.headsetNameSource)
        assertEquals(DEFAULT_FEATURE_CATALOG_VERSION, normalized.featureCatalogVersion)

        val normalizedAgain = CaptureStore.normalizeMetadata(
            normalized.copy(headsetAddress = normalized.headsetAddress),
        )
        assertEquals("**:**:**:**:EE:FF", normalizedAgain.headsetAddress)
    }

    @Test
    fun sessionFromMetadata_readsSchema2RecoveryFields() {
        val session = CaptureStore.sessionFromStoredValues(
            baseValues(schemaVersion = 2).copy(
                issueId = null,
                headsetModel = "legacy compatibility name",
                headsetName = "HUAWEI FreeClip 2",
                officialAppPackage = "com.huawei.smarthome",
                headsetAddress = "**:**:**:**:12:34",
                headsetNameSource = "connected_bluetooth",
                featureCatalogVersion = "huawei-headset-v2",
            ),
        )

        assertNull(session.issueId)
        assertEquals("HUAWEI FreeClip 2", session.headsetModel)
        assertEquals("com.huawei.smarthome", session.officialAppPackage)
        assertEquals("**:**:**:**:12:34", session.headsetAddress)
        assertEquals("connected_bluetooth", session.headsetNameSource)
        assertEquals("huawei-headset-v2", session.featureCatalogVersion)
        assertTrue(session.isActive)
    }

    @Test
    fun sessionFromMetadata_readsSchema1WithExplicitLegacyFallbacks() {
        val session = CaptureStore.sessionFromStoredValues(
            baseValues(schemaVersion = 1).copy(
                issueId = "unspecified",
                headsetModel = "FreeBuds 5",
                officialAppPackage = "com.huawei.smartaudio",
            ),
        )

        assertNull(session.issueId)
        assertEquals("FreeBuds 5", session.headsetModel)
        assertEquals("com.huawei.smartaudio", session.officialAppPackage)
        assertNull(session.headsetAddress)
        assertEquals(LEGACY_HEADSET_NAME_SOURCE, session.headsetNameSource)
        assertEquals(LEGACY_FEATURE_CATALOG_VERSION, session.featureCatalogVersion)
    }

    @Test
    fun readmeIssueLabel_describesMissingAssociation() {
        assertEquals("未关联 Issue", CaptureStore.readmeIssueLabel(null))
        assertEquals("未关联 Issue", CaptureStore.readmeIssueLabel("  "))
        assertEquals("#42", CaptureStore.readmeIssueLabel(" #42 "))
    }

    @Test
    fun requireNoActiveSession_rejectsStartingAnotherSession() {
        val activeSession = CaptureStore.sessionFromStoredValues(
            baseValues(schemaVersion = 2).copy(headsetName = "HUAWEI FreeBuds 6"),
        )

        val failure = assertThrows(IllegalStateException::class.java) {
            CaptureStore.requireNoActiveSession(activeSession)
        }

        assertTrue(failure.message.orEmpty().contains(activeSession.id))
        CaptureStore.requireNoActiveSession(null)
    }

    private fun baseValues(schemaVersion: Int) = CaptureStore.StoredSessionValues(
        schemaVersion = schemaVersion,
        id = "20260720-120000-000-test",
        issueId = null,
        headsetName = null,
        headsetModel = null,
        officialAppPackage = null,
        headsetAddress = null,
        headsetNameSource = null,
        featureCatalogVersion = null,
        startedAtEpochMs = 1_000L,
        stoppedAtEpochMs = null,
        eventCount = 3L,
        protocolEventCount = 2L,
        bytesWritten = 128L,
    )
}
