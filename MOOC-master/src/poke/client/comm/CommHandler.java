/*
 * copyright 2014, gash
 * 
 * Gash licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package poke.client.comm;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import poke.comm.App.Request;

import com.google.protobuf.GeneratedMessage;

public class CommHandler extends SimpleChannelInboundHandler<Request> {
	protected static Logger logger = LoggerFactory.getLogger("connect");
	protected ConcurrentMap<String, CommListener> listeners = new ConcurrentHashMap<String, CommListener>();
	private volatile Channel channel;
	private GeneratedMessage firstMessage;

	public CommHandler(GeneratedMessage msg) {
		firstMessage = msg;
	}
	
	public CommHandler() {
		//firstMessage = msg;
	}

	/**
	 * messages pass through this method. We use a blackbox design as much as
	 * possible to ensure we can replace the underlining communication without
	 * affecting behavior.
	 * 
	 * @param msg
	 * @return
	 */
	/*
	@Override 
  public void channelActive(ChannelHandlerContext ctx) {
	         // Send the first message if this handler is a client-side handler.
		System.out.println(" Came to channelActive");
		ChannelFuture cf = ctx.writeAndFlush(firstMessage);
		if (cf.isDone() && !cf.isSuccess()) {
			logger.error("@@@@@@@@@@@@@@@@@@@@@2failed to poke!");
			System.out.println("channelActive failed to poke!");
			System.out.println("cf.isDone() "+cf.isDone());
			System.out.println("cf.isSuccess() "+cf.isSuccess());
			//return false;
		}
	      }
	*/
	public boolean send(GeneratedMessage msg, Channel ch) {


		// TODO a queue is needed to prevent overloading of the socket
		// connection. For the demonstration, we don't need it
		channel = ch;
		ChannelFuture cf= channel.writeAndFlush(msg);   //writeAndFlush(msg, true);
		//channel.flush();
		System.out.println(" Flushed");

		if (cf.isDone() && !cf.isSuccess()) {
			logger.error("@@@@@@@@@@@@@@@@@@failed to poke!");
			return false;
		}

		return true;
	}

	/**
	 * Notification registration. Classes/Applications receiving information
	 * will register their interest in receiving content.
	 * 
	 * Note: Notification is serial, FIFO like. If multiple listeners are
	 * present, the data (message) is passed to the listener as a mutable
	 * object.
	 * 
	 * @param listener
	 */
	public void addListener(CommListener listener) {
		if (listener == null)
			return;

		listeners.putIfAbsent(listener.getListenerID(), listener);
	}

	/**
	 * a message was received from the server. Here we dispatch the message to
	 * the client's thread pool to minimize the time it takes to process other
	 * messages.
	 * 
	 * @param ctx
	 *            The channel the message was received from
	 * @param msg
	 *            The message
	 */
	@Override
	protected void channelRead0(ChannelHandlerContext ctx, Request msg) throws Exception {
		for (String id : listeners.keySet()) {
			CommListener cl = listeners.get(id);

			// TODO this may need to be delegated to a thread pool to allow
			// async processing of replies
			cl.onMessage(msg);
		}
	}
	
	
}
