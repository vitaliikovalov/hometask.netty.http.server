import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;

//Класс-инициализатор основного канала сервера. Устанавливает обработчики для основного канала.
public class ServerInitializer extends ChannelInitializer<SocketChannel> {

    @Override
    public void initChannel(SocketChannel ch) {
        ChannelPipeline p = ch.pipeline();
        p.addLast(new Decoder());
        p.addLast(new HttpServerCodec());
        p.addLast(new ServerHandler());
    }
}
