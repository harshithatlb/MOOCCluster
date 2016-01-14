package poke.server.management;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import poke.server.ServerInitializer;
import poke.server.conf.ServerConf;
import poke.server.managers.ElectionManager;
import poke.server.managers.HeartbeatData;
import poke.server.managers.HeartbeatManager;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by varuna on 12/6/15.
 */
public class SystemStateChecker implements Runnable {


        public SystemStateChecker() {

        }

        public void run() {
            // construct boss and worker threads (num threads = number of cores)
            while(true) {
                //System.out.println("$$$$$$$$$$$$$$$ SystemStateChecker ");
                if (HeartbeatManager.getInstance() != null) {
                    if (HeartbeatManager.getInstance().getIncomingHB() != null) {
                        for (ConcurrentHashMap.Entry<Integer, HeartbeatData> entry : HeartbeatManager.getInstance().getIncomingHB()
                                .entrySet()) {

                            long lastEntry = entry.getValue().getLastBeat();
                            if (lastEntry != 0) {

                                if (System.currentTimeMillis() - lastEntry >= 10 * 1000) {
                                    //System.out.println("Checking new logic");
                                    System.out.println("$$$$$$$$$$$$$$$ Node not Getting HB from " + entry.getKey());
                                    if (!HeartbeatManager.getInstance().getNotComingHB().contains(entry.getKey()))
                                        HeartbeatManager.getInstance().getNotComingHB().add(entry.getKey());
                                    //System.out.println("$$$$$$$$$$$$$$$ Aded from new logic");
                                }

                                // System.out.println("*******entries " + entry.getKey() +
                                // " value " + entry.getValue().getNodeId());
                            }
                        }
                    }

                    if (HeartbeatManager.getInstance().getIncomingHB().size() == HeartbeatManager.getInstance().getNotComingHB().size()) {
                        ElectionManager.getInstance().setLeaderId(null);
                        ElectionManager.getInstance().setFirstTime(2);
                    }
                }
            }


            // We can also accept connections from a other ports (e.g., isolate
            // read
            // and writes)

    }
}
