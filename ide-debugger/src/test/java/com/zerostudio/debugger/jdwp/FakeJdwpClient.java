/*
 *  ZeroStudio IDE - FakeJdwpClient
 *
 *  用于单元测试的 JdwpClient 假实现。绕过真实 TCP 套接字,按队列
 *  返回预制的响应包,并记录所有发出的命令以便断言。
 *
 *  用法:
 *    FakeJdwpClient fake = new FakeJdwpClient();
 *    fake.enqueueOkReply(buildSomeReply());
 *    Debugger d = new Debugger(fake);   // 包私有构造器
 *    EvalEngine e = new EvalEngine(d);
 *    EvalResult r = e.evaluate(threadId, frameId, "x");
 *    assertEquals(1, fake.commandCount());
 */

package com.zerostudio.debugger.jdwp;

import androidx.annotation.NonNull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FakeJdwpClient extends JdwpClient {

    /** 一条发送出去的 JDWP 命令记录(供测试断言)。 */
    public static final class SentCommand {
        public final byte commandSet;
        public final byte command;
        @NonNull public final byte[] data;

        public SentCommand(byte cs, byte c, @NonNull byte[] data) {
            this.commandSet = cs;
            this.command = c;
            this.data = data;
        }

        @Override
        public String toString() {
            return "SentCommand{cs=" + (commandSet & 0xff)
                    + ", cmd=" + (command & 0xff)
                    + ", dataLen=" + data.length + "}";
        }
    }

    /** 单条响应,根据命令返回 JdwpPacket。 */
    public interface Responder {
        @NonNull
        JdwpPacket respond(byte commandSet, byte command, @NonNull byte[] data);
    }

    private final List<Responder> queuedResponders = new ArrayList<>();
    private final List<SentCommand> sentCommands = new ArrayList<>();
    private boolean failOnMissingResponder = true;
    /** Phase H3: simulate connection state for reconnect tests. */
    private volatile boolean connected = false;
    /** Phase H3: if true, connect() throws IOException. */
    private volatile boolean failOnConnect = false;

    public FakeJdwpClient() {
        super();
    }

    /** 队列中追加一个"成功"响应,负载为 [payload]。 */
    public void enqueueOkReply(@NonNull byte[] payload) {
        queuedResponders.add((cs, c, d) -> okReply(payload));
    }

    /** 队列中追加一个"错误"响应,错误码为 [errorCode]。 */
    public void enqueueErrorReply(short errorCode) {
        queuedResponders.add((cs, c, d) -> errorReply(errorCode));
    }

    /** 队列中追加一个动态计算的响应。 */
    public void enqueueResponder(@NonNull Responder r) {
        queuedResponders.add(r);
    }

    /**
     * 关闭对未排队命令的严格失败行为。设置后,未配对的命令会回
     * 一个"errorCode=100"的错误响应,而不是抛 IOException。
     */
    public void setFailOnMissingResponder(boolean v) {
        this.failOnMissingResponder = v;
    }

    /** 已发送的命令列表(只读视图)。 */
    @NonNull
    public List<SentCommand> sentCommands() {
        return sentCommands;
    }

    public int commandCount() {
        return sentCommands.size();
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    /** Phase H3: Simulate a disconnection event. */
    public void simulateDisconnection() {
        connected = false;
    }

    /** Phase H3: When true, connect() will throw IOException. */
    public void setFailOnConnect(boolean v) {
        this.failOnConnect = v;
    }

    @Override
    public void connect(String host, int port) throws IOException {
        if (failOnConnect) {
            throw new IOException("simulated connect failure");
        }
        connected = true;
    }

    @Override
    public void close() {
        connected = false;
    }

    @Override
    @NonNull
    public JdwpPacket sendCommand(byte commandSet, byte command, @NonNull byte[] data)
            throws IOException {
        sentCommands.add(new SentCommand(commandSet, command, data));
        if (queuedResponders.isEmpty()) {
            if (failOnMissingResponder) {
                throw new IOException("no queued responder for cs=" + (commandSet & 0xff)
                        + " cmd=" + (command & 0xff));
            }
            return errorReply((short) 100);
        }
        Responder r = queuedResponders.remove(0);
        return r.respond(commandSet, command, data);
    }

    /** 构造一个错误码为 0 的成功响应包,负载为 [payload]。 */
    @NonNull
    public static JdwpPacket okReply(@NonNull byte[] payload) {
        byte[] data = new byte[2 + payload.length];
        data[0] = 0;
        data[1] = 0;
        System.arraycopy(payload, 0, data, 2, payload.length);
        return new JdwpPacket(0, JdwpPacket.FLAG_REPLY, (byte) 0, (byte) 0, data);
    }

    /** 构造一个错误响应包,JDWP 错误码为 [errorCode]。 */
    @NonNull
    public static JdwpPacket errorReply(short errorCode) {
        byte[] data = new byte[2];
        data[0] = (byte) ((errorCode >>> 8) & 0xff);
        data[1] = (byte) (errorCode & 0xff);
        return new JdwpPacket(0, JdwpPacket.FLAG_REPLY, (byte) 0, (byte) 0, data);
    }
}
