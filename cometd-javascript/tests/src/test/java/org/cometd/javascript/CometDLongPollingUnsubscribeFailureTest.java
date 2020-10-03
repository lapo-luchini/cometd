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
package org.cometd.javascript;

import java.io.IOException;
import java.util.EnumSet;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.Assert;
import org.junit.Test;

public class CometDLongPollingUnsubscribeFailureTest extends AbstractCometDLongPollingTest {
    @Override
    protected void customizeContext(ServletContextHandler context) throws Exception {
        super.customizeContext(context);
        UnsubscribeThrowingFilter filter = new UnsubscribeThrowingFilter();
        FilterHolder filterHolder = new FilterHolder(filter);
        context.addFilter(filterHolder, cometdServletPath + "/*", EnumSet.of(DispatcherType.REQUEST));
    }

    @Test
    public void testUnsubscribeFailure() throws Exception {
        evaluateScript("var readyLatch = new Latch(1);");
        Latch readyLatch = javaScript.get("readyLatch");
        evaluateScript("cometd.addListener('/meta/connect', function() { readyLatch.countDown(); });");
        evaluateScript("cometd.init({url: '" + cometdURL + "', logLevel: '" + getLogLevel() + "'})");
        Assert.assertTrue(readyLatch.await(5000));

        // Wait for the long poll to establish
        Thread.sleep(1000);

        evaluateScript("var subscribeLatch = new Latch(1);");
        Latch subscribeLatch = javaScript.get("subscribeLatch");
        evaluateScript("cometd.addListener('/meta/subscribe', function() { subscribeLatch.countDown(); });");
        evaluateScript("var subscription = cometd.subscribe('/echo', function() { subscribeLatch.countDown(); });");
        Assert.assertTrue(subscribeLatch.await(5000));

        evaluateScript("var unsubscribeLatch = new Latch(1);");
        Latch unsubscribeLatch = javaScript.get("unsubscribeLatch");
        evaluateScript("var failureLatch = new Latch(1);");
        Latch failureLatch = javaScript.get("failureLatch");
        evaluateScript("cometd.addListener('/meta/unsubscribe', function() { unsubscribeLatch.countDown(); });");
        evaluateScript("cometd.addListener('/meta/unsuccessful', function() { failureLatch.countDown(); });");
        evaluateScript("cometd.unsubscribe(subscription);");
        Assert.assertTrue(unsubscribeLatch.await(5000));
        Assert.assertTrue(failureLatch.await(5000));

        // Be sure there is no backoff
        evaluateScript("var backoff = cometd.getBackoffPeriod();");
        int backoff = ((Number)javaScript.get("backoff")).intValue();
        Assert.assertEquals(0, backoff);

        disconnect();
    }

    public static class UnsubscribeThrowingFilter implements Filter {
        private int messages;

        @Override
        public void init(FilterConfig filterConfig) throws ServletException {
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
            doFilter((HttpServletRequest)request, (HttpServletResponse)response, chain);
        }

        private void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
            String uri = request.getRequestURI();
            if (!uri.endsWith("/handshake") && !uri.endsWith("/connect")) {
                ++messages;
            }
            // The second non-handshake and non-connect message will be the unsubscribe, throw
            if (messages == 2) {
                throw new IOException();
            }
            chain.doFilter(request, response);
        }

        @Override
        public void destroy() {
        }
    }
}
