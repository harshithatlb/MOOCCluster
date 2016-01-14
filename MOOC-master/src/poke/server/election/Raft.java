package poke.server.election;

import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import poke.core.Mgmt.LeaderElection;
import poke.core.Mgmt.Management;
import poke.core.Mgmt.MgmtHeader;
import poke.core.Mgmt.VectorClock;
import poke.core.Mgmt.LeaderElection.ElectAction;
import poke.resources.LeaderData;
import poke.server.Server;
import poke.server.conf.ServerConf;

import poke.server.managers.*;

public class Raft implements Election {

	protected static Logger logger = LoggerFactory.getLogger("Raft");

	private Integer nodeId;
	private ElectionState current;
	private int maxHops = -1; // unlimited
	private ElectionListener listener;
	static AtomicInteger msgID = new AtomicInteger(0);
	private static int votesReceived = 1;
	//private boolean voteCasted = false;
	
	
	public Raft() {
		
	}
	
	public Raft(Integer nodeId) {
		this.nodeId = nodeId;
	}
	
	@Override
	public void setListener(ElectionListener listener) {
		this.listener = listener;
		
	}

	@Override
	public void clear() {
		// TODO Auto-generated method stub
		current = null;
		 resetRaftConfig();

	}
	
	private ServerConf getConf(){
		return ((ElectionManager)listener).getConf();
	}

	private boolean updateCurrent(LeaderElection req) {
		boolean isNew = false;

		if (current == null) {
			current = new ElectionState();
			isNew = true;
		}

		current.electionID = req.getElectId();
		current.candidate = req.getCandidateId();
		current.desc = req.getDesc();
		current.maxDuration = req.getExpires();
		current.startedOn = System.currentTimeMillis();
		current.state = req.getAction();
		current.id = -1; // TODO me or sender?
		current.active = true;

		return isNew;
	}
	@Override
	public boolean isElectionInprogress() {
		// TODO Auto-generated method stub
		boolean election = false;
		if (current!= null){
			election=current.active;
		}
		return election;
	}

	@Override
	public Integer getElectionId() {
		// TODO Auto-generated method stub
		return ElectionIDGenerator.getMasterID();
	}

	@Override
	public Integer createElectionID() {
		return ElectionIDGenerator.nextID();
	}
	
	private void setElectionId(int id) {
		if (current != null) {
			ElectionIDGenerator.setMasterID(id);
		}
		
	}

	@Override
	public Integer getWinner() {
		// TODO Auto-generated method stub
		return null;
	}
	
	public boolean checkVoteCastedForElection(LeaderElection req){
		
		
		boolean voteCasted = false;
		if (current != null) {
		if (req.getElectId()==current.electionID)
			voteCasted = true;
		}
		
		return voteCasted;
	}

	@Override
	public Management process(Management mgmt) {
		if (!mgmt.hasElection())
			return null;

		LeaderElection req = mgmt.getElection();
		if (req.getExpires() <= System.currentTimeMillis()) {
			// election has expired without a conclusion?
		}

		Management rtn = null;

		if (req.getAction().getNumber() == ElectAction.DECLAREELECTION_VALUE) {
			// an election is declared!

			// required to eliminate duplicate messages - on a declaration,
			// should not happen if the network does not have cycles
			List<VectorClock> rtes = mgmt.getHeader().getPathList();

			System.out.println("------------mgmt header is" + mgmt.getHeader());
			System.out.println("------------size of vector clock" + rtes.size());
//			for (VectorClock rp : rtes) {
//				if (rp.getNodeId() == this.nodeId) {
//					// message has already been sent to me, don't use and
//					// forward
//					return null;
//				}
//			}

			// I got here because the election is unknown to me

			// this 'if debug is on' should cover the below dozen or so
			// println()s. It is here to help with seeing when an election
			// occurs.
			if (logger.isDebugEnabled()) {
			}
			/*
			if (voteCasted)
			{
				System.out.println("\n\n*********************************************************");
				System.out.println("   Nomination Got:    Node " + req.getCandidateId());
				System.out.println("   Votes already casted" );
				return null;
			}*/
			
			if ((getCommitId() > req.getCommitId())||(this.getElectionId()>req.getElectId()) ){
				System.out.println("\n\n*********************************************************");
				System.out.println("   Nomination Got:    Node " + req.getCandidateId());
				System.out.println("************ Request electionId " + req.getElectId() + " node electionid " + this.getElectionId());
				System.out.println("************ Request commitId   " + req.getCommitId() + " node commitId " + getCommitId());
				System.out.println("************* Vote Casting Condition Not Met ********");
				System.out.println("\n\n*********************************************************");
				return null;
			}
			
			if (checkVoteCastedForElection(req)) {
				System.out.println("\n\n*********************************************************");
				System.out.println("   Nomination Got:    Node " + req.getCandidateId());
				System.out.println("   Votes already casted in ElectionId "+req.getElectId() );
				System.out.println("\n\n*********************************************************");
				return null;
			}
			
			updateCurrent(req);

			System.out.println("\n\n*********************************************************");
			System.out.println(" RAFT ELECTION: Election declared");
			System.out.println("   Election ID:  " + req.getElectId());
			System.out.println("   Rcv from:     Node " + mgmt.getHeader().getOriginator());
			System.out.println("   Expires:      " + new Date(req.getExpires()));
			System.out.println("   Nominates:    Node " + req.getCandidateId());
			System.out.println("   Desc:         " + req.getDesc());
			System.out.print("   Routing tbl:  [");
			for (VectorClock rp : rtes)
				System.out.print("Node " + rp.getNodeId() + " (" + rp.getVersion() + "," + rp.getTime() + "), ");
			System.out.println("]");
			System.out.println("*********************************************************\n\n");

			// sync master IDs to current election
			//ElectionIDGenerator.setMasterID(req.getElectId());

			/**
			 * a new election can be declared over an existing election.
			 * 
			 * TODO need to have an monotonically increasing ID that we can test
			 */
			
			rtn = castVote(mgmt, true);
		/*	if (rtn != null) {
				updateCurrent(req);
			}*/
			

		} else if (req.getAction().getNumber() == ElectAction.DECLAREVOID_VALUE) {
			// no one was elected, I am dropping into standby mode
			logger.info("TODO: no one was elected, I am dropping into standby mode");
			this.clear();
			notify(false, null);
		} else if (req.getAction().getNumber() == ElectAction.DECLAREWINNER_VALUE) {
			// some node declared itself the leader
			System.out.println("\n\n*********************************************************");
			System.out.println(" RAFT ELECTION: Leader declared");
			System.out.println("   Election ID:  " + req.getElectId());
			System.out.println("   Leader:    Node " + req.getCandidateId());
			System.out.println("*********************************************************\n\n");
			logger.info("Election " + req.getElectId() + ": Node " + req.getCandidateId() + " is declared the leader");
			updateCurrent(mgmt.getElection());
			current.active = false; // it's over
			setElectionId(req.getElectId());
			notify(true, req.getCandidateId());
			resetRaftConfig();

			//Consistency
			if((this.nodeId!=null) &&  (this.nodeId != req.getCandidateId())) {
				LeaderData leaderData = new LeaderData();
				leaderData.getData();
			}
			
		} else if (req.getAction().getNumber() == ElectAction.ABSTAIN_VALUE) {
			// for some reason, a node declines to vote - therefore, do nothing
		} else if (req.getAction().getNumber() == ElectAction.NOMINATE_VALUE) {
			updateCurrent(mgmt.getElection());
			//rtn = castVote(mgmt, isNew);
			rtn = nominateAction(mgmt);
		} else {
			// this is me!
		}

		return rtn;
	}
	
	private void notify(boolean success, Integer leader) {
		if (listener != null)
			listener.concludeWith(success, leader);
	}
	
	private synchronized Management nominateAction(Management mgmt){
		System.out.println("Reached nomination action");
		if (!mgmt.hasElection()) {
			System.out.println("***********" + mgmt.hasElection() + "**************");

			return null;
		}

		if(current == null){
			return null;
		}
		if ( !current.isActive()) {
			System.out.println("***********" + "current " + current + current.isActive()    + "**************");
			return null;
		}
		
		LeaderElection req = mgmt.getElection();
		if (req.getExpires() <= System.currentTimeMillis()) {
			System.out.println("Node " + this.nodeId + " says election expired - not voting");
			return null;
		}

		System.out.println("casting vote in election " + req.getElectId());
		
		boolean allowCycles = true;

//		if (!allowCycles) {
//			List<VectorClock> rtes = mgmt.getHeader().getPathList();
//			for (VectorClock rp : rtes) {
//				if (rp.getNodeId() == this.nodeId) {
//					// logger.info("Node " + this.nodeId +
//					// " already in the routing path - not voting");
//					return null;
//				}
//			}
//		}

		System.out.print("******************** checked cycles");

		if (req.getCandidateId()!=this.nodeId){
			 return null;
		}
		System.out.println("****************** request candidate id  " + req.getCandidateId());
		System.out.println("***************** checking election " + "Req electid " + req.getElectId() + " electionId " + this.getElectionId());
		if (req.getElectId()!=this.getElectionId()){

			return null;
		}
		
		votesReceived++;
		System.out.println("****************** votes received " + votesReceived);
		
		if (votesReceived<getConf().getNumberOfElectionVotes()){
			System.out.println("****************** waiting for votes. Got these many votes: " + votesReceived);
			//System.out.println("******************votes received " + votesReceived);
			//System.out.println("**************** votes received " + getConf().getNumberOfElectionVotes());
			return null;
		}
		System.out.println("****************** votes received " + votesReceived + "  election declared");
		//if (votesReceived==3){
			LeaderElection.Builder elb = LeaderElection.newBuilder();
			MgmtHeader.Builder mhb = MgmtHeader.newBuilder();
			mhb.setTime(System.currentTimeMillis());
			mhb.setSecurityCode(-999); // TODO add security
			
			if (elb.getHops() == 0)
				mhb.clearPath();
			else
				mhb.addAllPath(mgmt.getHeader().getPathList());

			mhb.setOriginator(mgmt.getHeader().getOriginator());

			elb.setElectId(req.getElectId());
			elb.setAction(ElectAction.NOMINATE);
			elb.setDesc(req.getDesc());
			elb.setExpires(req.getExpires());
			elb.setCandidateId(req.getCandidateId());
			notify(true, this.nodeId);

			elb.setAction(ElectAction.DECLAREWINNER);
			elb.setHops(mgmt.getHeader().getPathCount());
			logger.info("Node " + this.nodeId + " is declaring itself the leader");
			
			VectorClock.Builder rpb = VectorClock.newBuilder();
			rpb.setNodeId(this.nodeId);
			rpb.setTime(System.currentTimeMillis());
			rpb.setVersion(req.getElectId());
			mhb.addPath(rpb);

			Management.Builder mb = Management.newBuilder();
			mb.setHeader(mhb.build());
			mb.setElection(elb.build());
			resetRaftConfig();
			
			System.out.println("\n\n*********************************************************");
			System.out.println(" RAFT ELECTION: Leader declared");
			System.out.println("   Election ID:  " + req.getElectId());
			System.out.println("   Leader:    Node " + req.getCandidateId());
			System.out.println("*********************************************************\n\n");
		
		//}
		return mb.build();
	}
	
	private int getCommitId(){
		return Server.getCommitId();
	}
	
	private void resetRaftConfig()
	{
		//voteCasted = false;
		votesReceived = 1;
		
	}

	
	private synchronized Management castVote(Management mgmt, boolean isNew) {
		if (!mgmt.hasElection())
			return null;

		if (current == null || !current.isActive()) {
			return null;
		}

		LeaderElection req = mgmt.getElection();
		if (req.getExpires() <= System.currentTimeMillis()) {
			logger.info("Node " + this.nodeId + " says election expired - not voting");
			return null;
		}

		logger.info("casting vote in election " + req.getElectId());

		// DANGER! If we return because this node ID is in the list, we have a
		// high chance an election will not converge as the maxHops determines
		// if the graph has been traversed!
		boolean allowCycles = true;

//		if (!allowCycles) {
//			List<VectorClock> rtes = mgmt.getHeader().getPathList();
//			for (VectorClock rp : rtes) {
//				if (rp.getNodeId() == this.nodeId) {
//					// logger.info("Node " + this.nodeId +
//					// " already in the routing path - not voting");
//					return null;
//				}
//			}
//		}

		// okay, the message is new (to me) so I want to determine if I should
		// nominate myself

		/*if ((getCommitId() > req.getCommitId())||(this.getElectionId()>req.getElectId()) ){
			System.out.println("************checking election ids " + "req elect id    " + req.getElectId() + "node electionid     " + this.getElectionId());
			System.out.println("************checking commitId ids " + "req commitId    " + req.getCommitId() + "node commitId     " + getCommitId());
			return null;
		}*/
		
		
		
		System.out.println("****Building leader election***********");
				
		LeaderElection.Builder elb = LeaderElection.newBuilder();
		MgmtHeader.Builder mhb = MgmtHeader.newBuilder();
		mhb.setTime(System.currentTimeMillis());
		mhb.setSecurityCode(-999); // TODO add security
	
		// reversing path. If I'm the farthest a message can travel, reverse the
		// sending
		if (elb.getHops() == 0)
			mhb.clearPath();
		else
			mhb.addAllPath(mgmt.getHeader().getPathList());

		mhb.setOriginator(mgmt.getHeader().getOriginator());

		elb.setElectId(req.getElectId());
		elb.setAction(ElectAction.NOMINATE);
		elb.setDesc(req.getDesc());
		elb.setExpires(req.getExpires());
		elb.setCandidateId(req.getCandidateId());

		
			if (req.getHops() == -1)
				elb.setHops(-1);
			else
				elb.setHops(req.getHops() - 1);

			if (elb.getHops() == 0) {
				// reverse travel of the message to ensure it gets back to
				// the originator
				elb.setHops(mgmt.getHeader().getPathCount());

				// no clear winner, send back the candidate with the highest
				// known ID. So, if a candidate sees itself, it will
				// declare itself to be the winner (see above).
			} else {
				// forwarding the message on so, keep the history where the
				// message has been
				mhb.addAllPath(mgmt.getHeader().getPathList());
			}
		

		// add myself (may allow duplicate entries, if cycling is allowed)
		VectorClock.Builder rpb = VectorClock.newBuilder();
		rpb.setNodeId(this.nodeId);
		rpb.setTime(System.currentTimeMillis());
		rpb.setVersion(req.getElectId());
		mhb.addPath(rpb);

		Management.Builder mb = Management.newBuilder();
		mb.setHeader(mhb.build());
		mb.setElection(elb.build());

		return mb.build();
	}

	@Override
	public void setNodeId(int nodeId) {
		this.nodeId = nodeId;
		
	}

}
