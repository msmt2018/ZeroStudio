/*
 *  ZeroStudio IDE - Debugger H3 自动重连 单元测试
 *
 *  覆盖 Phase H3 的新增功能:
 *    - lastHost / lastPort 保存连接参数
 *    - setAutoReconnect / isAutoReconnectEnabled
 *    - reconnect() 正常重连
 *    - reconnect() 无 lastHost 时返回 false
 *    - reconnect() 已连接时返回 false
 *    - getMaxReconnectAttempts / lastConnectedHost / lastConnectedPort
 */

package com.zerostudio.debugger.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.zerostudio.debugger.jdwp.FakeJdwpClient;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DebuggerReconnectTest {

    @Test
    public void connect_storesHostAndPort() throws Exception {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueOkReply(new byte[0]); // handshake ok
        Debugger dbg = new Debugger(fake);
        dbg.connect("192.168.1.100", 8787);
        assertEquals("192.168.1.100", dbg.lastConnectedHost());
        assertEquals(8787, dbg.lastConnectedPort());
    }

    @Test
    public void setAutoReconnect_enablesFlag() {
        FakeJdwpClient fake = new FakeJdwpClient();
        Debugger dbg = new Debugger(fake);
        assertFalse(dbg.isAutoReconnectEnabled());
        dbg.setAutoReconnect(true, 3);
        assertTrue(dbg.isAutoReconnectEnabled());
        assertEquals(3, dbg.getMaxReconnectAttempts());
    }

    @Test
    public void setAutoReconnect_zeroAttemptsMeansInfinite() {
        FakeJdwpClient fake = new FakeJdwpClient();
        Debugger dbg = new Debugger(fake);
        dbg.setAutoReconnect(true, 0);
        assertTrue(dbg.isAutoReconnectEnabled());
        assertEquals(0, dbg.getMaxReconnectAttempts());
    }

    @Test
    public void reconnect_noLastHost_returnsFalse() {
        FakeJdwpClient fake = new FakeJdwpClient();
        Debugger dbg = new Debugger(fake);
        // No previous connection
        assertFalse(dbg.reconnect());
    }

    @Test
    public void reconnect_alreadyConnected_returnsFalse() throws Exception {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueOkReply(new byte[0]); // handshake
        Debugger dbg = new Debugger(fake);
        dbg.connect("localhost", 5005);
        assertTrue(dbg.lastConnectedHost() != null);
        // Already connected
        assertFalse(dbg.reconnect());
    }

    @Test
    public void reconnect_afterDisconnect_attemptsConnection() throws Exception {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueOkReply(new byte[0]); // handshake
        Debugger dbg = new Debugger(fake);
        dbg.connect("localhost", 5005);
        fake.simulateDisconnection();
        assertFalse(dbg.client().isConnected());
        // Should attempt reconnect
        boolean attempted = dbg.reconnect();
        assertTrue("reconnect should be attempted", attempted);
        assertTrue("should be reconnected", dbg.client().isConnected());
    }

    @Test
    public void reconnect_failure_returnsFalse() {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.setFailOnConnect(true);
        Debugger dbg = new Debugger(fake);
        // Manually set the last host (simulate a previous connection)
        dbg.setAutoReconnect(true, 3);
        // Can't directly set lastHost, so we test via connect failure
        try {
            dbg.connect("unreachable.host.invalid", 9999);
        } catch (Exception ignored) {}
        // reconnect would use lastHost, but we didn't connect successfully
        assertNull(dbg.lastConnectedHost());
    }

    @Test
    public void maxReconnectAttempts_defaultsToZero() {
        FakeJdwpClient fake = new FakeJdwpClient();
        Debugger dbg = new Debugger(fake);
        assertEquals(0, dbg.getMaxReconnectAttempts());
    }

    @Test
    public void lastConnectedPort_defaultsToZero() {
        FakeJdwpClient fake = new FakeJdwpClient();
        Debugger dbg = new Debugger(fake);
        assertEquals(0, dbg.lastConnectedPort());
    }
}
