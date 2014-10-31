import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

/**
 * Created by Cctv on 31.10.2014.
 */
public class StatisticsCollector {

    private static List<ActiveConnection> last16Connections = Collections.synchronizedList(new ArrayList<ActiveConnection>(18));
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

    private static volatile Map<String, Integer> uniqueRequest = Collections.synchronizedMap(new HashMap<String, Integer>());


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
        sqlExecute = "SELECT COUNT(*) FROM CONNECTION_DB";
        try {
            resultSet = statement.executeQuery(sqlExecute);
            if (resultSet.next()) {
                countConnection = resultSet.getInt(1);
                System.out.println("Общее количество запросов: ");
                System.out.println(countConnection);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        sqlExecute = "SELECT SOURCE_IP, COUNT(DISTINCT URI) FROM CONNECTION_DB GROUP BY SOURCE_IP";
        try {
            resultSet = statement.executeQuery(sqlExecute);
            System.out.println("Количество уникальных запросов (по одному на IP):");
            System.out.print(String.format("%-25s", "IP"));
            System.out.println(String.format("%-10s", "Count"));
            while (resultSet.next()) {
                uniqueRequest.put(resultSet.getString(1), resultSet.getInt(2));
                System.out.print(String.format("%-25s", resultSet.getString(1)));
                System.out.println(String.format("%-10s", resultSet.getString(2)));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


}
