/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
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

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.sql.*;
import java.util.List;

import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpHeaders.Values.CLOSE;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.*;

public class ServerHandler extends ChannelInboundHandlerAdapter {

    private Connection connection;
    private Statement statement;
    private ResultSet resultSet;
    private int size;
    private long startTime;
    private long finishTime;
    private int speed;

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        // Удаление запроса из количества активных подключений
        Server.removeConnection();
        ctx.flush();
        // Закрытие подключения к БД
        ConnectionManager.closeConnection(connection);
        ConnectionManager.closeStatement(statement);
        ConnectionManager.closeResultSet(resultSet);
    }


    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws URISyntaxException, SQLException {
        // Получение размера запроса
        if (msg instanceof Integer) {
            size = (Integer) msg;
        }
        // Обработка запроса
        if (msg instanceof HttpRequest) {
            // Получение времени запроса
            startTime = System.currentTimeMillis();
            HttpRequest req = (HttpRequest) msg;
            // Получение URI из запроса
            URI uri = new URI(req.getUri());
            // Создание подключения к БД
            connection = ConnectionManager.getConnection();
            statement = connection.createStatement();
            //Добавление запроса к списку активных подключений
            Server.addConnection();
            // Обработка запроса /hello.
            if (uri.toString().equalsIgnoreCase("/hello")) {
                renderHelloWorld(ctx, uri, statement, msg);
            // Обработка запроса /redirect.
            } else if (((uri.toString()).length() > 13) && (uri.toString()).substring(0, 14).equalsIgnoreCase("/redirect?url=")) {
                redirect(ctx, uri, statement);
            // Обработка запроса /status.
            } else if (uri.toString().equalsIgnoreCase("/status")) {
                renderStatus(ctx, uri, statement);
            // Обработка других запросов.
            } else {
                unsupportedParameter(ctx, uri, statement);
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        // Удаление запроса из количества активных подключений
        Server.removeConnection();
        // Звкрытие подключения к БД
        ConnectionManager.closeConnection(connection);
        ConnectionManager.closeStatement(statement);
        ConnectionManager.closeResultSet(resultSet);
        ctx.close();
    }

    // Метод создания страницы Hello world. Отправляет ответ в виде Hello world
    public void renderHelloWorld(ChannelHandlerContext ctx, URI uri, Statement statement, Object msg) {
        // Ждет 10 секунд
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // Формирование ответа
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK,
                Unpooled.copiedBuffer("Hello world",
                        Charset.forName("UTF-8")));
        response.headers().set(CONTENT_LENGTH,
                response.content().readableBytes());
        response.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");
        response.headers().set(CONNECTION, CLOSE);
        // Врямя завершения формирования ответа
        finishTime = System.currentTimeMillis();
        // Скорость обработки запроса
        try {
            speed = (int) ((response.content().readableBytes() + size) * 1000 / (finishTime - startTime));
        }
        catch (ArithmeticException e) {
            speed = 0;
        }
        // Подготовка запросы записи в БД
        String sqlExecute = "INSERT INTO CONNECTION_DB (SOURCE_IP, URI, TIMESTAMPS, SEND_BYTES, RECEIVED_BYTES, SPEED) VALUES (\""
                + ((InetSocketAddress) ctx.channel().remoteAddress()).getHostName()
                + "\", \""
                + uri
                + "\", "
                + "NOW()"
                + ", \""
                + response.content().readableBytes()
                + "\", \""
                + size
                + "\", \""
                + speed
                + "\")";
        ctx.write(response).addListener(ChannelFutureListener.CLOSE);
        // Запись в БД
        try {
            statement.execute(sqlExecute);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Метод переадресации. Отправляет ответ в виде запроса на переадресацию
    public void redirect(ChannelHandlerContext ctx, URI uri, Statement statement) {
        // Формирование ссылки на переадресацию
        String[] redirectLink = uri.getQuery().split("=");
        URI redirectUri = null;
        if (!redirectLink[1].substring(0, 7).equalsIgnoreCase("http://")) {
            redirectLink[1] = "http://" + redirectLink[1];
        }
        try {
            redirectUri = new URI(redirectLink[1]);
        } catch (URISyntaxException e) {
            System.err.println("bad uri");
        }
        // Формирование ответа
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, MOVED_PERMANENTLY);
        response.headers().set(LOCATION, redirectUri);
        // Время завершения обработки запроса
        finishTime = System.currentTimeMillis();
        // Скорость обработки запроса
        try {
            speed = (int) ((response.content().readableBytes() + size) * 1000 / (finishTime - startTime));
        }
        catch (ArithmeticException e) {
            speed = 0;
        }
        // Создание запроса к БД
        String sqlExecute = "INSERT INTO CONNECTION_DB (SOURCE_IP, URI, TIMESTAMPS, SEND_BYTES, RECEIVED_BYTES, SPEED) VALUES (\""
                + ((InetSocketAddress) ctx.channel().remoteAddress()).getHostName()
                + "\", \""
                + uri
                + "\", "
                + "NOW()"
                + ", \""
                + response.content().readableBytes()
                + "\", \""
                + size
                + "\", \""
                + speed
                + "\")";
        ctx.write(response).addListener(ChannelFutureListener.CLOSE);
        // Запись в БД
        try {
            statement.execute(sqlExecute);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Метод создания страницы статистики. Формирует и отправляет статистику
    public void renderStatus(ChannelHandlerContext ctx, URI uri, Statement statement) {
        StringBuilder status = new StringBuilder();
        // Определение количество запросов в БД
        String sqlExecute = "SELECT COUNT(*) FROM CONNECTION_DB";
        try {
            resultSet = statement.executeQuery(sqlExecute);
            if (resultSet.next()) {
                status.append("Общее количество запросов: ");
                status.append(resultSet.getInt(1)).append("\n");
            }
            status.append("\n");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        // Определение количества уникальных запросов
        sqlExecute = "SELECT SOURCE_IP, COUNT(DISTINCT URI) FROM CONNECTION_DB GROUP BY SOURCE_IP";
        try {
            resultSet = statement.executeQuery(sqlExecute);
            status.append("Количество уникальных запросов (по одному на IP):\n");
            status.append(String.format("%-25s", "IP")).append("|");
            status.append(String.format("%-10s", "Count")).append("\n");
            while (resultSet.next()) {
                status.append(String.format("%-25s", resultSet.getString(1))).append("|");
                status.append(String.format("%-10s", resultSet.getString(2))).append("\n");
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        // Определение количества запросов на каждый адрес
        sqlExecute = "SELECT SOURCE_IP, COUNT(*), MAX(TIMESTAMPS) FROM CONNECTION_DB GROUP BY SOURCE_IP";
        try {
            resultSet = statement.executeQuery(sqlExecute);
            status.append("\n").append("Счетчик запросов на каждый IP:").append("\n");
            status.append(String.format("%-25s", "IP")).append("|");
            status.append(String.format("%-10s", "Count")).append("|");
            status.append(String.format("%-25s", "Last time")).append("\n");
            while (resultSet.next()) {
                status.append(String.format("%-25s", resultSet.getString(1))).append("|");
                status.append(String.format("%-10s", resultSet.getString(2))).append("|");
                status.append(String.format("%-25s", resultSet.getString(3))).append("\n");
            }
            status.append("\n");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        // Определение количество уникальных переадресаций
        sqlExecute = "SELECT URI, COUNT(*) FROM CONNECTION_DB WHERE URI LIKE \"%/redirect?url=%\" GROUP BY URI";
        try {
            resultSet = statement.executeQuery(sqlExecute);
            status.append("Количество переадресаций по url'ам:").append("\n");
            status.append(String.format("%-40s", "URI")).append("|");
            status.append(String.format("%-10s", "Count")).append("\n");
            while (resultSet.next()) {
                status.append(String.format("%-40s", resultSet.getString(1).substring(14))).append("|");
                status.append(String.format("%-10s", resultSet.getString(2))).append("\n");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        // Добавляет список 16 последних обработанных соединений
        sqlExecute = "SELECT * FROM CONNECTION_DB ORDER BY TIMESTAMPS DESC LIMIT 16";
        try {
            resultSet = statement.executeQuery(sqlExecute);
            status.append("\n").append("16 последних обработанных соединений:").append("\n");
            status.append(String.format("%-25s", "IP")).append("|");
            status.append(String.format("%-40s", "URI")).append("|");
            status.append(String.format("%-25s", "Timestamp")).append("|");
            status.append(String.format("%-15s", "Sent bytes")).append("|");
            status.append(String.format("%-15s", "Received bytes")).append("|");
            status.append(String.format("%-10s", "Speed")).append("\n");
            while (resultSet.next()) {
                status.append(String.format("%-25s", resultSet.getString(1))).append("|");
                status.append(String.format("%-40s", resultSet.getString(2))).append("|");
                status.append(String.format("%-25s", resultSet.getString(3))).append("|");
                status.append(String.format("%-15s", resultSet.getString(4))).append("|");
                status.append(String.format("%-15s", resultSet.getString(5))).append("|");
                status.append(String.format("%-10s", resultSet.getString(6))).append("\n");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        // Определение количества активных соединений
        status.append("\n")
                .append("Количество соединений, открытых в данный момент: ")
                .append(Server.getConnection())
                .append("\n");
        // Создание ответа
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK,
                Unpooled.copiedBuffer(status.toString(),
                        Charset.forName("UTF-8")));
        response.headers().set(CONTENT_LENGTH,
                response.content().readableBytes());
        response.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");
        response.headers().set(CONNECTION, CLOSE);
        // Определение время окончания обработки запроса
        finishTime = System.currentTimeMillis();
        // Определение скорости обработки запроса
        try {
            speed = (int) ((response.content().readableBytes() + size) * 1000 / (finishTime - startTime));
        }
        catch (ArithmeticException e) {
            speed = 0;
        }
        // Создание запроса добавления в БД
        sqlExecute = "INSERT INTO CONNECTION_DB (SOURCE_IP, URI, TIMESTAMPS, SEND_BYTES, RECEIVED_BYTES, SPEED) VALUES (\""
                + ((InetSocketAddress) ctx.channel().remoteAddress()).getHostName()
                + "\", \""
                + uri
                + "\", "
                + "NOW()"
                + ", \""
                + response.content().readableBytes()
                + "\", \""
                + size
                + "\", \""
                + speed
                + "\")";
        // Добавление в БД
        try {
            statement.execute(sqlExecute);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        ctx.write(response).addListener(ChannelFutureListener.CLOSE);
    }

    // Метод обработки других запросов. Отправляет ответ в виде пустой страницы
    public void unsupportedParameter(ChannelHandlerContext ctx, URI uri, Statement statement) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST);
        finishTime = System.currentTimeMillis();
        try {
            speed = (int) ((response.content().readableBytes() + size) / (finishTime - startTime)) * 1000;
        }
        catch (ArithmeticException e) {
            speed = 0;
        }
        String sqlExecute = "INSERT INTO CONNECTION_DB (SOURCE_IP, URI, TIMESTAMPS, SEND_BYTES, RECEIVED_BYTES, SPEED) VALUES (\""
                + ((InetSocketAddress) ctx.channel().remoteAddress()).getHostName()
                + "\", \""
                + uri
                + "\", "
                + "NOW()"
                + ", \""
                + response.content().readableBytes()
                + "\", \""
                + size
                + "\", \""
                + speed
                + "\")";
        try {
            statement.execute(sqlExecute);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        ctx.write(response).addListener(ChannelFutureListener.CLOSE);
    }

    // Метод создает новое соединение с базой данных
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