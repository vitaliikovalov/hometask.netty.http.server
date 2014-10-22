import com.jolbox.bonecp.BoneCP;
import com.jolbox.bonecp.BoneCPConfig;

import java.sql.*;

/**
 * Created by Cctv on 22.10.2014.
 */
public class ConnectionManager {

    private static BoneCP connectionPool = null;

    public static void configureConnectionPool() {
        try {
            Class.forName("com.mysql.jdbc.Driver");
            BoneCPConfig config = new BoneCPConfig();
            config.setJdbcUrl("jdbc:mysql://localhost:3307/server_db");
            config.setUsername("root");
            config.setPassword("EMBT6BDc");
            config.setMaxConnectionsPerPartition(8);
            config.setPartitionCount(2);
            connectionPool = new BoneCP(config);
            System.err.println("Connection pool is initializing....");
            System.err.println("Total connection: " + connectionPool.getTotalCreatedConnections());
            ConnectionManager.setConnectionPool(connectionPool);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void shutdownConnectionPool() {
        try {
            BoneCP connectionPool = getConnectionPool();
            System.err.println("Context destroyed...");
            if (connectionPool != null) {
                connectionPool.shutdown();
                System.err.println("Connection Polling shut downed.");
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Connection getConnection() {

        Connection connection = null;
        try {
            connection = getConnectionPool().getConnection();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return connection;
    }

    public static void closeStatement(Statement statement) {
        try {
            if (statement != null) {
                statement.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void closeResultSet(ResultSet resultSet) {
        try {
            if (resultSet != null) {
                resultSet.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void closeConnection(Connection connection) {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static BoneCP getConnectionPool() {
        return connectionPool;
    }

    private static void setConnectionPool(BoneCP connectionPool) {
        ConnectionManager.connectionPool = connectionPool;
    }

    private Connection getNewConnection() {
        Connection connection = null;
        try {
            Class.forName("com.mysql.jdbc.Driver");
            connection = DriverManager.getConnection("jdbc:mysql://localhost:3307/server_db", "root", "EMBT6BDc");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("Error connection with database.");
        }
        return connection;
    }

}
