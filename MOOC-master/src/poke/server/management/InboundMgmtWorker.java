/*
 * copyright 2012, gash
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
package poke.server.management;

import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import poke.core.Mgmt;
import poke.core.Mgmt.Management;
import poke.server.conf.ServerConf;
import poke.server.management.ManagementQueue.ManagementQueueEntry;
import poke.server.managers.ElectionManager;
import poke.server.managers.HeartbeatData;
import poke.server.managers.HeartbeatManager;
import poke.server.managers.JobManager;
import poke.server.managers.NetworkManager;

/**
 * The inbound management worker is the cortex for all work related to the
 * Health and Status (H&S) of the node.
 * 
 * Example work includes processing job bidding, elections, network connectivity
 * building. An instance of this worker is blocked on the socket listening for
 * events. If you want to approximate a timer, executes on a consistent interval
 * (e.g., polling, spin-lock), you will have to implement a thread that injects
 * events into this worker's queue.
 * 
 * HB requests to this node are NOT processed here. Nodes making a request to
 * receive heartbeats are in essence requesting to establish an edge (comm)
 * between two nodes. On failure, the connecter must initiate a reconnect - to
 * produce the heartbeatMgr.
 * 
 * On loss of connection: When a connection is lost, the emitter will not try to
 * establish the connection. The edge associated with the lost node is marked
 * failed and all outbound (enqueued) messages are dropped (TBD as we could
 * delay this action to allow the node to detect and re-establish the
 * connection).
 * 
 * Connections are bi-directional (reads and writes) at this time.
 * 
 * @author gash
 * 
 */
public class InboundMgmtWorker extends Thread {
	protected static Logger logger = LoggerFactory.getLogger("management");

	int workerId;
	boolean forever = true;
	long HeartBeatTime = 0L;
	int electionTimeOut = 100;
	//long HeartBeattime = 0L;
	long electionExpiryTime;
	int firstTime = 2;
	ServerConf conf;
	Integer leaderNodeId;



	public InboundMgmtWorker(ThreadGroup tgrp, int workerId,ServerConf conf) {
		super(tgrp, "inbound-mgmt-" + workerId);
		this.workerId = workerId;
		this.conf=conf;
		this.electionTimeOut = conf.getElecTimeOut();
		this.electionExpiryTime = electionTimeOut*1000 + System.currentTimeMillis();

		System.out.println("************Set election timeout in inbound mgmt queue as " + electionExpiryTime + "  " + electionTimeOut);
		if (ManagementQueue.outbound == null)
			throw new RuntimeException("connection worker detected null queue");
	}


	@Override
	public void run() {
		//System.out.println("------------InboundMgmtWorker started here------------------ ");
		
		while (true) {


			//timer logic

			
			
			// System.out.println("***********State of Incoming HB map************");
			if (HeartbeatManager.getInstance() != null){
			if (HeartbeatManager.getInstance().getIncomingHB() != null){
						for (ConcurrentHashMap.Entry<Integer, HeartbeatData> entry : HeartbeatManager.getInstance().getIncomingHB()
								.entrySet()) {

							long lastEntry = entry.getValue().getLastBeat();
							if (lastEntry != 0) {
								
								if (System.currentTimeMillis() - lastEntry >= 10 * 1000) {
									//System.out.println("Checking new logic");
									System.out.println("$$$$$$$$$$$$$$$ Node not Getting HB from "+entry.getKey());
									if (!HeartbeatManager.getInstance().getNotComingHB().contains(entry.getKey()))
										HeartbeatManager.getInstance().getNotComingHB().add(entry.getKey());
									//System.out.println("$$$$$$$$$$$$$$$ Aded from new logic");
								}

								// System.out.println("*******entries " + entry.getKey() +
								// " value " + entry.getValue().getNodeId());
							}
						}
			}
		
						if (HeartbeatManager.getInstance().getIncomingHB().size() == HeartbeatManager.getInstance().getNotComingHB().size())
						{
							ElectionManager.getInstance().setLeaderId(null);
							ElectionManager.getInstance().setFirstTime(2);
						}
			}

			
			if (!forever && ManagementQueue.inbound.size() == 0){
				System.out.println("************BReak from queue due to no message");
				break;}

			try {
				// block until a message is enqueued
				//System.out.println("Should print----");
				ManagementQueueEntry msg = ManagementQueue.inbound.take();
				//System.out.println("msg-------------------- "+msg.req.toString());

				if (logger.isDebugEnabled())
					logger.debug("Inbound management message received");

				Management mgmt = (Management) msg.req;
				if (mgmt.hasBeat()) {
					/**
					 * Incoming: this is from a node we requested to create a
					 * connection (edge) to. In other words, we need to track
					 * that this connection is healthy by receiving HB messages.
					 *
					 * Incoming are connections this node establishes, which is
					 * handled by the HeartbeatPusher.
					 */
					//System.out.println("----------Checking heartbeat----------------------");
					HeartbeatManager.getInstance().processRequest(mgmt,msg.channel);
					leaderNodeId = ElectionManager.getInstance().whoIsTheLeader();
					System.out.println(" &&&&&&&&&&&&&&&&current leadernodeid       " + leaderNodeId);
					if (leaderNodeId != null) {
						if (mgmt.getHeaderOrBuilder().getOriginator() == leaderNodeId) {
							this.electionExpiryTime = electionTimeOut*1000 + System.currentTimeMillis();
						}
					}

					HeartBeatTime=System.currentTimeMillis();

					/**
					 * If we have a network (more than one node), check to see
					 * if a election manager has been declared. If not, start an
					 * election.
					 * 
					 * The flaw to this approach is from a bootstrap PoV.
					 * Consider a network of one node (myself), an event-based
					 * monitor does not detect the leader is myself. However, I
					 * cannot allow for each node joining the network to cause a
					 * leader election.
					 */
					if (electionExpiryTime <= System.currentTimeMillis()) {
						electionExpiryTime = electionTimeOut * 1000 + System.currentTimeMillis();
						//System.out.println("************* checking of conf is null" + conf);
						//System.out.println("************* checking of getnodeis null" + conf.getNodeId());
						//System.out.println("************* checking of " + conf.getNodeId());

						leaderNodeId = ElectionManager.getInstance().whoIsTheLeader();
						System.out.println(" previous node leadernodeid" + leaderNodeId);

						if (leaderNodeId != null) {
							if (leaderNodeId != conf.getNodeId()) {
								System.out.println("*************Election Started by node " + conf.getNodeId());
								ElectionManager.getInstance().startElection();

							}
						} else{
							ElectionManager.getInstance().startElection();
						}
					} else
					{
						ElectionManager.getInstance().assessCurrentState(mgmt);
					}

				} else if (mgmt.hasElection()) {
					ElectionManager.getInstance().processRequest(mgmt);
				} else if (mgmt.hasGraph()) {
					NetworkManager.getInstance().processRequest(mgmt, msg.channel);
//					System.out.println("******************** Inside Network Manager. Checking graph action" + Mgmt.Network.NetworkAction.NODEJOIN_VALUE);
//					if(leaderNodeId != null) {
//						if (mgmt.getGraph().getAction().getNumber() == Mgmt.Network.NetworkAction.NODEJOIN_VALUE) {
//                               ElectionManager.getInstance().respondToWhoIsTheLeader(mgmt);
//						}
//						System.out.println(" ******current leadernodeid" + leaderNodeId);
//					}
				} else
					logger.error("Unknown management message");

			} catch (InterruptedException ie) {
				break;
			} catch (Exception e) {
				logger.error("Unexpected processing failure, halting worker.", e);
				break;
			}
		}

		if (!forever) {
			logger.info("connection queue closing");
		}
	}
}
