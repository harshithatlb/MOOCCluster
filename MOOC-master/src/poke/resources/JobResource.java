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
package poke.resources;

import io.netty.channel.Channel;
import poke.Communication.CommunicationConnection;
import poke.comm.App;
import poke.comm.App.Request;
import poke.server.Server;
import poke.server.conf.NodeDesc;
import poke.server.conf.ServerConf;
import poke.server.managers.ElectionManager;
import poke.server.managers.HeartbeatData;
import poke.server.managers.HeartbeatManager;
import poke.server.resources.Resource;
import poke.server.resources.ResourceFactory;
import poke.server.resources.ResourceUtil;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class JobResource implements Resource {

	ServerConf conf = ResourceFactory.cfg;
	Integer leaderID = ElectionManager.getInstance().whoIsTheLeader();
	String email_ = "";


	public Request process(Request request) {
		long owner = request.getBody().getJobOp().getData().getOwnerId();
		System.out.println("Job resource owner :" + owner);

		Request.Builder reply = Request.newBuilder();

		//Header
		reply.setHeader(ResourceUtil.buildHeaderFrom(request.getHeader(), App.PokeStatus.SUCCESS, null));

		// payload
		App.Payload.Builder pb = App.Payload.newBuilder();
		App.JobStatus.Builder jsb = App.JobStatus.newBuilder();
		jsb.setJobId(request.getBody().getJobStatus().getJobId());
		jsb.setJobState(request.getBody().getJobStatus().getJobState());
		jsb.setStatus(App.PokeStatus.SUCCESS);


		String operation = request.getBody().getJobOp().getData().getNameSpace();

		//send the request to the other cluster for getting their data and respond back to teh client
		if(operation.equals("intercluster"))
		{
			App.JobDesc.Builder jobDesc = App.JobDesc.newBuilder();
			jobDesc.setNameSpace("listcourses");
			jobDesc.setOwnerId(1234);
			jobDesc.setJobId("100");
			jobDesc.setStatus(App.JobDesc.JobCode.JOBRECEIVED);

			System.out.println("Cluster Host and port!!!");
			int port,j=0;
			String host, course , description, name;
			Request interclusterRequest = buildInterclusterRequest();

			for (Map.Entry<Integer, NodeDesc> cluster : conf.getAdjacentCluster().getAdjacentNodes().entrySet())
			{
				port = cluster.getValue().getPort();
				host = cluster.getValue().getHost();
				name = cluster.getValue().getNodeName();
				System.out.println("Host :" + host + "Port : " + port);

				CommunicationConnection connection = new CommunicationConnection(host, port);
				Request res = connection.send(interclusterRequest);
				if(res != null)
				{
					int count = res.getBody().getJobStatus().getData(0).getOptions().getNodeCount();
					int i = 0;

					App.NameValueSet.Builder nv = App.NameValueSet.newBuilder();
					nv.setNodeType(App.NameValueSet.NodeType.VALUE);
					nv.setName(name + ":");
					App.NameValueSet.Builder nvc = App.NameValueSet.newBuilder();

					for (int k = 0; k < count; k++) {
						course = res.getBody().getJobStatus().getData(0).getOptions().getNode(k).getName();
						description = res.getBody().getJobStatus().getData(0).getOptions().getNode(k).getValue();
						System.out.println("Course :" + course + "Description:" + description);
						nvc.setNodeType(App.NameValueSet.NodeType.VALUE);
						nvc.setName(course);
						nvc.setValue(description);
						nv.addNode(i++, nvc.build());
					}

					jobDesc.setOptions(nv.build());
					jsb.addData(j++, jobDesc.build());
				}
			}
		}

		//process the request received from the other cluster
		if(operation.equals("getInterclusterData"))
		{
			App.NameValueSet.Builder nvc = App.NameValueSet.newBuilder();
			App.NameValueSet.Builder nv = App.NameValueSet.newBuilder();
			nv.setNodeType(App.NameValueSet.NodeType.VALUE);


			App.JobDesc.Builder jobDesc = App.JobDesc.newBuilder();
			jobDesc.setNameSpace("Cluster Data of " + conf.getNodeId());
			jobDesc.setOwnerId(conf.getNodeId());
			jobDesc.setJobId("100");
			jobDesc.setStatus(App.JobDesc.JobCode.JOBRECEIVED);

			int i = 0;
			for (Map.Entry<String, String> entry : Server.coursesMap.entrySet()) {
				nvc.setNodeType(App.NameValueSet.NodeType.VALUE);
				nvc.setName("CMPE" + entry.getKey());
				nvc.setValue(entry.getValue());
				nv.addNode(i++, nvc.build());
			}

			jobDesc.setOptions(nv.build());
			jsb.addData(0, jobDesc.build());
		}

		//list all the courses present in the cluster
		if (operation.equals("listcourses")) {
			App.NameValueSet.Builder nvc = App.NameValueSet.newBuilder();
			App.NameValueSet.Builder nv = App.NameValueSet.newBuilder();
			nv.setNodeType(App.NameValueSet.NodeType.VALUE);


			App.JobDesc.Builder jobDesc = App.JobDesc.newBuilder();
			jobDesc.setNameSpace("listcourses");
			jobDesc.setOwnerId(1234);
			jobDesc.setJobId("100");
			jobDesc.setStatus(App.JobDesc.JobCode.JOBRECEIVED);

			int i = 0;
			for (Map.Entry<String, String> entry : Server.coursesMap.entrySet()) {
				nvc.setNodeType(App.NameValueSet.NodeType.VALUE);
				nvc.setName("CMPE" + entry.getKey());
				nvc.setValue(entry.getValue());
				nv.addNode(i++, nvc.build());
			}

			jobDesc.setOptions(nv.build());
			jsb.addData(0, jobDesc.build());
		}

		// When a node joins back the cluster, it asks the leader for the latest commits if any
		// The leader sends all the commits that requesting servers does not have based on the commit number
		// The commmit numbers are stored along with the user and course registration  in the userTocourses file in runtime/ring folder
		if (operation.equals("latest"))
		{
			//TODO: create latest request with the latest commitID
			if (conf.getNodeId() == leaderID) {
				int nodesLatestCommit = Integer.parseInt(request.getBody().getJobOp().getData().getOptions().getNode(0).getValue());
				System.out.println("nodes latest commit :" + nodesLatestCommit);
				System.out.println("Leaders latest Commit :" + Server.getCommitId());

				App.NameValueSet.Builder nvc = App.NameValueSet.newBuilder();
				App.NameValueSet.Builder nv = App.NameValueSet.newBuilder();
				nv.setNodeType(App.NameValueSet.NodeType.VALUE);
				nv.setName("noData");

				App.JobDesc.Builder jobDesc = App.JobDesc.newBuilder();
				jobDesc.setNameSpace("latest commits from the leader");
				jobDesc.setOwnerId(1234);
				jobDesc.setJobId("100");
				jobDesc.setStatus(App.JobDesc.JobCode.JOBRECEIVED);

				int i = 0;
				for (Map.Entry<Integer, ArrayList<String>> entry : Server.userToCoursesMap.entrySet())
				{

					if (entry.getKey() >  nodesLatestCommit)
					{
						nv.setName("hasData");
						nvc.setNodeType(App.NameValueSet.NodeType.VALUE);
						nvc.setName(String.valueOf(entry.getKey()));
						nvc.setValue(entry.getValue().get(0) + ":" + entry.getValue().get(1));
						nv.addNode(i++, nvc.build());
					}

				}

				jobDesc.setOptions(nv.build());
				jsb.addData(0,jobDesc.build());
			}
		}

		//List all the registered courses for the signed-in user
		if (operation.equals("listregisteredcourses")) {
			App.NameValueSet.Builder nvc = App.NameValueSet.newBuilder();
			App.NameValueSet.Builder nv = App.NameValueSet.newBuilder();
			nv.setNodeType(App.NameValueSet.NodeType.VALUE);


			String email = request.getBody().getJobOp().getData().getOptions().getNode(0).getValue();
			System.out.println("*******************email got"+email);
			App.JobDesc.Builder jobDesc = App.JobDesc.newBuilder();
			jobDesc.setNameSpace("listregisteredcourses");
			jobDesc.setOwnerId(1234);
			jobDesc.setJobId("100");
			jobDesc.setStatus(App.JobDesc.JobCode.JOBRECEIVED);

			// get registered courses for the user
			for (Map.Entry<Integer, ArrayList<String>> entry : Server.userToCoursesMap.entrySet()) {
				System.out.println("val from map******************** " + entry.getValue().get(0));
				if(entry.getValue().get(0).equals(email)){
					System.out.println("********************searching for user's courses");
					nvc.setNodeType(App.NameValueSet.NodeType.VALUE);
					nvc.setName("CMPE" );
					nvc.setValue(entry.getValue().get(1));
					nv.addNode(nvc.build());
				}
			}

			jobDesc.setOptions(nv.build());
			jsb.addData(0, jobDesc.build());

		}

		// Get the course description for a course
		if (operation.equals("getdescription")) {
			String course = request.getBody().getJobOp().getData().getOptions().getValue();

			App.JobDesc.Builder jobDesc = App.JobDesc.newBuilder();
			if(Server.coursesMap.get(course) == null)
			{
				jobDesc.setNameSpace(course + " not present");
			}
			else
			{
				jobDesc.setNameSpace(course + Server.coursesMap.get(course));
			}

			jobDesc.setOwnerId(1234);
			jobDesc.setJobId("100");
			jobDesc.setStatus(App.JobDesc.JobCode.JOBRECEIVED);
			jsb.addData(0, jobDesc.build());

		}

		// Register for a course is a write request in the system; which is sent to the leader for broadcasting to the other nodes
		// Further , we perform 2 phase commit , where the non leader node acknowledges thato the leader that it is ready to commit ;
		// Based on the number of acknowledgements received, the leader then decides if it should broadcast the message again for final commit;
		// Atleast 2 nodes in the cluster should acknowledge that they are ready to commit ; only then the leader will send a second message for the nodes to commit else the message will not be commited
		// If non leader node recieved the message for the second time, then it would commit

		if (operation.equals("register_course")) {

			// if the ownerID is 1111 , then it means the request is built by the leader
			// The other nodes in the cluster would commit the request sent by the leader
			// This is 2 phase commit logic for the non-leader node
			if (request.getBody().getJobOp().getData().getOwnerId() == 1111) {

				// 2 phase commit
				System.out.println("Writing to file by " + conf.getNodeId());
				String course = request.getBody().getJobOp().getData().getOptions().getNode(0).getValue();
				String email = request.getBody().getJobOp().getData().getOptions().getNode(1).getValue();
				String msgNum = request.getBody().getJobOp().getData().getOptions().getNode(2).getValue();

				// 2 phase commit's First message ; The nodes acknowledges the leader that it is ready to commit
				if (msgNum.equals("1"))
				{
					//acknowledge that I can commit
					App.JobDesc.Builder jobDesc = App.JobDesc.newBuilder();
					jobDesc.setNameSpace("readyForCommit" + conf.getNodeId());
					jobDesc.setOwnerId(conf.getNodeId());
					jobDesc.setJobId("100");
					jobDesc.setStatus(App.JobDesc.JobCode.JOBRECEIVED);
					jsb.addData(0, jobDesc.build());
				}
				// If the message is seen for the second time , then the node would commit the data
				else if (msgNum.equals("2")) {
					// full commit - complete the transaction
					Server.updateUserToCoursesMap(Server.getCommitId() + 1, email, Integer.parseInt(course));
					App.JobDesc.Builder jobDesc = App.JobDesc.newBuilder();
					jobDesc.setNameSpace("User " + email + " registered for " + course);
					jobDesc.setOwnerId(1111);
					jobDesc.setJobId("100");
					jobDesc.setStatus(App.JobDesc.JobCode.JOBRECEIVED);
					jsb.addData(0, jobDesc.build());

				}
			}
			// if the message is recieved from the client , then it should be forwarded to the leader for broadcast to other nodes
			else {
				//forwarding logic
				System.out.println("Job resource , leader : " + leaderID);
				if (conf.getNodeId() != leaderID) {

					System.out.println("Conf:" + conf.toString());
					System.out.println("conf adjacent:" + conf.getAdjacent().getNode(leaderID));
					int port = conf.getAdjacent().getNode(leaderID).getPort();
					String host = conf.getAdjacent().getNode(leaderID).getHost();
					try {
						CommunicationConnection connection = new CommunicationConnection(host, port);
						Request response = connection.send(request);
						if(response != null)
						{
							System.out.println("for response from leader and forwarding to client");
							return response;
						}
						else
						{
							System.out.println("@@@@@@@@@@@@@ Connection could not be established with the leader!");

							App.JobDesc.Builder jobDesc = App.JobDesc.newBuilder();
							jobDesc.setNameSpace("Could not establish connection with the leader");
							jobDesc.setOwnerId(1111);
							jobDesc.setJobId(request.getBody().getJobOp().getJobId());
							jobDesc.setStatus(App.JobDesc.JobCode.JOBFAILED);
							jsb.addData(0, jobDesc.build());
							pb.setJobStatus(jsb.build());
							reply.setBody(pb.build());
							return reply.build();

						}

					}
					catch(Exception e)
					{
						System.out.println("@@@@@@@@@@@@@ Connection could not be established with the leader!");
						e.printStackTrace();

						App.JobDesc.Builder jobDesc = App.JobDesc.newBuilder();
						jobDesc.setNameSpace("Could not establish connection with the leader");
						jobDesc.setOwnerId(1111);
						jobDesc.setJobId(request.getBody().getJobOp().getJobId());
						jobDesc.setStatus(App.JobDesc.JobCode.JOBFAILED);
						jsb.addData(0, jobDesc.build());
						pb.setJobStatus(jsb.build());
						reply.setBody(pb.build());
						return reply.build();

					}
				}
				else
				{
					// after the leader gets the message it would build a message similar to the message recived from client
					// but changes the owner_id to 1111 (indicating) that the message is built by the leader and
					// forwards the request to other nodes in the cluster
					System.out.println("I am the leader , building the request");
					int port;
					String host;

					int noOfvotes = conf.getNumberOfElectionVotes();
					int activeConnections = HeartbeatManager.getInstance().getOutgoingHB().size();
					CommunicationConnection[] bbc = new CommunicationConnection[activeConnections];
					Request[] response = new Request[activeConnections];
					int i = 0;
					int messageNumber;

					for (ConcurrentHashMap.Entry<Channel, HeartbeatData> entry : HeartbeatManager.getInstance().getOutgoingHB().entrySet()) {
						messageNumber = 1;
						Request forwardRequest1 = buildForwardRequest(request, messageNumber);
						port = entry.getValue().getPort();
						host = HeartbeatManager.getInstance().getIncomingHB().get(entry.getValue().getNodeId()).getHost();
						System.out.println("**************** Host :" + host + "Port : " + port + " Phase1 commit");
						try
						{
							bbc[i] = new CommunicationConnection(host, port);
							response[i] = bbc[i].send(forwardRequest1);
							if(response[i] == null){
								System.out.println("@@@@@@@@@@@@@ Connection could not be established message 1!");
							}
							i++;

						}
						catch(Exception e)
						{
							System.out.println("@@@@@@@@@@@@@ Connection could not be established message 1!");
							e.printStackTrace();
						}

					}


					// Check how many nodes are ready to commit ; Collecting consensus
					int numberofSuccessNodes = 0;
					for (Request res : response) {
						if(res != null)
						{
							try {
								System.out.println("response 1 :" + res.getBody().getJobStatus().getData(0).getNameSpace());
								if (res.getBody().getJobStatus().getData(0).getNameSpace().contains("readyForCommit")) {
									numberofSuccessNodes++;
								}
							} catch (Exception e) {
								System.out.println("@@@@ Connection could not be get response after numberofsuccess!");
								e.printStackTrace();
							}
						}
					}

					i = 0;
					// The consensus number should be greater than the 50% of active connections
					if (numberofSuccessNodes >= noOfvotes-1)
					{
						// commit finally
						for (ConcurrentHashMap.Entry<Channel, HeartbeatData> entry : HeartbeatManager.getInstance().getOutgoingHB().entrySet()) {
							messageNumber = 2;
							Request forwardRequest2 = buildForwardRequest(request, messageNumber);
							port = entry.getValue().getPort();
							host = HeartbeatManager.getInstance().getIncomingHB().get(entry.getValue().getNodeId()).getHost();
							System.out.println("**************** Host  :" + host + "Port : " + port + " Phase2 commit");
							try
							{
								Request Finalresponse = bbc[i].send(forwardRequest2);
								if(Finalresponse == null){
									System.out.println("@@@@@@@@@@@@@ Connection could not be established message 2!");
								}
								i++;
							}
							catch (Exception e)
							{
								System.out.println("@@@@@@@@@@@@@ Connection could not be established message 2!");
								e.printStackTrace();
							}
						}
					}
					else // if majority consensus is not recieved then , commit does not succeed
					{
						App.JobDesc.Builder jobDesc = App.JobDesc.newBuilder();
						jobDesc.setNameSpace("Could not commit successfully!!");
						jobDesc.setOwnerId(1111);
						jobDesc.setJobId(request.getBody().getJobOp().getJobId());
						jobDesc.setStatus(App.JobDesc.JobCode.JOBFAILED);
						jsb.addData(0, jobDesc.build());
						pb.setJobStatus(jsb.build());
						reply.setBody(pb.build());
						return reply.build();
					}

					// save the data in the leader node too
					System.out.println("Writing to file by " + conf.getNodeId());
					String course = request.getBody().getJobOp().getData().getOptions().getNode(0).getValue();
					String email = request.getBody().getJobOp().getData().getOptions().getNode(1).getValue();
					Server.updateUserToCoursesMap(Server.getCommitId() + 1, email, Integer.parseInt(course));

					App.JobDesc.Builder jobDesc = App.JobDesc.newBuilder();
					jobDesc.setNameSpace("User " + email + " registered for " + course);
					jobDesc.setOwnerId(1111);
					jobDesc.setJobId(request.getBody().getJobOp().getJobId());
					jobDesc.setStatus(App.JobDesc.JobCode.JOBRECEIVED);

					jsb.addData(0, jobDesc.build());
				}
			}
		}

		// Signin to the system ; Allowed only if the user is present in the predefined list os users ;
		// Users file is present in runtime/ring/users
		if (operation.equals("sign_in")) {
			String email = request.getBody().getJobOp().getData().getOptions().getNode(0).getValue();
			System.out.println(email);
			String password = request.getBody().getJobOp().getData().getOptions().getNode(1).getValue();
			System.out.println(password);

			//Authenticate the user
			if(Server.usersMap.containsKey(email)){
				if(Server.usersMap.get(email).pwd.equals(password)){
					//Authenticate the user
					Server.email_session = email;
					App.JobDesc.Builder jobDesc = App.JobDesc.newBuilder();
					jobDesc.setNameSpace("Signin successful for " + email);
					jobDesc.setOwnerId(1234);
					jobDesc.setJobId("100");
					jobDesc.setStatus(App.JobDesc.JobCode.JOBRECEIVED);
					jsb.addData(0, jobDesc.build());
				}
			}
			else{
				Server.email_session = email;
				App.JobDesc.Builder jobDesc = App.JobDesc.newBuilder();
				jobDesc.setNameSpace("Signin not successful for " + email);
				jobDesc.setOwnerId(1234);
				jobDesc.setJobId("101");
				jobDesc.setStatus(App.JobDesc.JobCode.JOBFAILED);

				jsb.addData(0, jobDesc.build());
				System.out.println("setting to job failed status"+ App.JobDesc.JobCode.JOBFAILED);
			}

		}

		pb.setJobStatus(jsb.build());
		reply.setBody(pb.build());
		return reply.build();
	}

	//build the request to request for intercluster operation
	private Request buildInterclusterRequest()
	{
		Request.Builder forward = Request.newBuilder();

		App.Header.Builder header = App.Header.newBuilder();
		header.setOriginator(conf.getNodeId());
		header.setRoutingId(App.Header.Routing.JOBS);
		forward.setHeader(header.build());

		App.Payload.Builder forwardpb = App.Payload.newBuilder();

		App.JobStatus.Builder forwardjsb = App.JobStatus.newBuilder();
		forwardjsb.setJobId("5555");
		forwardjsb.setJobState(App.JobDesc.JobCode.JOBQUEUED);
		forwardjsb.setStatus(App.PokeStatus.SUCCESS);

		App.JobOperation.Builder forwardJobOp = App.JobOperation.newBuilder();
		forwardJobOp.setJobId("5555");
		forwardJobOp.setAction(App.JobOperation.JobAction.LISTJOBS);

		App.JobDesc.Builder forwardData = App.JobDesc.newBuilder();
		forwardData.setNameSpace("getInterclusterData");
		forwardData.setOwnerId(5555);
		forwardData.setJobId("5555");
		forwardData.setStatus(App.JobDesc.JobCode.JOBQUEUED);
		forwardJobOp.setData(forwardData.build());

		forwardpb.setJobOp(forwardJobOp.build());
		forwardpb.setJobStatus(forwardjsb.build());
		forward.setBody(forwardpb.build());
		return forward.build();
	}

	// Build the request to broadcast to the other servers
	private Request buildForwardRequest(Request request, int messagenumber)
	{
		Request.Builder forward = Request.newBuilder();
		forward.setHeader(ResourceUtil.buildHeaderFrom(request.getHeader(), App.PokeStatus.SUCCESS, null));

		App.Payload.Builder forwardpb = App.Payload.newBuilder();

		App.JobStatus.Builder forwardjsb = App.JobStatus.newBuilder();
		forwardjsb.setJobId(request.getBody().getJobStatus().getJobId());
		forwardjsb.setJobState(request.getBody().getJobStatus().getJobState());
		forwardjsb.setStatus(App.PokeStatus.SUCCESS);

		App.JobOperation.Builder forwardJobOp = App.JobOperation.newBuilder();
		forwardJobOp.setJobId(request.getBody().getJobOp().getJobId());
		forwardJobOp.setAction(request.getBody().getJobOp().getAction());

		App.JobDesc.Builder forwardData = App.JobDesc.newBuilder();
		forwardData.setNameSpace(request.getBody().getJobOp().getData().getNameSpace());
		forwardData.setOwnerId(1111);
		forwardData.setJobId(request.getBody().getJobOp().getData().getJobId());
		forwardData.setStatus(request.getBody().getJobOp().getData().getStatus());

		forwardjsb.addData(0, forwardData.build());

		App.NameValueSet.Builder nvc = App.NameValueSet.newBuilder();

		App.NameValueSet.Builder forwardOptionData1 = App.NameValueSet.newBuilder();
		nvc.setNodeType(request.getBody().getJobOp().getData().getOptions().getNodeType());

		forwardOptionData1.setNodeType(request.getBody().getJobOp().getData().getOptions().getNode(0).getNodeType());
		forwardOptionData1.setName(request.getBody().getJobOp().getData().getOptions().getNode(0).getName());
		forwardOptionData1.setValue(request.getBody().getJobOp().getData().getOptions().getNode(0).getValue());
		nvc.addNode(0,forwardOptionData1.build());

		App.NameValueSet.Builder email = App.NameValueSet.newBuilder();
		//adding email to send accross
		email.setNodeType(request.getBody().getJobOp().getData().getOptions().getNode(1).getNodeType());
		email.setName(request.getBody().getJobOp().getData().getOptions().getNode(1).getName());
		email.setValue(request.getBody().getJobOp().getData().getOptions().getNode(1).getValue());
		nvc.addNode(1,email.build());


		App.NameValueSet.Builder messageNum = App.NameValueSet.newBuilder();
		//adding email to send accross
		messageNum.setNodeType(request.getBody().getJobOp().getData().getOptions().getNode(1).getNodeType());
		messageNum.setName("Message Number");
		messageNum.setValue(String.valueOf(messagenumber));
		nvc.addNode(2,messageNum.build());

		forwardData.setOptions(nvc);
		forwardJobOp.setData(forwardData.build());

		forwardpb.setJobOp(forwardJobOp.build());
		forwardpb.setJobStatus(forwardjsb.build());
		forward.setBody(forwardpb.build());

		return forward.build();
	}
}
