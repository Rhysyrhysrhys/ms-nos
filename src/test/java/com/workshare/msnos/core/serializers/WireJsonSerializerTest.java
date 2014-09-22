package com.workshare.msnos.core.serializers;

import com.workshare.msnos.core.*;
import com.workshare.msnos.core.cloud.JoinSynchronizer;
import com.workshare.msnos.core.payloads.FltPayload;
import com.workshare.msnos.core.payloads.Presence;
import com.workshare.msnos.core.payloads.QnePayload;
import com.workshare.msnos.usvc.api.RestApi;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class WireJsonSerializerTest {

    private static final UUID AGENT_UUID = UUID.randomUUID();

    private static final UUID CLOUD_UUID = UUID.randomUUID();
    private static final Long CLOUD_INSTANCE_ID = 1274L;

    private Cloud cloud;
    private LocalAgent localAgent;
    private RemoteEntity remoteAgent;
    private WireJsonSerializer sz = new WireJsonSerializer();

    @BeforeClass
    public static void useLocalTimeSource() {
        System.setProperty("com.ws.nsnos.time.local", "true");
    }

    @Before
    public void before() throws Exception {
        cloud = new Cloud(CLOUD_UUID, "1231", new HashSet<Gateway>(Arrays.asList(new NoopGateway())), mock(JoinSynchronizer.class), CLOUD_INSTANCE_ID);

        localAgent = new LocalAgent(AGENT_UUID);
        localAgent.join(cloud);

        remoteAgent = new RemoteAgent(UUID.randomUUID(), cloud, null);
    }

    @Test
    public void shouldBeAbleToEncodeAndDecodeMessage() throws Exception {
        Message source = new MessageBuilder(Message.Type.PRS, localAgent, remoteAgent).with(new Presence(true)).make();

        byte[] data = sz.toBytes(source);
        Message decoded = sz.fromBytes(data, Message.class);

        assertEquals(source, decoded);
    }

    @Test
    public void shouldBeAbleToEncodeAndDecodeQNE() throws Exception {
        Message source = new MessageBuilder(Message.Type.QNE, localAgent, remoteAgent).with(new QnePayload("test", new RestApi("test", "/test", 7070))).make();

        byte[] data = sz.toBytes(source);
        Message decoded = sz.fromBytes(data, Message.class);

        assertEquals(source, decoded);
    }

    @Test
    public void shouldSerializeVersionObject() throws Exception {
        String expected = "\"1.0\"";
        String current = sz.toText(Version.V1_0);

        assertEquals(expected, current);
    }

    @Test
    public void shouldSerializeUUIDObject() throws Exception {
        UUID uuid = UUID.randomUUID();
        String expected = "\"" + toShortString(uuid).replace("-", "") + "\"";
        String current = sz.toText(uuid);

        assertEquals(expected, current);
    }

    @Test
    public void shouldDeserializeUUIDObject() throws Exception {
        UUID expected = UUID.randomUUID();
        String text = "\"" + toShortString(expected).replace("-", "") + "\"";
        UUID current = sz.fromText(text, UUID.class);

        assertEquals(expected, current);
    }

    @Test
    public void shouldBeAbleToEncodeAndDecodeSignedMessage() throws Exception {
        final String sig = "this-is-a-signature";
        final String rnd = "random";
        Message source = new MessageBuilder(Message.Type.QNE, localAgent, cloud).signed(sig, rnd).make();

        byte[] data = sz.toBytes(source);
        Message decoded = sz.fromBytes(data, Message.class);

        assertEquals(sig, decoded.getSig());
        assertEquals(rnd, decoded.getRnd());
    }

    @Test
    public void shouldCorrectlyDeserializeFLTMessage() throws Exception {
        Message source = new MessageBuilder(MessageBuilder.Mode.RELAXED, Message.Type.FLT, cloud.getIden(), cloud.getIden()).with(new FltPayload(localAgent.getIden())).with(UUID.randomUUID()).make();

        byte[] data = sz.toBytes(source);
        Message decoded = sz.fromBytes(data, Message.class);

        assertEquals(source.getData(), decoded.getData());
    }

    @Test
    public void shouldSerializeBooleanCompact() throws Exception {
        assertEquals("1", sz.toText(Boolean.TRUE));
        assertEquals("0", sz.toText(Boolean.FALSE));
    }

    @Test
    public void shouldDeserializeBooleanCompact() throws Exception {
        assertEquals(Boolean.TRUE, sz.fromText("1", Boolean.class));
        assertEquals(Boolean.FALSE, sz.fromText("0", Boolean.class));
    }

    @Test
    public void shouldMessageFromCloudContainExtendedIden() throws Exception {
        Message source = new MessageBuilder(Message.Type.PIN, cloud, localAgent).with(UUID.randomUUID()).make();

        String expected = "\"fr\":\"CLD:" + toShortString(CLOUD_UUID) + ":" + Long.toString(CLOUD_INSTANCE_ID, 32) + "\"";
        String current = sz.toText(source);

        assertTrue(current.contains(expected));
    }

    @Test
    public void shouldMessageToCloudContainStandardIden() throws Exception {
        Message source = new MessageBuilder(Message.Type.PON, localAgent, cloud).make();

        String expected = "\"to\":\"CLD:" + toShortString(CLOUD_UUID) + "\"";
        String current = sz.toText(source);

        assertTrue(current.contains(expected));
    }

    private String toShortString(UUID uuid) {
        return uuid.toString().replaceAll("-", "");
    }

}
