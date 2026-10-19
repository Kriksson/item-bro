package ItemBro;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

class CiFailureProbeTest {

    @Test
    void reportsRemainAvailableWhenTestFails() {
        fail("CI failure probe");
    }
}