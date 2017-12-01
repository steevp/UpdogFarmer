package com.steevsapps.idledaddy;

import com.steevsapps.idledaddy.utils.Utils;

import org.junit.Test;

import static org.junit.Assert.*;

public class UtilsTest {
    /**
     * Test the removeSpecialChars method
     */
    @Test
    public void removeSpecialChars_works() {
        assertEquals("daddy123", Utils.removeSpecialChars("daಥd益dಥy123"));
    }
}
