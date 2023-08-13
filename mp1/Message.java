import java.util.Objects;

public class Message {

    public String content;
    public int msgId;
    public boolean isDeliverable;
    public boolean isReturned;
    public String priority;
    public int priorityId;
    public long sendTime;
    public long receiveTime;
    public long deliverTime;
    public String senderNodeId;
    public String receiverNodeId;
    public long len;

    public Message(String content, String senderNodeId, String receiverNodeId, int msgId, boolean isDeliverable, boolean isReturned, String priority, int priorityId, long sendTime, long receiveTime, long deliverTime, long len) {
        this.content = content;
        this.msgId = msgId;
        this.isDeliverable = isDeliverable;
        this.isReturned = isReturned;
        this.priority = priority;
        this.priorityId = priorityId;
        this.sendTime = sendTime;
        this.receiveTime = receiveTime;
        this.deliverTime = deliverTime;
        this.senderNodeId = senderNodeId;
        this.receiverNodeId = receiverNodeId;
        this.len = len;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Message message)) return false;
        if (senderNodeId.equals(message.senderNodeId) && msgId == message.msgId) return true;
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(senderNodeId, msgId);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(content);
        sb.append(",");
        sb.append(senderNodeId);
        sb.append(",");
        sb.append(receiverNodeId);
        sb.append(",");
        sb.append(msgId);
        sb.append(",");
        sb.append(isDeliverable);
        sb.append(",");
        sb.append(isReturned);
        sb.append(",");
        sb.append(priority);
        sb.append(",");
        sb.append(priorityId);
        sb.append(",");
        sb.append(sendTime);
        sb.append(",");
        sb.append(receiveTime);
        sb.append(",");
        sb.append(deliverTime);
        sb.append(",");
        sb.append(len);
        return sb.toString();
    }
}
