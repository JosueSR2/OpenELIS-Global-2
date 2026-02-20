package org.openelisglobal.middleware;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

public class MiddlewareServiceTest {

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectHttpUrlWhenNotExplicitlyAllowed() {
        new MiddlewareService("http://localhost:5284/api/analyzer/", false, "", 30, new ObjectMapper());
    }

    @Test
    public void shouldAllowHttpsUrl() {
        new MiddlewareService("https://localhost:5284/api/analyzer/", false, "", 30, new ObjectMapper());
    }
}
