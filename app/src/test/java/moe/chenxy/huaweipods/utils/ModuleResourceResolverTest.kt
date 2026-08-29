package moe.chenxy.huaweipods.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleResourceResolverTest {
    @Test
    fun `only the same non-empty build id may use module resources`() {
        assertTrue(ModuleResourceResolver.moduleBuildMatches("b123", "b123"))
        assertFalse(ModuleResourceResolver.moduleBuildMatches("b122", "b123"))
        assertFalse(ModuleResourceResolver.moduleBuildMatches(null, "b123"))
        assertFalse(ModuleResourceResolver.moduleBuildMatches("", "b123"))
    }

}
