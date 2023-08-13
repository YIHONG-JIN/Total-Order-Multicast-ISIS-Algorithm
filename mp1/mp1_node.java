import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.FileHandler;
import java.util.logging.Logger;

import static java.lang.Thread.sleep;

public class mp1_node {
    static Logger logger;
    static ConcurrentHashMap<String, List<String>> targets;
    static String nodeId;
    private static int msgId;

    // the program will be started using the following command
    // java mp1_node <nodeId> <port> <configFile>
    // where <nodeId> is the id of the node, <port> is the port number it will listen on, and <configFile> is the name of the configuration file
    public static void main(String[] args) throws IOException, InterruptedException {
        msgId = 0;
        nodeId = args[0];
        initLogger(nodeId);
        int port = Integer.parseInt(args[1]);
        String configFile = args[2];

        // read the config file and get the list of all nodes and store it in a collection
        // the config file is a text file with the following format
        // <nodeId> <ip> <port>
        // The first argument is an identifier
        // that is unique for each node. The second argument is the port number it listens on. The third
        // argument is a configuration file – the first line of the configuration file is the number of other
        // nodes in the system that it must connect to, and each subsequent line contains the identifier,
        // hostname, and the port number of these nodes.
        File fileHandler = new File(configFile);
        Scanner scanner = new Scanner(fileHandler);
        targets = new ConcurrentHashMap<>();

        int count = scanner.nextInt();
        for (int i = 0; i < count; i++) {
            String id = scanner.next();
            String ip = scanner.next();
            int portNumber = scanner.nextInt();
            List<String> target = new ArrayList<>();
            target.add(id);
            target.add(ip);
            target.add(String.valueOf(portNumber));
            targets.put(id, target);
        }
        // close the scanner
        scanner.close();

        // add the current node to the list of targets
        List<String> currentNode = new ArrayList<>();
        currentNode.add(nodeId);
        currentNode.add(InetAddress.getLocalHost().getHostAddress());
        currentNode.add(String.valueOf(port));
        targets.put(nodeId, currentNode);


        // create a thread to listen to the port
        Thread t = new ServerThread(nodeId, port, configFile, logger);
        t.start();

        sleep(1000);

        Scanner input = new Scanner(System.in);
        while (true) {
            try {
                if (input.hasNextLine()) {
                    multicast(input.nextLine());
                }
            } catch (Exception e) {
                e.printStackTrace();
                break;
            }
        }
    }

    private static void initLogger(String nodeId) throws IOException {
        logger = Logger.getLogger("MyLog");
        FileHandler fh;
        fh = new FileHandler(nodeId + "log.txt");
        logger.addHandler(fh);
        // format the log file
        fh.setFormatter(new java.util.logging.SimpleFormatter());
        logger.setUseParentHandlers(false);
    }

    private static void multicast(String transaction) {
        // send the transaction to all the nodes in the system
        for (List<String> target : targets.values()) {
            String targetId = target.get(0);
            String targetIp = target.get(1);
            int targetPort = Integer.parseInt(target.get(2));
            long sendTime = System.currentTimeMillis() % 400000;
            Message msg;
            msg = new Message(transaction, nodeId, targetId, msgId, false, false, "", 0, sendTime, 0, 0, 0);
            unicast(msg, targetIp, targetPort);
        }
        msgId += 1;
    }

    private static void unicast(Message msg, String ip, int port) {
        // send the transaction to the node specified by the socket
        try {
            Socket socket = new Socket(ip, port);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.writeUTF(msg.toString());
            out.flush();
            out.close();
            socket.close();
        } catch (IOException e) {
            System.out.println("Connection failed: " + msg.receiverNodeId);
            // remove the node from the list of targets
            for (List<String> target : targets.values()) {
                if (target.get(1).equals(ip) && target.get(2).equals(String.valueOf(port))) {
                    targets.remove(target.get(0));
                    break;
                }
            }

            multicastFailure(msg.receiverNodeId);
        }
    }

    private static void multicastFailure(String failedNodeId) {
        // send a string "This node is down" + nodeId to all the nodes in the system
        for (List<String> target : targets.values()) {
            String targetId = target.get(0);
            String targetIp = target.get(1);
            int targetPort = Integer.parseInt(target.get(2));
            try {
                String failureMsg = "This node is down: " + failedNodeId;
                Socket failureSocket = new Socket(targetIp, targetPort);
                DataOutputStream failureOut = new DataOutputStream(failureSocket.getOutputStream());
                failureOut.writeUTF(failureMsg);
                failureOut.flush();
                failureOut.close();
                failureSocket.close();
            } catch (IOException e) {
                logger.info("Connection failed: " + targetId);
                // remove the node from the list of targets
                for (List<String> targetToRemove : targets.values()) {
                    if (target.get(1).equals(targetToRemove.get(1)) && target.get(2).equals(targetToRemove.get(2))) {
                        targets.remove(targetToRemove.get(0));
                        break;
                    }
                }
                multicastFailure(targetId);
            }
        }
    }

}



