/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.irc;

import java.util.*;

import net.java.sip.communicator.util.*;

import com.ircclouds.irc.api.*;
import com.ircclouds.irc.api.domain.messages.*;
import com.ircclouds.irc.api.listeners.*;
import com.ircclouds.irc.api.state.*;

/**
 * Identity manager.
 *
 * TODO Add support for Identity Service (NickServ) instance that can be used
 * for accessing remote identity facilities.
 *
 * TODO Implement OperationSetChangePassword once an identity service is
 * available.
 *
 * TODO Query remote identity service for current identity-state such as:
 * unknown, unauthenticated, authenticated.
 *
 * @author Danny van Heumen
 */
public class IdentityManager
{
    /**
     * Logger.
     */
    private static final Logger LOGGER = Logger
        .getLogger(IdentityManager.class);

    /**
     * Reserved symbols. These symbols have special meaning and cannot be
     * used to start nick names.
     */
    private static final Set<Character> RESERVED;

    /**
     * Initialize RESERVED symbols set.
     */
    static {
        final HashSet<Character> reserved = new HashSet<Character>();
        reserved.add('#');
        reserved.add('&');
        RESERVED = Collections.unmodifiableSet(reserved);
    }

    /**
     * The IRCApi instance.
     *
     * Instance must be thread-safe!
     */
    private final IRCApi irc;

    /**
     * The connection state.
     */
    private final IIRCState connectionState;

    /**
     * The identity container.
     */
    private final Identity identity = new Identity();

    /**
     * Constructor.
     *
     * @param irc thread-safe IRCApi instance
     * @param connectionState the connection state
     */
    public IdentityManager(final IRCApi irc, final IIRCState connectionState)
    {
        if (irc == null)
        {
            throw new IllegalArgumentException("irc instance cannot be null");
        }
        this.irc = irc;
        if (connectionState == null)
        {
            throw new IllegalArgumentException(
                "connectionState instance cannot be null");
        }
        this.connectionState = connectionState;
        // query user's WHOIS identity as perceived by the IRC server
        queryIdentity(this.irc, this.connectionState, new WhoisListener());
    }

    /**
     * Issue WHOIS query to discover identity as seen by the server.
     */
    private static void queryIdentity(final IRCApi irc, final IIRCState state,
        final WhoisListener listener)
    {
        // This method should be as light-weight as possible, since it is called
        // from the constructor.
        new Thread()
        {

            public void run()
            {
                irc.addListener(listener);
                irc.rawMessage("WHOIS " + state.getNickname());
            };
        }.start();
    }

    /**
     * Get the nick name of the user.
     *
     * @return Returns either the acting nick if a connection is established or
     *         the configured nick.
     */
    public String getNick()
    {
        return this.connectionState.getNickname();
    }

    /**
     * Set a new nick name.
     *
     * TODO Check ISUPPORT 'NICKLEN' for maximum nick length.
     *
     * @param nick new nick
     */
    public void setNick(final String nick)
    {
        this.irc.changeNick(checkNick(nick));
    }

    /**
     * Verify nick name.
     *
     * @param nick nick name
     * @return returns nick name
     */
    public static String checkNick(final String nick)
    {
        if (nick == null)
        {
            throw new IllegalArgumentException(
                "a nick name must be provided");
        }
        // TODO Add '+' and '!' to reserved symbols too?
        if (RESERVED.contains(nick.charAt(0)))
        {
            throw new IllegalArgumentException(
                "the nick name must not start with '#' or '&' "
                    + "since this is reserved for IRC channels");
        }
        return nick;
    }

    /**
     * Get the current identity string, based on nick, user and host of local
     * user.
     *
     * @return returns identity string
     */
    public String getIdentityString()
    {
        final String currentNick = this.connectionState.getNickname();
        return this.identity.getString(currentNick);
    }

    /**
     * The Whois listener which uses the WHOIS data for the local user to update
     * the identity information in the provided container.
     *
     * @author Danny van Heumen
     */
    private final class WhoisListener
        extends VariousMessageListenerAdapter
    {
        /**
         * IRC reply for WHOIS query.
         */
        private static final int RPL_WHOISUSER = 311;

        /**
         * On receiving a server numeric message.
         *
         * @param msg Server numeric message
         */
        @Override
        public void onServerNumericMessage(final ServerNumericMessage msg)
        {
            switch (msg.getNumericCode().intValue())
            {
            case RPL_WHOISUSER:
                final String whoismsg = msg.getText();
                final int endNickIndex = whoismsg.indexOf(' ');
                final String nick = whoismsg.substring(0, endNickIndex);
                if (!IdentityManager.this.connectionState.getNickname().equals(
                    nick))
                {
                    // We can only use WHOIS info about ourselves to discover
                    // our identity on the IRC server. So skip other WHOIS
                    // replies.
                    return;
                }
                updateIdentity(whoismsg);
                // Once the WHOIS reply is processed and the identity is
                // updated, we can delete the listener as the purpose is
                // fulfilled.
                IdentityManager.this.irc.deleteListener(this);
                break;
            default:
                break;
            }
        }

        /**
         * OnUserQuit event.
         *
         * @param msg QuitMessage event.
         */
        @Override
        public void onUserQuit(final QuitMessage msg)
        {
            final String user = msg.getSource().getNick();
            if (IdentityManager.this.connectionState.getNickname().equals(user))
            {
                LOGGER.debug("Local user QUIT message received: removing "
                    + "whois listener.");
                IdentityManager.this.irc.deleteListener(this);
            }
        }

        /**
         * Update the identity container instance with received WHOIS data.
         *
         * @param whoismsg the WHOIS reply message content
         */
        private void updateIdentity(final String whoismsg)
        {
            final int endNickIndex = whoismsg.indexOf(' ');
            final int endUserIndex = whoismsg.indexOf(' ', endNickIndex + 1);
            final int endHostIndex = whoismsg.indexOf(' ', endUserIndex + 1);
            final String user =
                whoismsg.substring(endNickIndex + 1, endUserIndex);
            final String host =
                whoismsg.substring(endUserIndex + 1, endHostIndex);
            IdentityManager.this.identity.setHost(host);
            IdentityManager.this.identity.setUser(user);
            LOGGER
                .debug(String.format("Current identity: %s!%s@%s",
                    IdentityManager.this.connectionState.getNickname(), user,
                    host));
        }
    }

    /**
     * Storage container for identity components.
     *
     * IRC identity components user and host are stored. The nick name component
     * isn't stored, because it changes too frequently. When getting the
     * identity string, the nick name component is provided at calling time.
     *
     * @author Danny van Heumen
     */
    private static final class Identity
    {
        /**
         * User name.
         */
        private String user = null;

        /**
         * Host name.
         */
        private String host = null;

        /**
         * Set user.
         *
         * @param user the new user
         */
        private void setUser(final String user)
        {
            this.user = user;
        }

        /**
         * Set host.
         *
         * @param host the new host
         */
        private void setHost(final String host)
        {
            this.host = host;
        }

        /**
         * Get identity string.
         *
         * @param currentNick the current nick
         * @return returns identity string
         */
        private String getString(final String currentNick)
        {
            return String.format("%s!%s@%s", currentNick, this.user, this.host);
        }
    }
}
