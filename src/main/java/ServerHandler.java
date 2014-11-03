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

import java.net.*;
import java.nio.charset.Charset;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    private SimpleDateFormat format = new SimpleDateFormat("yyyy:MM:dd HH.mm.ss.SSS");

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        // Удаление запроса из количества активных подключений
        StatisticsCollector.removeConnection();
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
            StatisticsCollector.addConnection();
            // Обработка запроса /hello.
            if (uri.toString().equalsIgnoreCase("/hello")) {
                renderHelloWorld(ctx, uri, statement);
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
        StatisticsCollector.removeConnection();
        // Звкрытие подключения к БД
        ConnectionManager.closeConnection(connection);
        ConnectionManager.closeStatement(statement);
        ConnectionManager.closeResultSet(resultSet);
        ctx.close();
    }

    // Метод создания страницы Hello world. Отправляет ответ в виде Hello world
    public void renderHelloWorld(ChannelHandlerContext ctx, URI uri, Statement statement) {
        // Ждет 10 секунд
//        try {
//            Thread.sleep(0);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
        // Формирование ответа
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK,
                Unpooled.copiedBuffer("Hello world",
                        Charset.forName("UTF-8")));
        response.headers().set(CONTENT_LENGTH,
                response.content().readableBytes());
        response.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");
        response.headers().set(CONNECTION, CLOSE);
        // Скорость обработки запроса
        speed = getSpeed(response.content().readableBytes());
        ActiveConnection activeConnection = null;
        try {
            activeConnection = new ActiveConnection(
                    InetAddress.getByName(((InetSocketAddress) (ctx.channel().remoteAddress())).getHostName()),
                    uri,
                    System.currentTimeMillis(),
                    response.content().readableBytes(),
                    size,
                    speed
            );
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        // Подготовка запросы записи в БД
        String sqlExecute = activeConnection.getSQLExecute();
        ctx.write(response).addListener(ChannelFutureListener.CLOSE);
        // Запись в БД
        try {
            statement.execute(sqlExecute);
            StatisticsCollector.addCountConnection();
            StatisticsCollector.addToLast16Connections(activeConnection);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Метод переадресации. Отправляет ответ в виде запроса на переадресацию
    public void redirect(ChannelHandlerContext ctx, URI uri, Statement statement) {
        // Формирование ссылки на переадресацию
        String[] redirectLink = uri.getQuery().split("=");
        URI redirectUri = null;
        if (redirectLink[1].length() < 7 || !redirectLink[1].substring(0,7).equalsIgnoreCase("http://")) {
            redirectLink[1] = "http://" + redirectLink[1];
        }
        try {
            redirectUri = new URI(redirectLink[1]);
        } catch (URISyntaxException e) {
            System.err.println("bad uri");
        }
        System.err.println(redirectUri);
        System.err.println(uri);
        System.err.println(redirectLink[0] + "=" + redirectLink[1]);
        // Формирование ответа
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, MOVED_PERMANENTLY);
        response.headers().set(LOCATION, redirectUri);
        // Скорость обработки запроса
        speed = getSpeed(response.content().readableBytes());
        ActiveConnection activeConnection = null;
        try {
            activeConnection = new ActiveConnection(
                    InetAddress.getByName(((InetSocketAddress) (ctx.channel().remoteAddress())).getHostName()),
                    uri,
                    System.currentTimeMillis(),
                    response.content().readableBytes(),
                    size,
                    speed
            );
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        // Подготовка запросы записи в БД
        String sqlExecute = activeConnection.getSQLExecute();
        ctx.write(response).addListener(ChannelFutureListener.CLOSE);
        // Запись в БД
        try {
            statement.execute(sqlExecute);
            StatisticsCollector.addCountConnection();
            StatisticsCollector.addToLast16Connections(activeConnection);
            StatisticsCollector.checkRedirectRequest(activeConnection);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Метод создания страницы статистики. Формирует и отправляет статистику
    public void renderStatus(ChannelHandlerContext ctx, URI uri, Statement statement) {
        StringBuilder status = new StringBuilder();
        // Определение количество запросов в БД
        status.append("Общее количество запросов: ");
        status.append(StatisticsCollector.getCountConnection()).append("\n");
        // Определение количество уникальных переадресаций
        status.append("Количество переадресаций по url'ам: ");
        status.append(StatisticsCollector.getCountUniqueRequest()).append("\n");
        // Определение количества запросов на каждый адрес
        status.append("\n").append("Счетчик запросов на каждый IP:").append("\n");
        status.append(String.format("%-25s", "IP")).append("|");
        status.append(String.format("%-10s", "Count")).append("|");
        status.append(String.format("%-25s", "Last time")).append("\n");
        for (Map.Entry<InetAddress, List<Long>> entry :StatisticsCollector.getRequestsPerAddress().entrySet()) {
            status.append(String.format("%-25s", entry.getKey())).append("|");
            status.append(String.format("%-10s", entry.getValue().get(0))).append("|");
            status.append(String.format("%-25s", format.format(new Date(entry.getValue().get(1))))).append("\n");
        }
        status.append("\n");


        //Добавляет список переадресаций
        for (Map.Entry<URI, Integer> map :StatisticsCollector.getRedirectedConnections().entrySet()) {
            String[] tmp = map.getKey().getQuery().split("=");
            status.append(String.format("%-40s", tmp[1])).append("|");
            status.append(String.format("%-10s", map.getValue())).append("\n");
        }

        // Добавляет список 16 последних обработанных соединений
        status.append("\n").append("16 последних обработанных соединений:").append("\n");
        status.append(String.format("%-25s", "IP")).append("|");
        status.append(String.format("%-40s", "URI")).append("|");
        status.append(String.format("%-25s", "Timestamp")).append("|");
        status.append(String.format("%-15s", "Sent bytes")).append("|");
        status.append(String.format("%-15s", "Received bytes")).append("|");
        status.append(String.format("%-10s", "Speed")).append("\n");
        for (ActiveConnection activeConnection :new ArrayList<ActiveConnection>(StatisticsCollector.getLast16Connections())) {
            status.append(activeConnection);
        }
        // Определение количества активных соединений
        status.append("\n")
                .append("Количество соединений, открытых в данный момент: ")
                .append(StatisticsCollector.getConnection())
                .append("\n");
        // Создание ответа
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK,
                Unpooled.copiedBuffer(status.toString(),
                        Charset.forName("UTF-8")));
        response.headers().set(CONTENT_LENGTH,
                response.content().readableBytes());
        response.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");
        response.headers().set(CONNECTION, CLOSE);
// Скорость обработки запроса
        speed = getSpeed(response.content().readableBytes());
        ActiveConnection activeConnection = null;
        try {
            activeConnection = new ActiveConnection(
                    InetAddress.getByName(((InetSocketAddress) (ctx.channel().remoteAddress())).getHostName()),
                    uri,
                    System.currentTimeMillis(),
                    response.content().readableBytes(),
                    size,
                    speed
            );
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        // Подготовка запросы записи в БД
        String sqlExecute = activeConnection.getSQLExecute();
        // Добавление в БД
        try {
            StatisticsCollector.addCountConnection();
            statement.execute(sqlExecute);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        StatisticsCollector.addToLast16Connections(activeConnection);
        StatisticsCollector.addRequestsPerAddress(activeConnection);
        ctx.write(response).addListener(ChannelFutureListener.CLOSE);
    }

    // Метод обработки других запросов. Отправляет ответ в виде пустой страницы
    public void unsupportedParameter(ChannelHandlerContext ctx, URI uri, Statement statement) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST);
        // Скорость обработки запроса
        speed = getSpeed(response.content().readableBytes());
        ActiveConnection activeConnection = null;
        try {
            activeConnection = new ActiveConnection(
                    InetAddress.getByName(((InetSocketAddress) (ctx.channel().remoteAddress())).getHostName()),
                    uri,
                    System.currentTimeMillis(),
                    response.content().readableBytes(),
                    size,
                    speed
            );
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        // Подготовка запросы записи в БД
        String sqlExecute = activeConnection.getSQLExecute();
        try {
            statement.execute(sqlExecute);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        ctx.write(response).addListener(ChannelFutureListener.CLOSE);
    }

    public int getSpeed(int sendBytes) {
        int speed;
        finishTime = System.currentTimeMillis();
        try {
            speed = (int) ((sendBytes + size) * 1000 / (finishTime - startTime));
        }
        catch (ArithmeticException e) {
            speed = (sendBytes + size) * 1000;
        }
        return speed;
    }

}