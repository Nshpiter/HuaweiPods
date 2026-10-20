package moe.chenxy.huaweipods.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ModuleHealthTest {
    @Test fun initialWaitIsNotTimeout() {
        assertEquals(ModuleHealth.CHECKING, moduleHealth(100, 100, 0, null, "new"))
        assertEquals(ModuleHealth.TIMEOUT, moduleHealth(76_000, 100, 0, null, "new"))
    }
    @Test fun onlyCurrentBuildIsOnline() {
        assertEquals(ModuleHealth.ONLINE, moduleHealth(200, 100, 150, "new", "new"))
        assertEquals(ModuleHealth.RELOAD_REQUIRED, moduleHealth(200, 100, 150, "old", "new"))
        assertEquals(ModuleHealth.CHECKING, moduleHealth(200, 100, 150, "", "new"))
    }
    @Test fun staleHeartbeatTimesOutAndRestartResetsGrace() {
        assertEquals(ModuleHealth.TIMEOUT, moduleHealth(80_000, 100, 150, "old", "new"))
        assertEquals(ModuleHealth.CHECKING, moduleHealth(80_000, 80_000, 0, null, "new"))
    }
}
