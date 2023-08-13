import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

class ServerThread extends Thread {
    List<List<String>> targets;
    private final ServerSocket serverSocket;
    private final Logger logger;
    private final String nodeId;
    private final int port;
    private final int msgId;
    private int priorityId;
    private final ConcurrentHashMap<String, Message> receivedMsgs;
    private final ConcurrentHashMap<Integer, List<Message>> returnedMsgs;
    private final TreeMap<String, Integer> accountMap;


    public ServerThread(String nodeId, int port, String configFile, Logger logger) throws IOException {
        this.msgId = 0;
        this.priorityId = 0;
        this.nodeId = nodeId;
        this.port = port;
        this.logger = logger;
        this.serverSocket = new ServerSocket(this.port, 0, InetAddress.getLocalHost());
        this.serverSocket.setSoTimeout(100000);
        this.receivedMsgs = new ConcurrentHashMap<>();
        this.returnedMsgs = new ConcurrentHashMap<>();


        File fileHandler = new File(configFile);
        Scanner scanner = new Scanner(fileHandler);

        this.accountMap = new TreeMap<>();

        this.targets = new ArrayList<>();

        int count = scanner.nextInt();
        for (int i = 0; i < count; i++) {
            String id = scanner.next();
            String ip = scanner.next();
            int portNumber = scanner.nextInt();
            List<String> target = new ArrayList<>();
            target.add(id);
            target.add(ip);
            target.add(String.valueOf(portNumber));
            this.targets.add(target);
        }
        // close the scanner
        scanner.close();

        // add the current node to the list of targets
        List<String> currentNode = new ArrayList<>();
        currentNode.add(nodeId);
        currentNode.add(InetAddress.getLocalHost().getHostAddress());
        currentNode.add(String.valueOf(port));
        this.targets.add(currentNode);

//        logger.info("Server started on port " + serverSocket.getLocalPort());
    }

    public void run() {
        // keep listening to the port and accept new connections
        while (true) {
            try {
                Socket server = serverSocket.accept();
                DataInputStream in = new DataInputStream(server.getInputStream());
                String msg = in.readUTF();
                // if the message starts with "This node is down", remove the node from the list of targets
                // the message is of the form "This node is down: <nodeId>"
                if (msg.startsWith("This node is down")) {
                    String[] msgParts = msg.split(":");
                    String failedNodeId = msgParts[1].trim();
                    for (List<String> target : targets) {
                        if (target.get(0).equals(failedNodeId)) {
                            targets.remove(target);
                            break;
                        }
                    }

                    // iterate through the returnedMsgs map and remove the messages that are sent to the failed node
                    for (Map.Entry<Integer, List<Message>> entry : returnedMsgs.entrySet()) {
                        List<Message> messages = entry.getValue();
                        for (Message message : messages) {
                            if (message.receiverNodeId.equals(failedNodeId)) {
                                messages.remove(message);
                                break;
                            }
                        }
                    }

                    // iterate through the receivedMsgs map and remove the messages that are sent by the failed node
                    for (Map.Entry<String, Message> entry : receivedMsgs.entrySet()) {
                        Message message = entry.getValue();
                        if (message.senderNodeId.equals(failedNodeId)) {
                            receivedMsgs.remove(entry.getKey());
                            break;
                        }
                    }

                    server.close();
                    continue;
                }

                // parse the msg and create a Message object
                // the msg is of the form "content,senderNodeId,receiverNodeId,msgId,isDeliverable,isReturned,priority,priorityId,sendTime,receiveTime,deliverTime,len"
                Message message = new Message(msg.split(",")[0], msg.split(",")[1], msg.split(",")[2], Integer.parseInt(msg.split(",")[3]),
                        Boolean.parseBoolean(msg.split(",")[4]), Boolean.parseBoolean(msg.split(",")[5]), msg.split(",")[6],
                        Integer.parseInt(msg.split(",")[7]), Long.parseLong(msg.split(",")[8]), Long.parseLong(msg.split(",")[9]),
                        Long.parseLong(msg.split(",")[10]), Integer.parseInt(msg.split(",")[11]));
                message.len = msg.length();

                // check if the node is still alive by iterate through the list of targets
                boolean isAlive = false;
                for (List<String> target : targets) {
                    if (target.get(0).equals(message.receiverNodeId)) {
                        isAlive = true;
                        break;
                    }
                }

                // if the node is not alive, continue
                if (!isAlive) {
                    server.close();
                    continue;
                }

                if (!message.isReturned) {
                    message.receiveTime = System.currentTimeMillis() % 400000;
                    receiveFirstTimeMessageHandler(message);
                } else if (message.isReturned && !message.isDeliverable) {
                    receiveReturnedMessageHandler(message);
                } else if (message.isReturned && message.isDeliverable) {
                    message.deliverTime = System.currentTimeMillis() % 400000;
                    receiveDeliverableMessageHandler(message);
                }

                server.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void receiveFirstTimeMessageHandler(Message message) {
        int newPriorityId = this.priorityId + 1;
        this.priorityId = newPriorityId;
        message.priorityId = newPriorityId;
        message.priority = priorityId + ":" + nodeId;
        message.isDeliverable = false;
        message.isReturned = true;

        // save the message in the receivedMsgs map
        this.receivedMsgs.put(message.senderNodeId + ":" + message.msgId, message);

        // find the sender by iterating through the list of targets with the senderNodeId
        String senderNodeId = message.senderNodeId;

        for (List<String> target : targets) {
            if (target.get(0).equals(senderNodeId)) {
                String senderIp = target.get(1);
                int senderPort = Integer.parseInt(target.get(2));
                // send the message to the sender
                sendMessage(message, senderIp, senderPort, senderNodeId);
                break;
            }
        }
    }


    private void sendMessage(Message message, String senderIp, int senderPort, String senderNodeId) {
        try {
            Socket client = new Socket(senderIp, senderPort);
            DataOutputStream out = new DataOutputStream(client.getOutputStream());
            String msg = message.toString();
            out.writeUTF(msg);
            client.close();
        } catch (Exception e) {
        }
    }


    private void receiveReturnedMessageHandler(Message message) {
        List<Message> returnedMsgsForTransaction = this.returnedMsgs.getOrDefault(message.msgId, new ArrayList<>());
        returnedMsgsForTransaction.add(message);
        this.returnedMsgs.put(message.msgId, returnedMsgsForTransaction);
        // check if all the servers have returned the message
        if (returnedMsgsForTransaction.size() >= targets.size()) {
            // sort the messages by priority
            sortMessagesByPriority(returnedMsgsForTransaction);
            // get the largest message
            Message maxMsg = returnedMsgsForTransaction.get(returnedMsgsForTransaction.size() - 1);

            // set the deliverable flag to true
            maxMsg.isDeliverable = true;
//            this.receivedMsgs.put(maxMsg.senderNodeId + ":" + maxMsg.msgId, maxMsg);
            this.returnedMsgs.remove(maxMsg.msgId);
            // multicast the message
            multicastFinalMessage(maxMsg);
        }

    }

    private void multicastFinalMessage(Message maxMsg) {
        for (List<String> target : targets) {
            String targetNodeId = target.get(0);
            String targetIp = target.get(1);
            int targetPort = Integer.parseInt(target.get(2));
            sendMessage(maxMsg, targetIp, targetPort, targetNodeId);
        }
    }

    private void sortMessagesByPriority(List<Message> messages) {
        messages.sort(new Comparator<Message>() {
            @Override
            public int compare(Message o1, Message o2) {
                String[] o1Priority = o1.priority.split(":");
                String[] o2Priority = o2.priority.split(":");
                int o1PriorityId = Integer.parseInt(o1Priority[0]);
                int o2PriorityId = Integer.parseInt(o2Priority[0]);
                if (o1PriorityId > o2PriorityId) {
                    return 1;
                } else if (o1PriorityId < o2PriorityId) {
                    return -1;
                } else {
                    String o1NodeId = o1Priority[1];
                    String o2NodeId = o2Priority[1];
                    return o1NodeId.compareTo(o2NodeId);
                }
            }
        });
    }

    private void receiveDeliverableMessageHandler(Message message) {
        this.receivedMsgs.put(message.senderNodeId + ":" + message.msgId, message);
        this.priorityId = Math.max(message.priorityId, this.priorityId);

        // create a copy of receivedMsgs's values
        List<Message> receivedMsgsCopy = new ArrayList<>(this.receivedMsgs.values());
        // sort the messages by priority
        receivedMsgsCopy.sort(new Comparator<Message>() {
            @Override
            public int compare(Message o1, Message o2) {
                String[] o1Priority = o1.priority.split(":");
                String[] o2Priority = o2.priority.split(":");
                int o1PriorityId = Integer.parseInt(o1Priority[0]);
                int o2PriorityId = Integer.parseInt(o2Priority[0]);
                if (o1PriorityId > o2PriorityId) {
                    return 1;
                } else if (o1PriorityId < o2PriorityId) {
                    return -1;
                } else {
                    String o1NodeId = o1Priority[1];
                    String o2NodeId = o2Priority[1];
                    return o1NodeId.compareTo(o2NodeId);
                }
            }
        });

        // if the first message in the sorted list is deliverable, deliver it
        if (receivedMsgsCopy.get(0).isDeliverable) {
            Message firstMsg = receivedMsgsCopy.get(0);
            String content = firstMsg.content;
            firstMsg.deliverTime = System.currentTimeMillis() % 400000;

            // split the content by space
            String[] contentParts = content.split(" ");
            if (contentParts[0].equals("DEPOSIT")) {
                String name = contentParts[1];
                int amount = Integer.parseInt(contentParts[2]);
                // update the balance
                if (accountMap.containsKey(name)) {
                    int newAmount = accountMap.get(name) + amount;
                    accountMap.put(name, newAmount);
                } else {
                    accountMap.put(name, amount);
                }
            } else if (contentParts[0].equals("TRANSFER")) {
                String fromName = contentParts[1];
                String toName = contentParts[3];
                accountMap.putIfAbsent(fromName, 0);
                accountMap.putIfAbsent(toName, 0);
                int amount = Integer.parseInt(contentParts[4]);
                if (amount > accountMap.get(fromName)) {
                    logger.warning("Illegal Transaction: Not enough money to transfer");
//                    System.out.println("Illegal Transaction: Not enough money to transfer");
                } else {
                    int newFromAmount = accountMap.get(fromName) - amount;
                    int newToAmount = accountMap.get(toName) + amount;
                    accountMap.put(fromName, newFromAmount);
                    accountMap.put(toName, newToAmount);
                }
            }

            StringBuilder sb = new StringBuilder();
            sb.append("BALANCES ");
            for (Map.Entry<String, Integer> entry : accountMap.entrySet()) {
                sb.append(entry.getKey() + ":" + entry.getValue() + " ");
            }
            logger.info(sb.toString());
//            System.out.println(sb.toString());

            writeDataToCSV(firstMsg);
            this.receivedMsgs.remove(firstMsg.senderNodeId + ":" + firstMsg.msgId);
        }


    }

    private void deliverMessage(Message message) {
        String content = message.content;
        message.deliverTime = System.currentTimeMillis() % 400000;

        // split the content by space
        String[] contentParts = content.split(" ");
        if (contentParts[0].equals("DEPOSIT")) {
            String name = contentParts[1];
            int amount = Integer.parseInt(contentParts[2]);
            // update the balance
            if (accountMap.containsKey(name)) {
                int newAmount = accountMap.get(name) + amount;
                accountMap.put(name, newAmount);
            } else {
                accountMap.put(name, amount);
            }
        } else if (contentParts[0].equals("TRANSFER")) {
            String fromName = contentParts[1];
            String toName = contentParts[3];
            accountMap.putIfAbsent(fromName, 0);
            accountMap.putIfAbsent(toName, 0);
            int amount = Integer.parseInt(contentParts[4]);
            if (amount > accountMap.get(fromName)) {
                logger.warning("Illegal Transaction: Not enough money to transfer");
            } else {
                int newFromAmount = accountMap.get(fromName) - amount;
                int newToAmount = accountMap.get(toName) + amount;
                accountMap.put(fromName, newFromAmount);
                accountMap.put(toName, newToAmount);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("BALANCES ");
        for (Map.Entry<String, Integer> entry : accountMap.entrySet()) {
            sb.append(entry.getKey() + ":" + entry.getValue() + " ");
        }
        System.out.println(sb.toString());
        logger.info(sb.toString());

        writeDataToCSV(message);
    }

    private void writeDataToCSV(Message message) {
        // create a csv file named nodeId + ".csv"
        String fileName = this.nodeId + ".csv";
        File file = new File(fileName);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        String data = message.senderNodeId + "," + message.receiverNodeId + "," + message.len + ','
                + message.sendTime + "," + message.receiveTime + "," + message.deliverTime;

        try {
            FileWriter fw = new FileWriter(file, true);
            writeLineByLine(data, fw);
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    private void writeLineByLine(String data, FileWriter fw) throws IOException {
        BufferedWriter bw = new BufferedWriter(fw);
        bw.write(data);
        bw.newLine();

        bw.flush();
        bw.close();
    }


}
