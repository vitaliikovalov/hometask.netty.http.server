import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by Cctv on 31.10.2014.
 */
public class ActiveConnection {

    private InetAddress ipSource;
    private URI uri;
    private long timestamps;
    private int sendBytes;
    private int receivedBytes;
    private int speed;
    private SimpleDateFormat format = new SimpleDateFormat("yyyy:MM:dd HH.mm.ss.SSS");

    public ActiveConnection(InetAddress ipSource, URI uri) {
        this.ipSource = ipSource;
        this.uri = uri;
    }


    public ActiveConnection(InetAddress ipSource, URI uri, long timestamps, int sendBytes, int receivedBytes, int speed) {
        this.ipSource = ipSource;
        this.uri = uri;
        this.timestamps = timestamps;
        this.sendBytes = sendBytes;
        this.receivedBytes = receivedBytes;
        this.speed = speed;
    }

    public InetAddress getIpSource() {
        return ipSource;
    }

    public URI getUri() {
        return uri;
    }

    public long getTimestamps() {
        return timestamps;
    }

    public int getSendBytes() {
        return sendBytes;
    }

    public int getReceivedBytes() {
        return receivedBytes;
    }

    public int getSpeed() {
        return speed;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ActiveConnection that = (ActiveConnection) o;

        if (ipSource != null ? !ipSource.equals(that.ipSource) : that.ipSource != null) return false;
        if (uri != null ? !uri.equals(that.uri) : that.uri != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = ipSource != null ? ipSource.hashCode() : 0;
        result = 31 * result + (uri != null ? uri.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append(String.format("%-25s", ipSource.getHostName())).append("|");
        result.append(String.format("%-40s", uri.getPath())).append("|");
        result.append(String.format("%-25s", format.format(new Date(timestamps)))).append("|");
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
