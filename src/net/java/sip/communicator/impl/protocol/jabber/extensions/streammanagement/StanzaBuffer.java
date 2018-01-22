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

import java.util.LinkedList;
import java.util.Queue;

import org.jivesoftware.smack.packet.Stanza;

/**
 * The entry used in unacknowledgedMessages queue.
 *
 * @author Maksym Chmutov
 */

public class StanzaBuffer
{
    private static StanzaBuffer Instance = null;
    
    /**
     * Indicates the number that is going to be paired with the next stanza in the buffer.
     */
    private int stanzaPairedValue;

    /**
     * Unacknowledged messages that have been sent.
     */
    private Queue<BufferEntry> unacknowledgedMessages = new LinkedList<>();
    
    
    private StanzaBuffer() 
    {
        
    }
    
    public static StanzaBuffer getStanzaBuffer() 
    {
        if(Instance == null) {   
            synchronized(StanzaBuffer.class) 
            {
                if(Instance == null)
                {
                    Instance = new StanzaBuffer();
                }
            }
        }
        return Instance;
    }
    
    public synchronized void addStanzaToBuffer(Stanza stanza)
    {
        unacknowledgedMessages.add(new BufferEntry(this.stanzaPairedValue, stanza));
        this.stanzaPairedValue++;
        System.out.println("Stanza paired value"+ stanzaPairedValue);
        System.out.println("Queue entries"+ unacknowledgedMessages.size());
    }

    public synchronized int getStanzaPairedValue()
    {
        return stanzaPairedValue;
    }
    
    public synchronized int getBufferSize()
    {
        return unacknowledgedMessages.size();
    }
    
}
