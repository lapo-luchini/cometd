package org.cometd.bayeux.server;

import org.cometd.bayeux.Bayeux;
import org.cometd.bayeux.BayeuxListener;
import org.cometd.bayeux.client.ClientSession;
import org.cometd.bayeux.client.SessionChannel;


/* ------------------------------------------------------------ */
/**
 * The Bayeux Server interface.
 * <p>
 * An instance of the BayeuxServer interface is available to 
 * webapplications from via the {@value #ATTRIBUTE} attribute
 * of the {@link javax.servlet.ServletContext}.
 * </p>
 * <p>The BayeuxServer API gives access to the 
 * {@link ServerSession}s via the {@link #getSession(String)}
 * method.  It also allows new {@link LocalSession} to be 
 * created within the server using the {@link #newLocalSession(String)}
 * method.
 * </p>
 * {@link ServerChannel} instances may be accessed via the 
 * {@link #getChannel(String)} method, but the server has 
 * no direct relationship with {@link SessionChannel}s or
 * {@link ClientSession}.  If subscription semantics is required, then
 * the {@link #newLocalSession(String)} method should be used to
 * create a {@link LocalSession} that can subscribe and publish
 * like a remote bayeux session.
 * 
 */
public interface BayeuxServer extends Bayeux
{
    /* ------------------------------------------------------------ */
    /** ServletContext attribute name used to obtain the Bayeux object */
    public static final String ATTRIBUTE ="org.cometd.bayeux";


    /* ------------------------------------------------------------ */
    /**
     * Adds the given extension to this bayeux object.
     * @param extension the extension to add
     * @see #removeExtension(Extension)
     */
    void addExtension(Extension extension);

    /* ------------------------------------------------------------ */
    /**
     * @param listener
     */
    void addListener(BayeuxServerListener listener);
    
    /* ------------------------------------------------------------ */
    /**
     * @param listener
     */
    void removeListener(BayeuxServerListener listener);

    /* ------------------------------------------------------------ */
    /**
     * @param channelId
     * @return
     */
    ServerChannel getChannel(String channelId);
    
    /* ------------------------------------------------------------ */
    /**
     * @param channelId
     * @param create
     * @return
     */
    ServerChannel getChannel(String channelId, boolean create);
    
    /* ------------------------------------------------------------ */
    /** Get a server session my ID
     * @param clientId the ID
     * @return the server session or null if no such valid session exists.
     */
    ServerSession getSession(String clientId);


    /* ------------------------------------------------------------ */
    /** Create a local session.
     * A Local session is a server-side ClientSession.  This allows the 
     * server to have special clients resident within the same JVM.
     * @param idHint A hint to be included in the unique client ID.
     * @return A new LocalSession
     */
    LocalSession newLocalSession(String idHint);
    

    /* ------------------------------------------------------------ */
    /** Create a new Message.
     * @return A new or recycled message instance.
     */
    ServerMessage.Mutable newMessage();
    

    /* ------------------------------------------------------------ */
    /**
     * @return
     */
    public SecurityPolicy getSecurityPolicy();

    /* ------------------------------------------------------------ */
    /**
     * @param securityPolicy
     */
    public void setSecurityPolicy(SecurityPolicy securityPolicy);


    /* ------------------------------------------------------------ */
    /**
     * Get the current transport for the current thread.
     * A transport object will be: <ul>
     * <li>A javax.servlet.http.HttpServletRequest instance for a HTTP transport
     * <li>An org.eclipse.jetty.websocket.WebSocket instance for WebSocket transports
     * </ul>
     */
    public Object getCurrentTransport();

    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    /**
     */
    interface BayeuxServerListener extends BayeuxListener
    {}

    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    /** A Channel Initializer Listener.
     * Channel Initializer listeners are called atomically during 
     * Channel creation to obtain a ServerChannel listener to add to
     * the channel before any publishes or subscribes can occur.
     * <p>
     * Any attempt to call {@link BayeuxServer#getChannel(String)}
     * from a {@link ChannelInitializerListener} will result in an 
     * {@link IllegalStateException} after a delay.
     */
    public interface ChannelInitializerListener extends BayeuxServerListener
    {
        public ServerChannel.ServerChannelListener getServerChannelListener(String channelId);
    };

    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    /**
     */
    public interface ChannelListener extends BayeuxServerListener
    {
        public void channelAdded(ServerChannel channel);
        public void channelRemoved(String channelId);
    };

    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    /**
     */
    public interface SessionListener extends BayeuxServerListener
    {
        public void sessionAdded(ServerSession session);
        public void sessionRemoved(ServerSession session,boolean timedout);
    }

    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    public interface SubscriptionListener extends BayeuxServerListener
    {
        public void subscribed(ServerSession session, ServerChannel channel);
        public void unsubscribed(ServerSession session, ServerChannel channel);
    }
    

    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    /**
     * <p>Extension API for bayeux server.</p>
     * <p>Implementations of this interface allow to modify incoming and outgoing messages
     * respectively just before and just after they are handled by the implementation,
     * either on client side or server side.</p>
     * <p>Extensions are be registered in order and one extension may allow subsequent
     * extensions to process the message by returning true from the callback method, or
     * forbid further processing by returning false.</p>
     *
     * @see BayeuxServer#addExtension(Extension)
     */
    public interface Extension
    {
        /**
         * Callback method invoked every time a normal message is incoming.
         * @param from the session that sent the message
         * @param message the incoming message
         * @return true if message processing should continue, false if it should stop
         */
        boolean rcv(ServerSession from, ServerMessage.Mutable message);

        /**
         * Callback method invoked every time a meta message is incoming.
         * @param from the session that sent the message
         * @param message the incoming meta message
         * @return true if message processing should continue, false if it should stop
         */
        boolean rcvMeta(ServerSession from, ServerMessage.Mutable message);

        /**
         * Callback method invoked every time a normal message is outgoing.
         * @param to the session receiving the message, or null for a publish
         * @param message the outgoing message
         * @return true if message processing should continue, false if it should stop
         */
        boolean send(ServerMessage.Mutable message);

        /**
         * Callback method invoked every time a meta message is outgoing.
         * @param to the session receiving the message
         * @param message the outgoing meta message
         * @return true if message processing should continue, false if it should stop
         */
        boolean sendMeta(ServerSession to, ServerMessage.Mutable message);
    }
}
