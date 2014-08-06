package com.workshare.msnos.core;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MessageBuilderTest {

    @Test
    public void shouldPopulateSequenceNumberWhenFromAnAgent() throws Exception {
        LocalAgent agent = mock(LocalAgent.class);
        when(agent.getIden()).thenReturn(new Iden(Iden.Type.AGT, UUID.randomUUID()));

        Cloud to = mock(Cloud.class);
        when(to.getIden()).thenReturn(new Iden(Iden.Type.CLD, UUID.randomUUID()));

        when(agent.getSeq()).thenReturn(32L);
        Message msg = new MessageBuilder(Message.Type.APP, agent, to).make();

        assertEquals(32L, msg.getSeq());
    }
}