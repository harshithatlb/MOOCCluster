package poke.resources;

import poke.Communication.CommunicationConnection;
import poke.comm.App;
import poke.server.Server;
import poke.server.conf.ServerConf;
import poke.server.managers.ElectionManager;
import poke.server.resources.ResourceFactory;

import java.util.ArrayList;
import java.util.Map;

/**
 * Created by varuna on 12/5/15.
 */
public class LeaderData {

   ServerConf conf  ;
   Integer leaderID ;

    public LeaderData()
    {
        conf = ResourceFactory.cfg;
        leaderID = ElectionManager.getInstance().whoIsTheLeader();
    }

    public void getData()
    {
        App.Request latest = buildLatestRequest();
        System.out.println("Job resource , the leader is : " + leaderID);
        int port = conf.getAdjacent().getNode(leaderID).getPort();
        String host = conf.getAdjacent().getNode(leaderID).getHost();
        CommunicationConnection connection = new CommunicationConnection(host, port);
        App.Request response = connection.send(latest);

        if(response!=null) {
            System.out.println("latest Response: " + response);
            // commit it to the files
            String commitID, value, name, course;
            String[] nameCourse;

            if(response.getBody().getJobStatus().getData(0).getOptions().getName().equals("hasData"))
            {
                System.out.println(response.getBody().getJobStatus().getData(0).getOptions().getNodeCount());
                int count = response.getBody().getJobStatus().getData(0).getOptions().getNodeCount();

                for (int k = 0; k < count; k++) {
                    commitID = response.getBody().getJobStatus().getData(0).getOptions().getNode(k).getName();
                    value = response.getBody().getJobStatus().getData(0).getOptions().getNode(k).getValue();
                    nameCourse = value.split(":");
                    name = nameCourse[0];
                    course = nameCourse[1];
                    Server.updateUserToCoursesMap(Integer.parseInt(commitID), name, Integer.parseInt(course));
                    System.out.println("got response from the leader about the latest files");
                }
            }

        }
    }

    // Build the request to request the latest commits from the leader
    private App.Request buildLatestRequest()
    {
        //request.getBody().getJobOp().getData().getNameSpace();
        App.Request.Builder forward = App.Request.newBuilder();

        App.Header.Builder header = App.Header.newBuilder();
        header.setOriginator(conf.getNodeId());
        header.setRoutingId(App.Header.Routing.JOBS);
        header.setToNode(1);
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
        forwardData.setNameSpace("latest");
        forwardData.setOwnerId(5555);
        forwardData.setJobId("5555");
        forwardData.setStatus(App.JobDesc.JobCode.JOBQUEUED);

        //forwardjsb.addData(0, forwardData.build());
        //forwardJobOp.setData(forwardData.build());

        App.NameValueSet.Builder nvc = App.NameValueSet.newBuilder();
        App.NameValueSet.Builder forwardOptionData1 = App.NameValueSet.newBuilder();
        nvc.setNodeType(App.NameValueSet.NodeType.VALUE);

        forwardOptionData1.setNodeType(App.NameValueSet.NodeType.VALUE);
        forwardOptionData1.setName("latestCommitNumber");
        forwardOptionData1.setValue(String.valueOf(Server.getCommitId()));
        nvc.addNode(0,forwardOptionData1.build());

        forwardData.setOptions(nvc);
        forwardJobOp.setData(forwardData.build());

        forwardpb.setJobOp(forwardJobOp.build());
        forwardpb.setJobStatus(forwardjsb.build());
        forward.setBody(forwardpb.build());
        return forward.build();
    }
}
