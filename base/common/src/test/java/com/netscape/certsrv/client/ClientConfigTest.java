package com.netscape.certsrv.client;

import static org.junit.Assert.assertEquals;

import java.net.MalformedURLException;

import org.junit.Before;
import org.junit.Test;

import com.netscape.certsrv.util.JSONSerializer;

public class ClientConfigTest {

    private static ClientConfig before = new ClientConfig();

    @Before
    public void setUpBefore() throws MalformedURLException {
        before.setServerURL("http://localhost:8080");
        before.setNSSDatabase("certs");
        before.setNSSPassword("12345");
        before.setNSSPassword("internal", "12345");
        before.setNSSPassword("hsm", "12345");
        before.setCertNickname("caadmin");
        before.setUsername("caadmin");
        before.setPassword("12345");
    }

    @Test
    public void testJSON() throws Exception {
        // Act
        String json = before.toJSON();
        System.out.println("JSON (before): " + json);

        ClientConfig afterJSON = JSONSerializer.fromJSON(json, ClientConfig.class);
        System.out.println("JSON (after): " + afterJSON.toJSON());

        // Assert
        assertEquals(before, afterJSON);
    }

}
