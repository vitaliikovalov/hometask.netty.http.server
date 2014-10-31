import java.net.InetSocketAddress;
import java.net.URI;

/**
 * Created by Cctv on 31.10.2014.
 */
public class ActiveConnection {

    private InetSocketAddress ipSource;
    private URI uri;
    private long timestamps;
    private int sendBytes;
    private int receivedBytes;
    private int speed;

    public ActiveConnection(InetSocketAddress ipSource, URI uri, long timestamps, int sendBytes, int receivedBytes, int speed) {
        this.ipSource = ipSource;
        this.uri = uri;
        this.timestamps = timestamps;
        this.sendBytes = sendBytes;
        this.receivedBytes = receivedBytes;
        this.speed = speed;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append(String.format("%-25s", ipSource.getHostName())).append("|");
        result.append(String.format("%-40s", uri)).append("|");
        result.append(String.format("%-25s", timestamps)).append("|");
        result.append(String.format("%-15s", sendBytes)).append("|");
        result.append(String.format("%-15s", receivedBytes)).append("|");
        result.append(String.format("%-10s", speed)).append("\n");
        return result.toString();
    }

    public String getSQLExecute() {
         return "INSERT INTO CONNECTION_DB (SOURCE_IP, URI, TIMESTAMPS, SEND_BYTES, RECEIVED_BYTES, SPEED) VALUES (\""
                + ipSource.getHostName()
                + "\", \""
                + uri
                + "\", "
                + timestamps
                + ", \""
                + sendBytes
                + "\", \""
                + receivedBytes
                + "\", \""
                + speed
                + "\")";
    }
}
