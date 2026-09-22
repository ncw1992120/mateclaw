package vip.mate.dataagent.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AloudataServiceImplTest {

    @Test
    void usesLocalMockIdentityWhenTheUserHasNoBoundAuthValue() {
        assertEquals("mock-uid-001", AloudataServiceImpl.resolveAuthValue(null, true));
    }

    @Test
    void keepsProductionAuthRequirementWhenTheUserHasNoBoundAuthValue() {
        assertNull(AloudataServiceImpl.resolveAuthValue(null, false));
    }
}
