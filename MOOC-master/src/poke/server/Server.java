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
package poke.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import poke.resources.User;
import poke.server.conf.JsonUtil;
import poke.server.conf.NodeDesc;
import poke.server.conf.ServerConf;
import poke.server.management.ManagementInitializer;
import poke.server.management.ManagementQueue;
import poke.server.managers.*;
import poke.server.resources.ResourceFactory;

/**
 * Note high surges of messages can close down the channel if the handler cannot
 * process the messages fast enough. This design supports message surges that
 * exceed the processing capacity of the server through a second thread pool
 * (per connection or per server) that performs the work. Netty's boss and
 * worker threads only processes new connections and forwarding requests.
 * <p>
 * Reference Proactor pattern for additional information.
 * 
 * @author gash
 * 
 */
public class Server {
	protected static Logger logger = LoggerFactory.getLogger("server");

	protected static ChannelGroup allChannels;
	protected static HashMap<Integer, ServerBootstrap> bootstrap = new HashMap<Integer, ServerBootstrap>();
	protected ServerConf conf;

	protected JobManager jobMgr;
	protected NetworkManager networkMgr;
	protected HeartbeatManager heartbeatMgr;
	protected ElectionManager electionMgr;
	private static String courses;
	private static String users;
	private static String usersToCourses;
   public static String email_session="";

	/**
	 * static because we need to get a handle to the factory from the shutdown
	 * resource
	 */
	public static void shutdown() {
		try {
			if (allChannels != null) {
				ChannelGroupFuture grp = allChannels.close();
				grp.awaitUninterruptibly(5, TimeUnit.SECONDS);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		logger.info("Server shutdown");
		System.exit(0);
	}

	/**
	 * initialize the server with a configuration of it's resources
	 * 
	 * @param cfg
	 */
	public Server(File cfg, String users,  String courses, String userToCourses) {
		init(cfg);
		
		this.users = users;
		this.usersToCourses = userToCourses;
		this.courses = courses;
		System.out.println("In Server: going to read from file");
		readUsersFile(users);
		readCoursesFile(courses);
		readUserToCoursesFile(userToCourses);
	}
	
	public Server(File cfg) {
		init(cfg);
		System.out.println("Server is getting init");

	}

	public static Map<String,User> usersMap = new HashMap<String,User>();
	public static Map<String,String> coursesMap = new HashMap<String,String>();
	public static Map<Integer, ArrayList<String>> userToCoursesMap = new TreeMap<Integer, ArrayList<String>>(Collections.reverseOrder());

	public static void updateCoursesMap(String courseId, String courseName) {
		coursesMap.put(courseId,courseName);
		writeToCourseFile();
	}

	private static void writeToCourseFile() {
		PrintWriter writer = null;

		try {
			writer = new PrintWriter(courses, "UTF-8");
		} catch (Exception e) {
			e.printStackTrace();
		}
		if(!coursesMap.isEmpty()) {
			for (Map.Entry<String, String> entry : coursesMap.entrySet()) {
				System.out.println(entry.getKey()+""+entry.getValue());
				writer.append(entry.getKey() + ":" + entry.getValue());
			}
		}
		writer.close();
	}

	public static int  getCommitId(){
		if(!userToCoursesMap.isEmpty()){
		Iterator list_=  userToCoursesMap.keySet().iterator();
		return (Integer) list_.next();
		}
		return 0;
	}


	public static void updateUsersMap(User user){
	 for (Map.Entry<String, User> entry : usersMap.entrySet()) {
		 if(!entry.getKey().equals(user.getFname()))
				 usersMap.put(user.getFname(), user);
	 }
	 System.out.println("nsize of users map"+usersMap.size());
	 writeToUsersFile();
	}

	private static void writeToUsersFile() {
		System.out.println("************************In write to USers file");
		Writer writer = null;


		try {
			writer = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(users), "utf-8"));
			if(!usersMap.isEmpty()) {
				for (Map.Entry<String, User> entry : usersMap.entrySet()) {
					System.out.println(entry.getKey()+""+entry.getValue());
					User newUser = entry.getValue();
					writer.write(newUser.getFname() + ":" + newUser.getLname() + ":" + newUser.getEmail() + ":" + newUser.getPwd());
                     writer.write("\n");
				}
			}

		} catch (IOException ex) {
			// report
			ex.printStackTrace();
		} finally {
			try {writer.close();} catch (Exception ex) {/*ignore*/}
		}
	}


	public static void updateUserToCoursesMap(Integer commitId,String userName, Integer courseId) {
			ArrayList<String> course_name_list = new ArrayList<String>();
			course_name_list.add(userName);
			course_name_list.add(courseId.toString());
			userToCoursesMap.put(commitId, course_name_list);
			System.out.println("\n***************calling write on file with latest commit id:" +getCommitId()+"and course as"+course_name_list.toString());
			writeToUsersToCourses();

	}

	private static void writeToUsersToCourses() {

		Writer writer = null;

		try {
			writer =new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(usersToCourses), "utf-8"));
			if(!userToCoursesMap.isEmpty()) {
				for (Map.Entry<Integer, ArrayList<String>> entry : userToCoursesMap.entrySet()) {
					ArrayList<String> courses_= entry.getValue();
					System.out.println("********************in write to users");
				//	System.out.println(entry.getKey()+":"+courses_.get(0)+":"+courses_.get(1));
					writer.write(entry.getKey() + ":" + courses_.get(0) + ":" + courses_.get(1));
					writer.write("\n");
				}
				writer.close();
			}
		}
		 catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	}


	private void readUserToCoursesFile(String userToCourses) {
			System.out.println("Reading user to courser file");
			String line = null;
			try {
				FileReader fileReader =
						new FileReader(userToCourses);

				BufferedReader bufferedReader =
						new BufferedReader(fileReader);

				while((line = bufferedReader.readLine()) != null) {
					String[] input = line.split(":");
					ArrayList<String> courses = new ArrayList<String>();
						courses.add(input[1]);
						courses.add(input[2]);
					userToCoursesMap.put(Integer.parseInt(input[0]), courses);
				}

				// Always close files.
				bufferedReader.close();
			}
			catch(FileNotFoundException ex) {
				System.out.println(
						"Unable to open file '" +
								userToCourses + "'");
			}
			catch(IOException ex) {
				System.out.println(
						"Error reading file '"
								+ userToCourses + "'");
				// Or we could just do this:
				// ex.printStackTrace();
			}
			if(userToCoursesMap!=null){
				for (Map.Entry<Integer,ArrayList<String>> entry : userToCoursesMap.entrySet()) 
					System.out.println(entry.getKey()+entry.getValue().toString());
					System.out.println("Latest commit id"+getCommitId());
			}
		}

	private void readUsersFile(String users) {
		System.out.println("Reading users file");
         String line = null;
		try {
			FileReader fileReader =
					new FileReader(users);

			BufferedReader bufferedReader =
					new BufferedReader(fileReader);
			System.out.println("Reading from users file");
			while((line = bufferedReader.readLine() )!=null) {
				System.out.println("***reading"+line);
				if(!line.startsWith(" ")) {
					String[] input = line.split(":");
					System.out.println("__________________________________");
					System.out.println("**********" + input.length + " " + input[0] + " " + input[1] + "  " + input[2] + " " + input[3]);
					User newUser = new User(input[0], input[1], input[2], input[3]);
					usersMap.put(input[0], newUser);
				}
			}
			if(!usersMap.isEmpty()) {
				for (Map.Entry<String, User> entry : usersMap.entrySet()) {
					User newUser = entry.getValue();
					System.out.println(newUser.getFname() + ":" + newUser.getLname() + ":" + newUser.getEmail() + ":" + newUser.getPwd());
				}
			}

			// Always close files.
			bufferedReader.close();
		}
		catch(FileNotFoundException ex) {
			System.out.println(
					"Unable to open file '" +
							users + "'");
		}
		catch(IOException ex) {
			System.out.println(
					"Error reading file '"
							+ users + "'");
			// Or we could just do this:
			// ex.printStackTrace();
		}
	}

	private void readCoursesFile(String courses) {
		System.out.println("Read courses file");
		String line = null;
		try {
			FileReader fileReader =
					new FileReader(courses);

			BufferedReader bufferedReader =
					new BufferedReader(fileReader);

			while((line = bufferedReader.readLine()) != null) {
				String[] input = line.split(":");
				coursesMap.put(input[0], input[1]);
			}

			// Always close files.
			bufferedReader.close();
		}
		catch(FileNotFoundException ex) {
			System.out.println(
					"Unable to open file '" +
							courses + "'");
		}
		catch(IOException ex) {
			System.out.println(
					"Error reading file '"
							+ courses + "'");
			// Or we could just do this:
			// ex.printStackTrace();
		}
		if(coursesMap!=null){
			for (Map.Entry<String, String> entry : coursesMap.entrySet()) 
					System.out.println(entry.getKey() + "  " + entry.getValue());
		}
	}


	private void init(File cfg) {
		if (!cfg.exists())
			throw new RuntimeException(cfg.getAbsolutePath() + " not found");
		// resource initialization - how message are processed
		BufferedInputStream br = null;
		try {
			byte[] raw = new byte[(int) cfg.length()];
			br = new BufferedInputStream(new FileInputStream(cfg));
			br.read(raw);
			//System.out.println("new String(raw) "+new String(raw));
			conf = JsonUtil.decode(new String(raw), ServerConf.class);
			//System.out.println("conf "+conf);
			if (!verifyConf(conf))
				throw new RuntimeException("verification of configuration failed");
			ResourceFactory.initialize(conf);
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private boolean verifyConf(ServerConf conf) {
		boolean rtn = true;
		if (conf == null) {
			logger.error("Null configuration");
			return false;
		} else if (conf.getNodeId() < 0) {
			logger.error("Bad node ID, negative values not allowed.");
			rtn = false;
		} else if (conf.getPort() < 1024 || conf.getMgmtPort() < 1024) {
			logger.error("Invalid port number");
			rtn = false;
		}

		return rtn;
	}

	public void release() {
		if (HeartbeatManager.getInstance() != null)
			HeartbeatManager.getInstance().release();
	}

	/**
	 * initialize the outward facing (public) interface
	 * 
	 *
	 *            The port to listen to
	 */
	private static class StartCommunication implements Runnable {
		ServerConf conf;

		public StartCommunication(ServerConf conf) {
			this.conf = conf;
		}

		public void run() {
			// construct boss and worker threads (num threads = number of cores)

			EventLoopGroup bossGroup = new NioEventLoopGroup();
			EventLoopGroup workerGroup = new NioEventLoopGroup();

			try {
				ServerBootstrap b = new ServerBootstrap();
				bootstrap.put(conf.getPort(), b);

				b.group(bossGroup, workerGroup);
				b.channel(NioServerSocketChannel.class);
				b.option(ChannelOption.SO_BACKLOG, 100);
				b.option(ChannelOption.TCP_NODELAY, true);
				b.option(ChannelOption.SO_KEEPALIVE, true);
				// b.option(ChannelOption.MESSAGE_SIZE_ESTIMATOR);

				boolean compressComm = false;
				b.childHandler(new ServerInitializer(compressComm));

				// Start the server.
				logger.info("Starting server " + conf.getNodeId() + ", listening on port = " + conf.getPort());
				ChannelFuture f = b.bind(conf.getPort()).syncUninterruptibly();

				// should use a future channel listener to do this step
				// allChannels.add(f.channel());

				// block until the server socket is closed.
				f.channel().closeFuture().sync();
			} catch (Exception ex) {
				// on bind().sync()
				logger.error("Failed to setup public handler.", ex);
			} finally {
				// Shut down all event loops to terminate all threads.
				bossGroup.shutdownGracefully();
				workerGroup.shutdownGracefully();
			}

			// We can also accept connections from a other ports (e.g., isolate
			// read
			// and writes)
		}
	}

	/**
	 * initialize the private network/interface
	 * 
	 * @param port
	 *            The port to listen to
	 */
	private static class StartManagement implements Runnable {
		private ServerConf conf;

		public StartManagement(ServerConf conf) {
			this.conf = conf;
		}

		public void run() {
			// construct boss and worker threads (num threads = number of cores)

			// UDP: not a good option as the message will be dropped

			EventLoopGroup bossGroup = new NioEventLoopGroup();
			EventLoopGroup workerGroup = new NioEventLoopGroup();

			try {
				ServerBootstrap b = new ServerBootstrap();
				bootstrap.put(conf.getMgmtPort(), b);

				b.group(bossGroup, workerGroup);
				b.channel(NioServerSocketChannel.class);
				b.option(ChannelOption.SO_BACKLOG, 100);
				b.option(ChannelOption.TCP_NODELAY, true);
				b.option(ChannelOption.SO_KEEPALIVE, true);
				// b.option(ChannelOption.MESSAGE_SIZE_ESTIMATOR);

				boolean compressComm = false;
				b.childHandler(new ManagementInitializer(compressComm));

				// Start the server.

				logger.info("Starting mgmt " + conf.getNodeId() + ", listening on port = " + conf.getMgmtPort());
				ChannelFuture f = b.bind(conf.getMgmtPort()).syncUninterruptibly();

				// block until the server socket is closed.
				f.channel().closeFuture().sync();
			} catch (Exception ex) {
				// on bind().sync()
				logger.error("Failed to setup public handler.", ex);
			} finally {
				// Shut down all event loops to terminate all threads.
				bossGroup.shutdownGracefully();
				workerGroup.shutdownGracefully();
			}
		}
	}

	/**
	 * this initializes the managers that support the internal communication
	 * network.
	 * 
	 * TODO this should be refactored to use the conf file
	 */
	private void startManagers() {
		if (conf == null)
			return;
		System.out.println("Caling start manager");
		electionMgr = ElectionManager.initManager(conf);
		// start the inbound and outbound manager worker threads
		ManagementQueue.startup(conf);

		// create manager for network changes
		networkMgr = NetworkManager.initManager(conf);

		// create manager for leader election. The number of votes (default 1)
		// is used to break ties where there are an even number of nodes.
		//electionMgr = ElectionManager.initManager(conf);

		// create manager for accepting jobs
		jobMgr = JobManager.initManager(conf);

		System.out.println("---> Server.startManagers() expecting " + conf.getAdjacent().getAdjacentNodes().size()
			+ " connections");
		// establish nearest nodes and start sending heartbeats
		heartbeatMgr = HeartbeatManager.initManager(conf);
		for (NodeDesc nn : conf.getAdjacent().getAdjacentNodes().values()) {
			HeartbeatData node = new HeartbeatData(nn.getNodeId(), nn.getHost(), nn.getPort(), nn.getMgmtPort());

			// fn(from, to)
			HeartbeatPusher.getInstance().connectToThisNode(conf.getNodeId(), node);
		}
		heartbeatMgr.start();

		// manage heartbeatMgr connections
		HeartbeatPusher conn = HeartbeatPusher.getInstance();
		conn.start();

		logger.info("Server " + conf.getNodeId() + ", managers initialized");
	}

	/**
	 * Start the communication for both external (public) and internal
	 * (management)
	 */
	public void run() {
		if (conf == null) {
			logger.error("Missing configuration file");
			return;
		}

		logger.info("Initializing server " + conf.getNodeId());

		// storage initialization
		// TODO storage setup (e.g., connection to a database)

		startManagers();

		StartManagement mgt = new StartManagement(conf);
		Thread mthread = new Thread(mgt);
		mthread.start();

		StartCommunication comm = new StartCommunication(conf);
		logger.info("Server " + conf.getNodeId() + " ready");

		Thread cthread = new Thread(comm);
		cthread.start();
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		if (args.length != 4) {
			//System.err.println("Usage: java " + Server.class.getClass().getName() + " conf-file");
			//System.exit(1);
		}
		System.out.println("**************length of args " + args.length +args[0]);
		File cfg = new File(args[0]);
		System.out.println("**********" + args[1]);
		String users = args[1];
		System.out.println("main Started");
		String courses = args[2];
		String usersToCourses = args[3];

		if (!cfg.exists()) {
			Server.logger.error("configuration file does not exist: " + cfg);
			System.exit(2);
		}

		Server svr = new Server(cfg, users,courses, usersToCourses);
		svr.run();
	}
}
