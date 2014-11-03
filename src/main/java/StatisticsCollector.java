import java.net.*;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

/**
 * Created by Cctv on 31.10.2014.
 */
public class StatisticsCollector {

    // Количество открытых соединений.
    private static volatile int connection = 0;

    // Метод возвращает количество открытых соеденений.
    public static int getConnection() {
        return connection;
    }

    // Метод добавляет открытое соединение.
    public static synchronized void addConnection() {
        connection++;
    }

    // Метод удаляет открытое соеденение.
    public static synchronized void removeConnection() {
        connection--;
    }

    private static volatile int countConnection = 0;

    public static synchronized void addCountConnection() {
        countConnection++;
    }

    public static synchronized int getCountConnection() {
        return countConnection;
    }

    private static volatile Set<ActiveConnection> uniqueRequest = Collections.synchronizedSet(new HashSet<ActiveConnection>());

    private static volatile List<ActiveConnection> last16Connections = Collections.synchronizedList(new ArrayList<ActiveConnection>(17));

    public static synchronized List<ActiveConnection> getLast16Connections() {
        return last16Connections;
    }

    public static synchronized void addToLast16Connections(ActiveConnection activeConnection) {
        last16Connections.add(activeConnection);
        if (last16Connections.size() > 16) {
            last16Connections.remove(0);
        }
    }

    public static synchronized void checkUniqueRequest(ActiveConnection activeConnection) {
        uniqueRequest.add(activeConnection);
    }

    public static synchronized int getCountUniqueRequest() {
        return uniqueRequest.size();
    }

    private static volatile Map<URI, Integer> redirectedConnections = Collections.synchronizedMap(new HashMap<URI, Integer>());

    public static void checkRedirectRequest(ActiveConnection activeConnection) {
        if (redirectedConnections.containsKey(activeConnection.getUri())) {
            Integer tmp = redirectedConnections.get(activeConnection.getUri());
            tmp++;
            redirectedConnections.put(activeConnection.getUri(), tmp);
        }
        else {
            redirectedConnections.put(activeConnection.getUri(), 1);
        }
    }

    public static Map<URI, Integer> getRedirectedConnections() {
        return redirectedConnections;
    }

    private static Map<InetAddress, List<Long>> requestsPerAddress = Collections.synchronizedMap(new HashMap<InetAddress, List<Long>>());

    public static void addRequestsPerAddress(ActiveConnection activeConnection) {
        if (requestsPerAddress.containsKey(activeConnection.getIpSource())) {
            List<Long> tmp = requestsPerAddress.get(activeConnection.getIpSource());
            tmp.set(0, tmp.get(0) + 1);
            tmp.set(1, activeConnection.getTimestamps());
        }
        else {
            requestsPerAddress.put(activeConnection.getIpSource(),
                    new ArrayList<Long>(Arrays.asList(new Long(1),
                            activeConnection.getTimestamps())));
        }
    }

    public static Map<InetAddress, List<Long>> getRequestsPerAddress() {
        return requestsPerAddress;
    }



    static {
        Connection connection = ConnectionManager.getConnection();
        Statement statement = null;
        ResultSet resultSet;
        String sqlExecute;
        try {
            statement = connection.createStatement();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        // количество записей в бд
        sqlExecute = "SELECT COUNT(*) FROM CONNECTION_DB";
        try {
            resultSet = statement.executeQuery(sqlExecute);
            if (resultSet.next()) {
                countConnection = resultSet.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        //количество уникальных запросов
        sqlExecute = "SELECT SOURCE_IP, URI FROM CONNECTION_DB GROUP BY SOURCE_IP, URI";
        try {
            resultSet = statement.executeQuery(sqlExecute);
            while (resultSet.next()) {
                uniqueRequest.add(new ActiveConnection(InetAddress.getByName(resultSet.getString(1)), new URI(resultSet.getString(2))));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        // Определение количества запросов на каждый адрес
        sqlExecute = "SELECT SOURCE_IP, COUNT(*), MAX(TIMESTAMPS) FROM CONNECTION_DB GROUP BY SOURCE_IP";
        try {
            resultSet = statement.executeQuery(sqlExecute);

            while (resultSet.next()) {
                requestsPerAddress.put(InetAddress.getByName(resultSet.getString(1)),
                        new ArrayList<Long>(Arrays.asList(new Long(resultSet.getString(2)), new Long(resultSet.getString(3)))));
            }
            System.err.println(requestsPerAddress);
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        //список переадресаций
        sqlExecute = "SELECT URI, COUNT(*) FROM CONNECTION_DB WHERE URI LIKE \"%/redirect?url=%\" GROUP BY URI";
        try {
            resultSet = statement.executeQuery(sqlExecute);
            while (resultSet.next()) {
                redirectedConnections.put(new URI(resultSet.getString(1)), resultSet.getInt(2));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        //последние 16 подключений
        sqlExecute = "SELECT * FROM CONNECTION_DB ORDER BY TIMESTAMPS DESC LIMIT 16";
        try {
            resultSet = statement.executeQuery(sqlExecute);
            while (resultSet.next()) {
                addToLast16Connections(new ActiveConnection((InetAddress.getByName(resultSet.getString(1))),
                        new URI(resultSet.getString(2)),
                        resultSet.getLong(3),
                        resultSet.getInt(4),
                        resultSet.getInt(5),
                        resultSet.getInt(6)));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }


}
