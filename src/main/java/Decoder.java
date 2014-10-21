import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequestDecoder;

import java.util.List;

/**
 * Created by Cctv on 19.10.2014.
 */
public class Decoder extends HttpRequestDecoder {

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf buf,
                          List<Object> list) throws Exception {
        Integer size = buf.readableBytes();
        super.decode(ctx, buf, list);
        size -= buf.readableBytes();
        list.add(0, size);
    }
}
