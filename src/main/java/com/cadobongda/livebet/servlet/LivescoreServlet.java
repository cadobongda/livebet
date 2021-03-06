/*
 * Copyright (C) 2012 eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.cadobongda.livebet.servlet;

import com.cadobongda.livenews.messaging.JMSProvider;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.RootContainer;
import java.io.IOException;
import java.io.Writer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;
import javax.servlet.AsyncContext;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebInitParam;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author <a href="hoang281283@gmail.com">Minh Hoang TO</a>
 * @date 5/18/12
 */
@WebServlet(name = "livescore", asyncSupported = true,
   initParams = {
      @WebInitParam(name = "topic", value = "/topic/liveScoreTopic"),
      @WebInitParam(name = "portalContainer", value = "portal")
   },
   urlPatterns = {"/livescore"}
)
public class LivescoreServlet extends HttpServlet implements MessageListener
{

   private final Queue<AsyncContext> asyncContexts = new ConcurrentLinkedQueue<AsyncContext>();

   private volatile boolean processingPush = false;

   private TopicSubscriber topicSubscriber;

   @Override
   public void init(ServletConfig config) throws ServletException
   {
      super.init(config);

      String topicName = config.getInitParameter("topic");
      String portalContainer = config.getInitParameter("portalContainer");
      try
      {
         initTopicSubscriber(topicName, portalContainer);
      }
      catch (JMSException jmsEx)
      {
         jmsEx.printStackTrace();
      }
   }

   @Override
   protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
   {
      super.doGet(req, resp);

      if (!processingPush)
      {
         AsyncContext asyncContext = req.startAsync();
         asyncContexts.add(asyncContext);
      }
   }

   public void onMessage(Message message)
   {
      processingPush = true;
      try
      {
         String newScore = message.getStringProperty("homeTeam") + " " + message.getStringProperty("score") + " " + message.getStringProperty("awayTeam");
         AsyncContext waitingCtx;
         while ((waitingCtx = asyncContexts.poll()) != null)
         {
            HttpServletResponse response = (HttpServletResponse)waitingCtx.getResponse();
            try
            {
               Writer writer = response.getWriter();
               writer.write(newScore);
               writer.flush();
               writer.close();
            }
            catch (IOException ioEx)
            {
            }
            finally
            {
               //waitingCtx.complete();
            }
         }
      }
      catch(JMSException jmsEx)
      {
      }
      finally
      {
         processingPush = false;
      }
   }

   private void initTopicSubscriber(String topicName, String portalContainer) throws JMSException
   {
      PortalContainer pContainer = RootContainer.getInstance().getPortalContainer(portalContainer);
      JMSProvider jmsProvider = (JMSProvider)pContainer.getComponentInstanceOfType(JMSProvider.class);

      Connection connection = jmsProvider.createConnection();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

      Topic topic = jmsProvider.getTopic(topicName);
      topicSubscriber = session.createDurableSubscriber(topic, "LivescoreServlet");
      topicSubscriber.setMessageListener(this);
      connection.start();
   }
}
