/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.java.sip.communicator.impl.protocol.jabber.extensions.streammanagement;

import java.text.SimpleDateFormat;
import java.util.LinkedList;
import java.util.Queue;

import javax.swing.SwingUtilities;

import org.jivesoftware.smack.SmackException.*;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.sm.packet.StreamManagement.*;
import org.jivesoftware.smack.util.Async;
import org.xmlpull.v1.XmlPullParser;

/**
 * The entry used in unacknowledgedMessages queue.
 *
 * @author Maksym Chmutov
 */

public class ConnectionStanzaBuffer
{
    /**
     * Number of the required sent stanzas for requiring ack.
     */
    private static final int STANZAS_FOR_ACK_REQUEST = 5;
    /**
     * Indicates the number that is going to be paired with the next stanza in the buffer.
     */
    private long stanzaPairedValue;
    
    /**
     * Counter for a more efficient approach. We request an ack every five 5 sent stanzas.
     */
    private int counter;
    
    /**
     * Indicates the connection to which the buffer belongs to.
     */
    private XMPPConnection connection;

    /**
     * Unacknowledged messages that have been sent.
     */
    private Queue<BufferEntry> unacknowledgedMessages = new LinkedList<>();
    
    private StanzaListener packetReaderListener = null;

    public final Inbound inbound = new Inbound();
    public final Outbound outbound = new Outbound();

    public ConnectionStanzaBuffer(XMPPConnection connection)
    {
        this.connection = connection;
    }

    public long getStanzaPairedValue()
    {
        return stanzaPairedValue;
    }

    public int getBufferSize()
    {
        return unacknowledgedMessages.size();
    }
    
    private void sendAckRequest() 
    {
        AckRequest req = AckRequest.INSTANCE;
        try
        {
            connection.sendNonza(req);
        }
        catch (NotConnectedException | InterruptedException e)
        {
            e.printStackTrace();
        }
    }
    
    private void resetCounter() 
    {
        this.counter = 0;
    }
    
    private void incrementCounter() 
    {
        this.counter++;
    }
    
    private void incrementStanzaPairedValue() 
    {
        this.stanzaPairedValue++;
    }
    
    public class Outbound 
        implements StanzaListener
        {

        @Override
        public void processStanza(Stanza outboundStanza)
            throws NotConnectedException,
            InterruptedException
        {
            try 
            {
                if(outboundStanza != null) 
                {
                    unacknowledgedMessages.add(new BufferEntry(stanzaPairedValue, outboundStanza));
                    incrementStanzaPairedValue();
                    incrementCounter();
                    if(counter >= STANZAS_FOR_ACK_REQUEST)
                    {
                       sendAckRequest();
                       resetCounter();
                    }
                }
                System.out.println("Stanza CATCHED! -> " +outboundStanza.getStanzaId() +" Type: "+outboundStanza.toXML() + " Queue entries"+ getBufferSize() + "Stanza paired value"+ stanzaPairedValue);
            }
            catch(Throwable t)
            {
                t.printStackTrace();
            }
        }
            
        }
    
    public class Inbound
        implements StanzaListener
    {

        @Override
        public void processStanza(Stanza packet)
            throws NotConnectedException,
            InterruptedException
        {
            // TODO Auto-generated method stub
            
        }

    }
}





