import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Created by Cctv on 06.11.2014.
 */
public class Worker implements Runnable {
    private Connection connection;
    private Statement statement;

    public Worker(Connection connection) {
        this.connection = connection;
        try {
            statement = connection.createStatement();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        while (true) {
            try {
                statement.execute(StatisticsCollector.activeConnections.take().getSQLExecute());
            } catch (SQLException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                ConnectionManager.closeStatement(statement);
                ConnectionManager.closeConnection(connection);
                break;
            }
        }
    }
}
