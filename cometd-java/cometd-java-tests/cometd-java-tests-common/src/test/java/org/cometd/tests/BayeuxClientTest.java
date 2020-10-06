/*
 * Copyright (c) 2008-2020 the original author or authors.
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
package org.cometd.tests;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.cometd.bayeux.Channel;
import org.cometd.bayeux.Message;
import org.cometd.bayeux.Promise;
import org.cometd.bayeux.client.ClientSession;
import org.cometd.bayeux.client.ClientSessionChannel;
import org.cometd.client.BayeuxClient;
import org.eclipse.jetty.util.BlockingArrayQueue;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

public class BayeuxClientTest extends AbstractClientServerTest {
    public BayeuxClientTest(Transport transport) {
        super(transport);
    }

    @Before
    public void setUp() throws Exception {
        startServer(serverOptions());
    }

    @Test
    public void testIPv6Address() throws Exception {
        Assume.assumeTrue(ipv6Available());

        cometdURL = cometdURL.replace("localhost", "[::1]");

        BayeuxClient client = newBayeuxClient();
        client.handshake();

        Assert.assertTrue(client.waitFor(5000, BayeuxClient.State.CONNECTED));

        // Allow long poll to establish
        Thread.sleep(1000);

        disconnectBayeuxClient(client);
    }

    @Test
    public void testBatchingAfterHandshake() throws Exception {
        BayeuxClient client = newBayeuxClient();
        AtomicBoolean connected = new AtomicBoolean();
        client.getChannel(Channel.META_CONNECT).addListener((ClientSessionChannel.MessageListener)(channel, message) -> connected.set(message.isSuccessful()));
        client.getChannel(Channel.META_HANDSHAKE).addListener((ClientSessionChannel.MessageListener)(channel, message) -> connected.set(false));
        client.handshake();

        String channelName = "/foo/bar";
        BlockingArrayQueue<String> messages = new BlockingArrayQueue<>();
        client.batch(() -> {
            // Subscribe and publish must be batched so that they are sent in order,
            // otherwise it's possible that the subscribe arrives to the server after the publish
            client.getChannel(channelName).subscribe((channel, message) -> {
                messages.add(channel.getId());
                messages.add(message.getData().toString());
            });
            client.getChannel(channelName).publish("hello");
        });

        Assert.assertTrue(client.waitFor(5000, BayeuxClient.State.CONNECTED));

        Assert.assertEquals(channelName, messages.poll(1, TimeUnit.SECONDS));
        Assert.assertEquals("hello", messages.poll(1, TimeUnit.SECONDS));

        disconnectBayeuxClient(client);
    }

    @Test
    public void testMessageWithoutChannel() {
        BayeuxClient client = newBayeuxClient();
        client.addExtension(new ClientSession.Extension() {
            @Override
            public void outgoing(ClientSession session, Message.Mutable message, Promise<Boolean> promise) {
                message.remove(Message.CHANNEL_FIELD);
                promise.succeed(true);
            }
        });

        client.handshake();
        Assert.assertTrue(client.waitFor(5000, BayeuxClient.State.DISCONNECTED));

        disconnectBayeuxClient(client);
    }

    @Test
    public void loadTest() throws Exception {
        boolean stress = Boolean.getBoolean("STRESS");
        Random random = new Random();

        int rooms = stress ? 100 : 10;
        int publish = stress ? 4000 : 100;
        int batch = stress ? 10 : 2;
        int pause = stress ? 50 : 10;
        BayeuxClient[] clients = new BayeuxClient[stress ? 500 : 2 * rooms];

        AtomicInteger connections = new AtomicInteger();
        AtomicInteger received = new AtomicInteger();

        for (int i = 0; i < clients.length; i++) {
            AtomicBoolean connected = new AtomicBoolean();
            BayeuxClient client = newBayeuxClient();
            String room = "/channel/" + (i % rooms);
            clients[i] = client;

            client.getChannel(Channel.META_HANDSHAKE).addListener((ClientSessionChannel.MessageListener)(channel, message) -> {
                if (connected.getAndSet(false)) {
                    connections.decrementAndGet();
                }

                if (message.isSuccessful()) {
                    client.getChannel(room).subscribe((c, m) -> received.incrementAndGet());
                }
            });

            client.getChannel(Channel.META_CONNECT).addListener((ClientSessionChannel.MessageListener)(channel, message) -> {
                if (!connected.getAndSet(message.isSuccessful())) {
                    connections.incrementAndGet();
                }
            });

            clients[i].handshake();
            Assert.assertTrue(client.waitFor(5000, BayeuxClient.State.CONNECTED));
        }

        Assert.assertEquals(clients.length, connections.get());

        long start0 = System.nanoTime();
        for (int i = 0; i < publish; i++) {
            int sender = random.nextInt(clients.length);
            String channel = "/channel/" + random.nextInt(rooms);

            String data = "data from " + sender + " to " + channel;
            clients[sender].getChannel(channel).publish(data);

            if (i % batch == (batch - 1)) {
                Thread.sleep(pause);
            }
        }

        int expected = clients.length * publish / rooms;

        long start = System.nanoTime();
        while (received.get() < expected && TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start) < 10) {
            Thread.sleep(100);
        }
        logger.info("{} m/s", (received.get() * 1000 * 1000 * 1000L) / (System.nanoTime() - start0));

        Assert.assertEquals(expected, received.get());

        for (BayeuxClient client : clients) {
            Assert.assertTrue(client.disconnect(1000));
        }
    }
}